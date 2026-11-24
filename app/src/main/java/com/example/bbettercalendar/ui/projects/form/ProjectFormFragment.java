package com.example.bbettercalendar.ui.projects.form;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.databinding.FragmentProjectFormBinding;
import com.example.bbettercalendar.projects.Tag;
import com.example.bbettercalendar.ui.common.InsetsHelper;
import com.example.bbettercalendar.ui.common.motion.ScreenTransitions;
import com.example.bbettercalendar.ui.common.widget.ProjectAccents;
import com.example.bbettercalendar.ui.projects.ProjectsNavigator;
import com.example.bbettercalendar.ui.projects.ProjectsResults;
import com.example.bbettercalendar.ui.projects.picker.PickerLabels;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.text.DateFormat;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Create / edit project, full screen (rediseno-proyectos u10, spec 3.6). Everything the user types lives in
 * {@link ProjectFormModel} (kept by {@link ProjectFormViewModel}); this class only draws it. Saving posts
 * {@link ProjectsResults#PROJECT_SAVED}; Archive and Delete post their request keys and go back, so the
 * Projects screen does the action and offers Undo.
 */
@AndroidEntryPoint
public class ProjectFormFragment extends Fragment implements InsetsHelper.LiftsWithIme {

    /** Navigation argument: project to edit (0 = new). */
    public static final String ARG_PROJECT_ID = "projectId";

    private FragmentProjectFormBinding binding;
    private ProjectFormViewModel viewModel;
    private ProjectsNavigator navigator;
    @Nullable private ProjectFormModel model;
    @Nullable private List<Tag> areas;
    private boolean pickerOpen;
    private boolean nameFocused;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScreenTransitions.apply(this);
        viewModel = new ViewModelProvider(this).get(ProjectFormViewModel.class);
        navigator = new ProjectsNavigator(this);
        Bundle args = getArguments();
        viewModel.init(args == null ? 0 : args.getInt(ARG_PROJECT_ID, 0));
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProjectFormBinding.inflate(inflater, container, false);
        boolean editing = viewModel.isEditing();
        binding.projectFormTitle.setText(editing ? R.string.projects_form_title_edit : R.string.projects_form_title_new);
        binding.projectFormSave.setText(editing ? R.string.projects_form_save : R.string.projects_form_create);
        binding.projectFormEditCard.setVisibility(editing ? View.VISIBLE : View.GONE);

        TooltipCompat.setTooltipText(binding.projectFormClose, binding.projectFormClose.getContentDescription());
        binding.projectFormClose.setOnClickListener(v -> close());
        binding.projectFormSave.setOnClickListener(v -> save());
        binding.projectFormIcon.setContentDescription(getString(R.string.projects_form_icon_desc));
        TooltipCompat.setTooltipText(binding.projectFormIcon, getString(R.string.projects_form_icon_desc));
        binding.projectFormIcon.setOnClickListener(v -> {
            pickerOpen = !pickerOpen;
            renderPicker();
        });
        binding.projectFormArchive.setOnClickListener(v -> requestAndLeave(ProjectsResults.PROJECT_ARCHIVE_REQUESTED,
                ProjectsResults.projectArchiveRequested(viewModel.getProjectId())));
        binding.projectFormDelete.setOnClickListener(v -> requestAndLeave(ProjectsResults.PROJECT_DELETE_REQUESTED,
                ProjectsResults.projectDeleteRequested(viewModel.getProjectId())));

        binding.projectFormName.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (model == null) return;
                model.setName(s.toString());
                renderSave();
            }
        });
        binding.projectFormNotes.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (model != null) model.setNotes(s.toString());
            }
        });

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        close();
                    }
                });

        viewModel.getReady().observe(getViewLifecycleOwner(), this::onModel);
        viewModel.getAreas().observe(getViewLifecycleOwner(), list -> {
            areas = list;
            renderAreas();
        });
        viewModel.getMissing().observe(getViewLifecycleOwner(), gone -> {
            if (Boolean.TRUE.equals(gone)) {
                Toast.makeText(requireContext(), R.string.projects_form_missing, Toast.LENGTH_SHORT).show();
                leave();
            }
        });
        return binding.getRoot();
    }

    // ---------------------------------------------------------------- model to views

    private void onModel(@Nullable ProjectFormModel loaded) {
        if (binding == null || loaded == null) return;
        boolean first = model != loaded;
        model = loaded;
        if (first) {
            if (!binding.projectFormName.getText().toString().equals(loaded.getName())) {
                binding.projectFormName.setText(loaded.getName());
            }
            if (!binding.projectFormNotes.getText().toString().equals(loaded.getNotes())) {
                binding.projectFormNotes.setText(loaded.getNotes());
            }
            if (!viewModel.isEditing() && !nameFocused) {
                nameFocused = true;
                binding.projectFormName.requestFocus();
                InputMethodManager imm = (InputMethodManager) requireContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(binding.projectFormName, InputMethodManager.SHOW_IMPLICIT);
            }
        }
        renderBubble();
        renderPicker();
        renderDeadline();
        renderAreas();
        renderSave();
    }

    private void renderSave() {
        if (binding == null || model == null) return;
        binding.projectFormSave.setEnabled(model.isSaveEnabled());
    }

    private void renderBubble() {
        if (binding == null || model == null) return;
        binding.projectFormIcon.setAccentColor(ContextCompat.getColor(requireContext(),
                ProjectAccents.colorResFor(model.getColorIndex())));
        binding.projectFormIcon.setIconRes(ProjectAccents.projectIconRes(model.getIconKey()));
        binding.projectFormIcon.setContentDescription(getString(R.string.projects_form_icon_desc));
    }

    private void renderPicker() {
        if (binding == null || model == null) return;
        binding.projectFormPicker.setVisibility(pickerOpen ? View.VISIBLE : View.GONE);
        if (!pickerOpen) return;
        int accent = ProjectAccents.colorResFor(model.getColorIndex());
        FormPickers.fillIcons((GridLayout) binding.projectFormIcons, ProjectAccents.PROJECT_ICON_KEYS, false,
                model.getIconKey(), accent, key -> {
                    model.setIconKey(key);
                    renderBubble();
                    renderPicker();
                });
        int[] colors = new int[ProjectFormModel.COLOR_COUNT];
        String[] names = new String[ProjectFormModel.COLOR_COUNT];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = ProjectAccents.colorResFor(i);
            names[i] = FormPickers.projectColorName(requireContext(), i);
        }
        FormPickers.fillColors((LinearLayout) binding.projectFormColors, colors, names,
                Math.floorMod(model.getColorIndex(), colors.length), index -> {
                    model.setColorIndex(index);
                    renderBubble();
                    renderPicker();
                });
    }

    // ---------------------------------------------------------------- questions

    private static ZoneId zone() {
        return ZoneId.systemDefault();
    }

    private void renderDeadline() {
        if (binding == null || model == null) return;
        long now = System.currentTimeMillis();
        ProjectFormModel.DeadlineChoice current = model.deadlineChoice(now, zone());
        binding.projectFormDeadlineValue.setText(current == ProjectFormModel.DeadlineChoice.NONE
                ? getString(R.string.projects_form_deadline_none)
                : DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(model.getDeadlineMillis())));
        ChipGroup group = binding.projectFormDeadlineChips;
        group.removeAllViews();
        addDeadlineChip(group, R.string.projects_form_deadline_none, ProjectFormModel.DeadlineChoice.NONE, current);
        addDeadlineChip(group, R.string.projects_form_deadline_end_month,
                ProjectFormModel.DeadlineChoice.END_OF_MONTH, current);
        addDeadlineChip(group, R.string.projects_form_deadline_month,
                ProjectFormModel.DeadlineChoice.IN_A_MONTH, current);
        Chip other = addChip(group, getString(R.string.projects_form_deadline_other), v -> pickDate());
        other.setChecked(current == ProjectFormModel.DeadlineChoice.OTHER);
        other.setChipIcon(tinted(R.drawable.ic_calendar_24, R.color.bb_on_surface_muted));
        other.setChipIconVisible(true);
    }

    private void addDeadlineChip(ChipGroup group, int label, ProjectFormModel.DeadlineChoice choice,
                                 ProjectFormModel.DeadlineChoice current) {
        Chip chip = addChip(group, getString(label), v -> {
            if (model == null) return;
            model.chooseDeadline(choice, System.currentTimeMillis(), zone());
            renderDeadline();
            renderSave();
        });
        chip.setChecked(choice == current);
    }

    private void pickDate() {
        if (model == null) return;
        long base = model.getDeadlineMillis() > 0 ? model.getDeadlineMillis() : System.currentTimeMillis();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.projects_picker_date_dialog_title)
                .setSelection(PickerLabels.dayToPickerSelection(base, zone()))
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            if (model == null) return;
            model.setDeadlineMillis(PickerLabels.pickerSelectionToDay(selection, zone()));
            renderDeadline();
            renderSave();
        });
        picker.show(getChildFragmentManager(), "projects_form_date");
        renderDeadline(); // the Other chip toggled itself on the tap: restore it until a day is chosen
    }

    private void renderAreas() {
        if (binding == null || model == null) return;
        ChipGroup group = binding.projectFormAreaChips;
        group.removeAllViews();
        String chosenName = null;
        boolean exists = model.getAreaId() == 0;
        if (areas != null) {
            for (Tag area : areas) {
                final int id = area.id;
                Chip chip = addChip(group, area.name, v -> {
                    if (model == null) return;
                    model.toggleArea(id);
                    renderAreas();
                    renderSave();
                });
                chip.setChipIcon(tinted(ProjectAccents.projectIconRes(area.iconKey), R.color.bb_on_surface_muted));
                chip.setChipIconVisible(true);
                chip.setOnLongClickListener(v -> {
                    navigator.openTagEditor(Tag.KIND_AREA, id);
                    return true;
                });
                boolean on = id == model.getAreaId();
                chip.setChecked(on);
                if (on) {
                    chosenName = area.name;
                    exists = true;
                }
            }
        }
        if (areas != null && !exists) {
            // The chosen area was deleted from the editor: the project goes without one.
            model.toggleArea(model.getAreaId());
            renderAreas();
            return;
        }
        Chip create = addChip(group, getString(R.string.projects_form_area_new), v -> {
            navigator.openTagEditor(Tag.KIND_AREA, 0);
        });
        create.setCheckable(false);
        create.setChipIcon(tinted(R.drawable.ic_add_24, R.color.bb_primary));
        create.setChipIconVisible(true);
        binding.projectFormAreaValue.setText(chosenName == null
                ? getString(R.string.projects_form_area_none) : chosenName);
    }

    private Chip addChip(ChipGroup group, CharSequence text, View.OnClickListener click) {
        Chip chip = (Chip) LayoutInflater.from(requireContext()).inflate(R.layout.picker_chip, group, false);
        chip.setText(text);
        chip.setCheckable(true);
        chip.setOnClickListener(click);
        group.addView(chip);
        return chip;
    }

    private Drawable tinted(int drawableRes, int colorRes) {
        Drawable d = ContextCompat.getDrawable(requireContext(), drawableRes).mutate();
        d.setTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), colorRes)));
        return d;
    }

    // ---------------------------------------------------------------- leaving

    private void save() {
        if (model == null || !model.isSaveEnabled()) return;
        binding.projectFormSave.setEnabled(false);
        final FragmentManager results = getParentFragmentManager();
        final boolean isNew = !viewModel.isEditing();
        viewModel.save(id -> {
            ProjectsResults.post(results, ProjectsResults.PROJECT_SAVED, ProjectsResults.projectSaved(id, isNew));
            leave();
        });
    }

    private void requestAndLeave(String key, Bundle result) {
        ProjectsResults.post(getParentFragmentManager(), key, result);
        leave();
    }

    /** X and system Back: leaves at once when nothing changed, else asks to discard. */
    private void close() {
        if (model == null || !model.isDirty()) {
            leave();
            return;
        }
        new AlertDialog.Builder(requireContext(), R.style.RoundedDialog)
                .setTitle(R.string.projects_form_discard_title)
                .setMessage(R.string.projects_form_discard_message)
                .setPositiveButton(R.string.projects_form_discard_confirm, (d, w) -> leave())
                .setNegativeButton(R.string.projects_form_discard_keep, null)
                .show();
    }

    private void leave() {
        if (!isAdded() || binding == null) return;
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(binding.getRoot().getWindowToken(), 0);
        NavHostFragment.findNavController(this).popBackStack();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) { }
    }
}
