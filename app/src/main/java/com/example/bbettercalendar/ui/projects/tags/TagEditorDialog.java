package com.example.bbettercalendar.ui.projects.tags;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.projects.Tag;
import com.example.bbettercalendar.projects.TagRepository;
import com.example.bbettercalendar.ui.common.widget.ProjectAccents;
import com.example.bbettercalendar.ui.projects.ProjectsResults;
import com.example.bbettercalendar.ui.projects.form.FormPickers;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Tag and area editor (rediseno-proyectos u10, spec 3.7). A task tag has a name, one of 8 tag icons and one
 * of 4 tag colours; an area has a name and one of the project icons, no colour. Create, edit and delete
 * (delete asks first; the repository leaves the tasks without tag / the projects without area). Every
 * change posts {@link ProjectsResults#TAG_SAVED}. Opened through {@code ProjectsNavigator.openTagEditor}.
 */
@AndroidEntryPoint
public class TagEditorDialog extends DialogFragment {

    public static final String TAG = "tag_editor_dialog";
    private static final String ARG_KIND = "kind";
    private static final String ARG_TAG_ID = "tagId";
    private static final String STATE_ICON = "icon";
    private static final String STATE_COLOR = "color";

    /** {@code kind} is {@link Tag#KIND_TAG} or {@link Tag#KIND_AREA}; {@code tagId} 0 = create. */
    public static TagEditorDialog newInstance(int kind, int tagId) {
        TagEditorDialog dialog = new TagEditorDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_KIND, kind);
        args.putInt(ARG_TAG_ID, tagId);
        dialog.setArguments(args);
        return dialog;
    }

    @Inject TagRepository tagRepository;

    private int kind;
    private int tagId;
    private String iconKey;
    private String colorKey;
    @Nullable private Tag existing;
    private boolean filled;
    private EditText nameField;
    private GridLayout icons;
    private LinearLayout colors;
    private AlertDialog dialog;

    private boolean isArea() {
        return kind == Tag.KIND_AREA;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = requireArguments();
        kind = args.getInt(ARG_KIND, Tag.KIND_TAG);
        tagId = args.getInt(ARG_TAG_ID, 0);
        String[] iconKeys = iconKeys();
        iconKey = savedInstanceState != null ? savedInstanceState.getString(STATE_ICON) : iconKeys[0];
        colorKey = savedInstanceState != null ? savedInstanceState.getString(STATE_COLOR)
                : ProjectAccents.DEFAULT_TAG_COLOR;
        if (iconKey == null) iconKey = iconKeys[0];
        if (colorKey == null) colorKey = ProjectAccents.DEFAULT_TAG_COLOR;

        Context context = requireContext();
        View body = LayoutInflater.from(context).inflate(R.layout.dialog_tag_editor, null, false);
        nameField = body.findViewById(R.id.tagEditorName);
        icons = body.findViewById(R.id.tagEditorIcons);
        colors = body.findViewById(R.id.tagEditorColors);
        if (isArea()) {
            body.findViewById(R.id.tagEditorColorLabel).setVisibility(View.GONE);
            colors.setVisibility(View.GONE);
        }
        nameField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                updateSaveButton();
            }
        });

        int title;
        if (isArea()) {
            title = tagId == 0 ? R.string.projects_tag_title_new_area : R.string.projects_tag_title_edit_area;
        } else {
            title = tagId == 0 ? R.string.projects_tag_title_new_tag : R.string.projects_tag_title_edit_tag;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.RoundedDialog)
                .setTitle(title)
                .setView(body)
                .setPositiveButton(R.string.projects_main_action_save, null)
                .setNegativeButton(R.string.projects_main_action_cancel, null);
        if (tagId != 0) builder.setNeutralButton(R.string.projects_tag_delete, null);
        dialog = builder.create();
        dialog.setOnShowListener(d -> {
            // Own click handlers: Save validates, Delete asks first, neither closes the dialog by itself.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> save());
            Button delete = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (delete != null) delete.setOnClickListener(v -> askDelete());
            updateSaveButton();
            if (tagId == 0) {
                nameField.requestFocus();
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(nameField, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        renderPickers();

        if (tagId != 0) {
            tagRepository.observe(kind).observe(this, list -> onTags(list));
        }
        return dialog;
    }

    private String[] iconKeys() {
        return isArea() ? ProjectAccents.PROJECT_ICON_KEYS : ProjectAccents.TAG_ICON_KEYS;
    }

    /** Edit mode: fills the fields once, from the stored tag; later emissions only refresh {@link #existing}. */
    private void onTags(@Nullable List<Tag> list) {
        existing = null;
        if (list != null) {
            for (Tag t : list) {
                if (t.id == tagId) {
                    existing = t;
                    break;
                }
            }
        }
        if (existing == null || filled) return;
        filled = true;
        if (nameField.getText().length() == 0) nameField.setText(existing.name);
        nameField.setSelection(nameField.getText().length());
        iconKey = existing.iconKey == null ? iconKey : existing.iconKey;
        if (!isArea() && existing.colorKey != null) colorKey = existing.colorKey;
        renderPickers();
        updateSaveButton();
    }

    private void renderPickers() {
        int accent = isArea() ? R.color.bb_on_surface_muted : ProjectAccents.tagColorRes(colorKey);
        FormPickers.fillIcons(icons, iconKeys(), !isArea(), iconKey, accent, key -> {
            iconKey = key;
            renderPickers();
        });
        if (isArea()) return;
        String[] keys = ProjectAccents.TAG_COLOR_KEYS;
        int[] res = new int[keys.length];
        String[] names = new String[keys.length];
        int selected = 0;
        for (int i = 0; i < keys.length; i++) {
            res[i] = ProjectAccents.tagColorRes(keys[i]);
            names[i] = FormPickers.tagColorName(requireContext(), keys[i]);
            if (keys[i].equals(colorKey)) selected = i;
        }
        FormPickers.fillColors(colors, res, names, selected, index -> {
            colorKey = ProjectAccents.TAG_COLOR_KEYS[index];
            renderPickers();
        });
    }

    private void updateSaveButton() {
        if (dialog == null) return;
        Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (save != null) save.setEnabled(nameField.getText().toString().trim().length() > 0);
    }

    private void save() {
        String name = nameField.getText().toString().trim();
        if (name.isEmpty()) return;
        FragmentManager results = getParentFragmentManager();
        final int savedKind = kind;
        String colorToStore = isArea() ? "" : colorKey;
        if (tagId == 0) {
            tagRepository.create(kind, name, iconKey, colorToStore,
                    id -> ProjectsResults.post(results, ProjectsResults.TAG_SAVED,
                            ProjectsResults.tagSaved(savedKind, id)));
        } else if (existing != null) {
            existing.name = name;
            existing.iconKey = iconKey;
            existing.colorKey = colorToStore;
            tagRepository.update(existing);
            ProjectsResults.post(results, ProjectsResults.TAG_SAVED, ProjectsResults.tagSaved(savedKind, tagId));
        }
        dismiss();
    }

    private void askDelete() {
        final Tag tag = existing;
        if (tag == null) return;
        final FragmentManager results = getParentFragmentManager();
        new AlertDialog.Builder(requireContext(), R.style.RoundedDialog)
                .setTitle(isArea() ? R.string.projects_tag_delete_title_area : R.string.projects_tag_delete_title_tag)
                .setMessage(isArea() ? R.string.projects_tag_delete_message_area
                        : R.string.projects_tag_delete_message_tag)
                .setPositiveButton(R.string.projects_tag_delete, (d, w) -> {
                    tagRepository.delete(tag);
                    ProjectsResults.post(results, ProjectsResults.TAG_SAVED, ProjectsResults.tagSaved(kind, tag.id));
                    dismiss();
                })
                .setNegativeButton(R.string.projects_main_action_cancel, null)
                .show();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_ICON, iconKey);
        outState.putString(STATE_COLOR, colorKey);
    }
}
