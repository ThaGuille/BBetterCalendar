package com.example.bbettercalendar.ui.projects.quickadd;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.Tag;
import com.example.bbettercalendar.projects.domain.SectionRef;
import com.example.bbettercalendar.projects.domain.TagRef;
import com.example.bbettercalendar.projects.domain.TaskDraft;
import com.example.bbettercalendar.ui.common.widget.ProjectAccents;
import com.example.bbettercalendar.ui.projects.ProjectsNavigator;
import com.example.bbettercalendar.ui.projects.ProjectsResults;
import com.example.bbettercalendar.ui.projects.picker.DatePickerView;
import com.example.bbettercalendar.ui.projects.picker.DurationPickerView;
import com.example.bbettercalendar.ui.projects.picker.MorePickerView;
import com.example.bbettercalendar.ui.projects.picker.PickerHost;
import com.example.bbettercalendar.ui.projects.picker.PickerView;
import com.example.bbettercalendar.ui.projects.picker.PriorityPickerView;
import com.example.bbettercalendar.ui.projects.picker.RepeatAvailability;
import com.example.bbettercalendar.ui.projects.picker.SectionPickerView;
import com.example.bbettercalendar.ui.projects.picker.SubtasksPickerView;
import com.example.bbettercalendar.ui.projects.picker.TagPickerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.time.ZoneId;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Quick add "A2" (rediseno-proyectos u8, spec 3.3): a box glued on top of the keyboard with the context
 * line, the title and send button, the value chips and a fixed row of icons. Tapping an icon hides the
 * keyboard and shows its picker (ui/projects/picker) in the keyboard's place, at the last measured
 * keyboard height; its Done brings the keyboard back. A scrim dims the list; tapping outside closes
 * without saving. Send saves through ProjectTasksRepository.saveDraft and posts
 * {@link ProjectsResults#TASK_SAVED}; Expand opens the full editor with the draft.
 */
@AndroidEntryPoint
public class QuickAddDialog extends DialogFragment implements PickerHost {

    public static final String TAG = "quick_add_dialog";
    private static final String ARG_PROJECT_ID = "projectId";
    private static final String ARG_SECTION_ID = "sectionId";
    /** Minimum IME inset above the navigation bar that counts as the keyboard (dp). */
    private static final int KEYBOARD_MIN_DP = 100;
    /** The panel never shrinks below this (dp); it scrolls inside. */
    private static final int MIN_PANEL_DP = 120;

    /** Last keyboard height measured in this process (px); 0 = never. */
    private static int lastKeyboardHeight;

    public static QuickAddDialog newInstance(int projectId, int sectionId) {
        QuickAddDialog dialog = new QuickAddDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_PROJECT_ID, projectId);
        args.putInt(ARG_SECTION_ID, sectionId);
        dialog.setArguments(args);
        return dialog;
    }

    private QuickAddViewModel viewModel;
    private ProjectsNavigator navigator;
    private View root;
    private EditText title;
    private View send;
    private TextView projectName;
    private TextView sectionName;
    private ImageView projectIcon;
    private ChipGroup chipGroup;
    private FrameLayout panel;
    private QuickAddIconButton btnDate;
    private QuickAddIconButton btnDuration;
    private QuickAddIconButton btnPriority;
    private QuickAddIconButton btnTag;
    private QuickAddIconButton btnSubtasks;
    private QuickAddIconButton btnMore;
    private final Map<QuickAddViewModel.Picker, PickerView> pickers = new EnumMap<>(QuickAddViewModel.Picker.class);
    private int navigationBarHeight;
    private boolean keyboardUp;
    private boolean sectionsSeeded;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.Theme_BBetter_QuickAdd);
        Bundle args = requireArguments();
        viewModel = new ViewModelProvider(this).get(QuickAddViewModel.class);
        viewModel.init(args.getInt(ARG_PROJECT_ID), args.getInt(ARG_SECTION_ID));
        navigator = new ProjectsNavigator(this);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new Dialog(requireContext(), getTheme()) {
            @Override
            public void onBackPressed() {
                if (!handleBack()) super.onBackPressed();
            }
        };
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_quick_add, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        root = view.findViewById(R.id.quickAddRoot);
        title = view.findViewById(R.id.quickAddTitle);
        send = view.findViewById(R.id.quickAddSend);
        projectName = view.findViewById(R.id.quickAddProjectName);
        sectionName = view.findViewById(R.id.quickAddSectionName);
        projectIcon = view.findViewById(R.id.quickAddProjectIcon);
        chipGroup = view.findViewById(R.id.quickAddChips);
        panel = view.findViewById(R.id.quickAddPickerPanel);
        btnDate = view.findViewById(R.id.quickAddDate);
        btnDuration = view.findViewById(R.id.quickAddDuration);
        btnPriority = view.findViewById(R.id.quickAddPriority);
        btnTag = view.findViewById(R.id.quickAddTag);
        btnSubtasks = view.findViewById(R.id.quickAddSubtasks);
        btnMore = view.findViewById(R.id.quickAddMore);

        TaskDraft draft = viewModel.getDraft();

        root.setOnClickListener(v -> dismissAllowingStateLoss());
        view.findViewById(R.id.quickAddSection).setOnClickListener(v -> togglePicker(QuickAddViewModel.Picker.SECTION));
        btnDate.setOnClickListener(v -> togglePicker(QuickAddViewModel.Picker.DATE));
        btnDuration.setOnClickListener(v -> togglePicker(QuickAddViewModel.Picker.DURATION));
        btnPriority.setOnClickListener(v -> togglePicker(QuickAddViewModel.Picker.PRIORITY));
        btnTag.setOnClickListener(v -> togglePicker(QuickAddViewModel.Picker.TAG));
        btnSubtasks.setOnClickListener(v -> togglePicker(QuickAddViewModel.Picker.SUBTASKS));
        btnMore.setOnClickListener(v -> togglePicker(QuickAddViewModel.Picker.MORE));
        view.findViewById(R.id.quickAddExpand).setOnClickListener(v -> expand());
        send.setOnClickListener(v -> send());

        title.setText(draft.title);
        title.setSelection(title.getText().length());
        title.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                viewModel.getDraft().title = s.toString();
                send.setEnabled(viewModel.getDraft().canSave());
            }
        });
        title.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE || enter) {
                send();
                return true;
            }
            return false;
        });
        send.setEnabled(draft.canSave());

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            onInsets(v, insets);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
        // A picker with its own text field (subtasks, minutes, note) raises the keyboard again: the panel
        // then shrinks to what is left above it, so nothing is covered and nothing stacks over the box.
        root.getViewTreeObserver().addOnGlobalLayoutListener(this::fitPanel);

        viewModel.getProject().observe(getViewLifecycleOwner(), this::bindProject);
        viewModel.getSections().observe(getViewLifecycleOwner(), sections -> {
            TaskDraft d = viewModel.getDraft();
            if (!sectionsSeeded && d.sectionId == 0 && sections != null && !sections.isEmpty()) {
                sectionsSeeded = true;
                d.sectionId = sections.get(0).id;
            }
            refreshPickers();
            render();
        });
        viewModel.getTags().observe(getViewLifecycleOwner(), tags -> {
            refreshPickers();
            render();
        });

        render();
        if (viewModel.getPicker() != QuickAddViewModel.Picker.NONE) {
            QuickAddViewModel.Picker open = viewModel.getPicker();
            root.post(() -> showPicker(open));
        } else {
            title.requestFocus();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog == null || dialog.getWindow() == null) return;
        Window window = dialog.getWindow();
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        // Edge to edge: the keyboard comes as an IME inset (see onInsets), so the box sits on it and the
        // keyboard never covers the title. ADJUST_RESIZE makes API 24-29 deliver the same inset.
        WindowCompat.setDecorFitsSystemWindows(window, false);
        int state = viewModel.getPicker() == QuickAddViewModel.Picker.NONE
                ? WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                : WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN;
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | state);
    }

    @Override
    public void onDestroyView() {
        pickers.clear();
        root = null;
        super.onDestroyView();
    }

    // ---------------------------------------------------------------- keyboard and pickers

    /**
     * The window is edge to edge, so the keyboard arrives as an IME inset: the root takes the bigger of the
     * navigation bar and the keyboard as bottom padding and the box (glued to the bottom) sits on it. The
     * keyboard height seen here is remembered for the picker panel.
     */
    private void onInsets(View view, WindowInsetsCompat insets) {
        Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                | WindowInsetsCompat.Type.displayCutout());
        int ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
        navigationBarHeight = bars.bottom;
        float density = getResources().getDisplayMetrics().density;
        boolean up = insets.isVisible(WindowInsetsCompat.Type.ime())
                && ime > bars.bottom + KEYBOARD_MIN_DP * density;
        if (up) lastKeyboardHeight = ime;
        if (up != keyboardUp) {
            keyboardUp = up;
            view.post(this::render);
        }
        view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime));
    }

    /** Height of the picker panel: the keyboard it replaces (without the navigation bar), or the fallback. */
    private int panelHeight() {
        if (lastKeyboardHeight > 0) return Math.max(0, lastKeyboardHeight - navigationBarHeight);
        return getResources().getDimensionPixelSize(R.dimen.projects_quick_keyboard_fallback);
    }

    /** Keeps the open panel at its usual height, or at what fits above the keyboard when that is less. */
    private void fitPanel() {
        if (root == null || panel.getVisibility() != View.VISIBLE) return;
        ViewGroup box = (ViewGroup) root.findViewById(R.id.quickAddBox);
        int others = box.getPaddingTop() + box.getPaddingBottom();
        for (int i = 0; i < box.getChildCount(); i++) {
            View child = box.getChildAt(i);
            if (child == panel || child.getVisibility() == View.GONE) continue;
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
            others += child.getHeight() + lp.topMargin + lp.bottomMargin;
        }
        int available = root.getHeight() - root.getPaddingTop() - root.getPaddingBottom() - others;
        int min = (int) (MIN_PANEL_DP * getResources().getDisplayMetrics().density);
        int target = Math.max(min, Math.min(panelHeight(), available));
        ViewGroup.LayoutParams lp = panel.getLayoutParams();
        if (lp.height != target) {
            lp.height = target;
            panel.setLayoutParams(lp);
        }
    }

    private void togglePicker(QuickAddViewModel.Picker picker) {
        if (viewModel.getPicker() == picker) {
            closePicker();
        } else {
            showPicker(picker);
        }
    }

    private void showPicker(QuickAddViewModel.Picker picker) {
        if (root == null) return;
        viewModel.setPicker(picker);
        PickerView view = pickerFor(picker);
        view.refresh();
        panel.removeAllViews();
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        panel.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ViewGroup.LayoutParams lp = panel.getLayoutParams();
        lp.height = panelHeight();
        panel.setLayoutParams(lp);
        panel.setVisibility(View.VISIBLE);
        hideKeyboard();
        render();
    }

    private void closePicker() {
        if (root == null) return;
        viewModel.setPicker(QuickAddViewModel.Picker.NONE);
        panel.setVisibility(View.GONE);
        panel.removeAllViews();
        render();
        title.requestFocus();
        showKeyboard();
    }

    private boolean handleBack() {
        if (viewModel.getPicker() != QuickAddViewModel.Picker.NONE) {
            closePicker();
            return true;
        }
        return false;
    }

    private void hideKeyboard() {
        View focus = root != null ? root.findFocus() : null;
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && root != null) {
            imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
        }
        if (focus != null && focus != title) focus.clearFocus();
    }

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(title, InputMethodManager.SHOW_IMPLICIT);
    }

    private PickerView pickerFor(QuickAddViewModel.Picker picker) {
        PickerView view = pickers.get(picker);
        if (view != null) return view;
        Context context = requireContext();
        switch (picker) {
            case DATE: view = new DatePickerView(context); break;
            case DURATION: view = new DurationPickerView(context); break;
            case PRIORITY: view = new PriorityPickerView(context); break;
            case TAG: view = new TagPickerView(context); break;
            case SUBTASKS: view = new SubtasksPickerView(context); break;
            case MORE: view = new MorePickerView(context); break;
            default: view = new SectionPickerView(context); break;
        }
        view.setHeaderVisible(true);
        view.bind(viewModel.getDraft(), this);
        pickers.put(picker, view);
        return view;
    }

    private void refreshPickers() {
        for (PickerView view : pickers.values()) view.refresh();
    }

    // ---------------------------------------------------------------- rendering

    private void bindProject(@Nullable Project project) {
        if (project == null) return;
        projectName.setText(project.name);
        projectIcon.setImageResource(ProjectAccents.projectIconRes(project.iconKey));
        projectIcon.setColorFilter(ContextCompat.getColor(requireContext(),
                ProjectAccents.colorResFor(project.colorIndex)));
    }

    /** The context line, the chips and the icon row from the draft. */
    private void render() {
        if (root == null) return;
        TaskDraft draft = viewModel.getDraft();
        QuickAddViewModel.Picker open = viewModel.getPicker();
        boolean repeat = RepeatAvailability.isEnabled();

        SectionRef section = sectionOf(draft.sectionId);
        String name = section != null ? section.name : "";
        sectionName.setText(getString(R.string.projects_quick_context_separator) + " " + name);
        root.findViewById(R.id.quickAddSection).setContentDescription(
                getString(R.string.projects_quick_section_desc, name));

        btnDate.setState(QuickAddChips.hasDate(draft), open == QuickAddViewModel.Picker.DATE);
        btnDuration.setState(QuickAddChips.hasDuration(draft), open == QuickAddViewModel.Picker.DURATION);
        btnPriority.setState(QuickAddChips.hasPriority(draft), open == QuickAddViewModel.Picker.PRIORITY);
        btnTag.setState(QuickAddChips.hasTag(draft), open == QuickAddViewModel.Picker.TAG);
        btnSubtasks.setState(QuickAddChips.hasSubtasks(draft), open == QuickAddViewModel.Picker.SUBTASKS);
        btnMore.setState(QuickAddChips.hasMore(draft, repeat), open == QuickAddViewModel.Picker.MORE);

        List<QuickAddChips.Chip> chips = QuickAddChips.derive(draft, getTags(), repeat,
                System.currentTimeMillis(), ZoneId.systemDefault(), locale(), new ResourceLabels());
        chipGroup.removeAllViews();
        for (QuickAddChips.Chip spec : chips) chipGroup.addView(buildChip(spec));
        // A picker with its own text field raises the keyboard: the chips step aside so the picker keeps room.
        boolean roomForChips = !(open != QuickAddViewModel.Picker.NONE && keyboardUp);
        chipGroup.setVisibility(chips.isEmpty() || !roomForChips ? View.GONE : View.VISIBLE);
    }

    private Chip buildChip(QuickAddChips.Chip spec) {
        Chip chip = (Chip) LayoutInflater.from(requireContext())
                .inflate(R.layout.view_quick_add_value_chip, chipGroup, false);
        chip.setText(spec.label);
        QuickAddViewModel.Picker target;
        Drawable first = null;
        Drawable second = null;
        switch (spec.kind) {
            case DATE:
                chip.setId(R.id.quickAddChipDate);
                target = QuickAddViewModel.Picker.DATE;
                if (spec.repeatIcon) first = tinted(R.drawable.ic_repeat_24, R.color.bb_on_surface_muted);
                if (spec.reminderIcon) {
                    Drawable bell = tinted(R.drawable.ic_bell_24, R.color.bb_on_surface_muted);
                    if (first == null) first = bell; else second = bell;
                }
                break;
            case DURATION:
                chip.setId(R.id.quickAddChipDuration);
                target = QuickAddViewModel.Picker.DURATION;
                // Focus things use the existing amber (bb_sanct_*).
                if (spec.pomodoroIcon) first = tinted(R.drawable.ic_timer_24, R.color.bb_sanct_primary);
                if (spec.blockIcon) {
                    Drawable lock = tinted(R.drawable.ic_lock_24, R.color.bb_on_surface_muted);
                    if (first == null) first = lock; else second = lock;
                }
                break;
            case PRIORITY:
                chip.setId(R.id.quickAddChipPriority);
                target = QuickAddViewModel.Picker.PRIORITY;
                first = priorityDot(spec.priority);
                break;
            case TAG:
                chip.setId(R.id.quickAddChipTag);
                target = QuickAddViewModel.Picker.TAG;
                if (spec.tag != null) {
                    first = tinted(ProjectAccents.tagIconRes(spec.tag.iconKey),
                            ProjectAccents.tagColorRes(spec.tag.colorKey));
                }
                break;
            case SUBTASKS:
                chip.setId(R.id.quickAddChipSubtasks);
                target = QuickAddViewModel.Picker.SUBTASKS;
                first = tinted(R.drawable.ic_subtasks_24, R.color.bb_on_surface_muted);
                break;
            default:
                chip.setId(R.id.quickAddChipNote);
                target = QuickAddViewModel.Picker.MORE;
                first = tinted(R.drawable.ic_note_24, R.color.bb_on_surface_muted);
                break;
        }
        chip.setChipIcon(first);
        chip.setChipIconVisible(first != null);
        chip.setCloseIcon(second);
        chip.setCloseIconVisible(second != null);
        View.OnClickListener open = v -> showPicker(target);
        chip.setOnClickListener(open);
        if (second != null) chip.setOnCloseIconClickListener(open);
        return chip;
    }

    private Drawable tinted(@DrawableRes int drawable, @ColorRes int color) {
        Drawable d = ContextCompat.getDrawable(requireContext(), drawable).mutate();
        d.setTint(ContextCompat.getColor(requireContext(), color));
        return d;
    }

    /** D-P03: high = danger, medium = secondary, low = the neutral border colour. */
    private Drawable priorityDot(int priority) {
        int color = priority >= 3 ? R.color.bb_danger : priority == 2 ? R.color.bb_secondary
                : R.color.bb_checkbox_border;
        int size = getResources().getDimensionPixelSize(R.dimen.projects_picker_dot);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(ContextCompat.getColor(requireContext(), color));
        d.setSize(size, size);
        return d;
    }

    private Locale locale() {
        return getResources().getConfiguration().getLocales().get(0);
    }

    @Nullable
    private SectionRef sectionOf(long sectionId) {
        List<SectionRef> sections = getSections();
        for (SectionRef s : sections) {
            if (s.id == sectionId) return s;
        }
        return sections.isEmpty() ? null : sections.get(0);
    }

    private final class ResourceLabels implements QuickAddChips.Labels {
        @Override public String today() { return getString(R.string.projects_quick_chip_today); }
        @Override public String tomorrow() { return getString(R.string.projects_quick_chip_tomorrow); }
        @Override public String withNote() { return getString(R.string.projects_quick_chip_note); }

        @Override
        public String priority(int priority) {
            return getString(priority >= 3 ? R.string.projects_picker_pri_high
                    : priority == 2 ? R.string.projects_picker_pri_medium : R.string.projects_picker_pri_low);
        }

        @Override
        public String subtasks(int count) {
            return getResources().getQuantityString(R.plurals.projects_quick_chip_subtasks, count, count);
        }
    }

    // ---------------------------------------------------------------- actions

    private void send() {
        TaskDraft draft = viewModel.getDraft();
        if (!draft.canSave() || !viewModel.beginSend()) return;
        FragmentManager fm = getParentFragmentManager();
        int projectId = viewModel.getProjectId();
        SectionRef section = sectionOf(draft.sectionId);
        String sectionLabel = section != null ? section.name : null;
        viewModel.save(id -> ProjectsResults.post(fm, ProjectsResults.TASK_SAVED,
                ProjectsResults.taskSaved(projectId, sectionLabel, true)));
        hideKeyboard();
        dismissAllowingStateLoss();
    }

    private void expand() {
        TaskDraft draft = viewModel.getDraft();
        hideKeyboard();
        navigator.openTaskEditorWithDraft(viewModel.getProjectId(), draft);
        dismissAllowingStateLoss();
    }

    // ---------------------------------------------------------------- PickerHost

    @NonNull
    @Override
    public FragmentManager getPickerFragmentManager() {
        return getChildFragmentManager();
    }

    @Override
    public void onDraftChanged() {
        render();
    }

    @Override
    public void onDone() {
        closePicker();
    }

    @Override
    public void onNewTag() {
        navigator.openTagEditor(Tag.KIND_TAG, 0);
    }

    @Override
    public void onEditTag(long tagId) {
        navigator.openTagEditor(Tag.KIND_TAG, (int) tagId);
    }

    @NonNull
    @Override
    public List<TagRef> getTags() {
        List<TagRef> tags = viewModel.getTags() != null ? viewModel.getTags().getValue() : null;
        return tags == null ? Collections.<TagRef>emptyList() : tags;
    }

    @NonNull
    @Override
    public List<SectionRef> getSections() {
        return QuickAddViewModel.orEmpty(viewModel.getSections() != null ? viewModel.getSections().getValue() : null);
    }
}
