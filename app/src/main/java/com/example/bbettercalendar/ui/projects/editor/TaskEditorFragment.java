package com.example.bbettercalendar.ui.projects.editor;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
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
import com.example.bbettercalendar.projects.Tag;
import com.example.bbettercalendar.projects.domain.SectionRef;
import com.example.bbettercalendar.projects.domain.TagRef;
import com.example.bbettercalendar.projects.domain.TaskDraft;
import com.example.bbettercalendar.ui.common.motion.ScreenTransitions;
import com.example.bbettercalendar.ui.common.widget.ProjectAccents;
import com.example.bbettercalendar.ui.projects.ProjectsNavigator;
import com.example.bbettercalendar.ui.projects.ProjectsResults;
import com.example.bbettercalendar.ui.projects.picker.DurationPickerView;
import com.example.bbettercalendar.ui.projects.picker.PickerHost;
import com.example.bbettercalendar.ui.projects.picker.PickerLabels;
import com.example.bbettercalendar.ui.projects.picker.PickerView;
import com.example.bbettercalendar.ui.projects.picker.PriorityPickerView;
import com.example.bbettercalendar.ui.projects.picker.SectionPickerView;
import com.example.bbettercalendar.ui.projects.picker.SubtasksPickerView;
import com.example.bbettercalendar.ui.projects.picker.TagPickerView;
import com.google.android.material.button.MaterialButton;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Full-screen task editor "D1" (rediseno-proyectos u9, spec 3.4): create or edit one task. A grid of tiles
 * shows the values; tapping a tile opens its picker (ui/projects/picker) right under the tile's row at full
 * width, one at a time. Saving goes through {@link TaskEditorViewModel#save} and tells the Projects screen with
 * {@link ProjectsResults#TASK_SAVED}; the trash asks it to delete with {@link ProjectsResults#TASK_DELETE_REQUESTED}.
 */
@AndroidEntryPoint
public class TaskEditorFragment extends Fragment implements PickerHost {

    public static final String ARG_PROJECT_ID = "projectId";
    public static final String ARG_ENTRY_ID = "entryId";
    public static final String ARG_DRAFT = "draft";

    private enum Tile { WHEN, FOCUS, PRIORITY, TAG, SECTION, SUBTASKS }

    private static final int[] PRIORITY_NAMES = {
            R.string.projects_picker_pri_none, R.string.projects_picker_pri_low,
            R.string.projects_picker_pri_medium, R.string.projects_picker_pri_high};
    /** D-P03: high = danger, medium = secondary, low and none = the neutral border colour. */
    private static final int[] PRIORITY_COLORS = {
            R.color.bb_checkbox_border, R.color.bb_checkbox_border, R.color.bb_secondary, R.color.bb_danger};

    private TaskEditorViewModel viewModel;
    private TaskEditorViewModel.State loaded;
    private TaskDraft draft;
    private final List<TagRef> tags = new ArrayList<>();

    private View root;
    private View scroll;
    private EditText titleField;
    private TextView addNote;
    private EditText noteField;
    private MaterialButton saveButton;
    private TextView barTitle;
    private View deleteButton;
    private View doneBlock;
    private MaterialButton doneButton;
    private EditText completionNote;
    private TextView focusedMinutes;

    private View whenTile;
    private TextView whenValue;
    private LinearLayout whenMeta;
    private View focusTile;
    private TextView focusValue;
    private LinearLayout focusMeta;
    private TextView priorityTile;
    private TextView tagTile;
    private TextView sectionTile;
    private View subtasksTile;
    private TextView subtasksLabel;
    private LinearLayout subtasksLines;
    private final FrameLayout[] slots = new FrameLayout[4];

    private Tile openTile;
    private PickerView openPicker;
    private boolean binding;
    private boolean closing;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScreenTransitions.apply(this);
        viewModel = new ViewModelProvider(this).get(TaskEditorViewModel.class);
        Bundle args = getArguments();
        int projectId = args == null ? 0 : args.getInt(ARG_PROJECT_ID, 0);
        int entryId = args == null ? 0 : args.getInt(ARG_ENTRY_ID, 0);
        TaskDraft given = null;
        if (args != null && args.getSerializable(ARG_DRAFT) instanceof TaskDraft) {
            given = (TaskDraft) args.getSerializable(ARG_DRAFT);
        }
        viewModel.start(projectId, entryId, given);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_task_editor, container, false);
        scroll = root.findViewById(R.id.taskEditorScroll);
        titleField = root.findViewById(R.id.taskEditorTitle);
        addNote = root.findViewById(R.id.taskEditorAddNote);
        noteField = root.findViewById(R.id.taskEditorNote);
        saveButton = root.findViewById(R.id.taskEditorSave);
        barTitle = root.findViewById(R.id.taskEditorBarTitle);
        deleteButton = root.findViewById(R.id.taskEditorDelete);
        doneBlock = root.findViewById(R.id.taskEditorDoneBlock);
        doneButton = root.findViewById(R.id.taskEditorDone);
        completionNote = root.findViewById(R.id.taskEditorCompletionNote);
        focusedMinutes = root.findViewById(R.id.taskEditorFocusedMinutes);
        whenTile = root.findViewById(R.id.taskEditorWhen);
        whenValue = root.findViewById(R.id.taskEditorWhenValue);
        whenMeta = root.findViewById(R.id.taskEditorWhenMeta);
        focusTile = root.findViewById(R.id.taskEditorFocus);
        focusValue = root.findViewById(R.id.taskEditorFocusValue);
        focusMeta = root.findViewById(R.id.taskEditorFocusMeta);
        priorityTile = root.findViewById(R.id.taskEditorPriority);
        tagTile = root.findViewById(R.id.taskEditorTag);
        sectionTile = root.findViewById(R.id.taskEditorSection);
        subtasksTile = root.findViewById(R.id.taskEditorSubtasks);
        subtasksLabel = root.findViewById(R.id.taskEditorSubtasksLabel);
        subtasksLines = root.findViewById(R.id.taskEditorSubtasksLines);
        slots[0] = root.findViewById(R.id.taskEditorSlot1);
        slots[1] = root.findViewById(R.id.taskEditorSlot2);
        slots[2] = root.findViewById(R.id.taskEditorSlot3);
        slots[3] = root.findViewById(R.id.taskEditorSlot4);

        ImageButton close = root.findViewById(R.id.taskEditorClose);
        ImageButton delete = root.findViewById(R.id.taskEditorDelete);
        TooltipCompat.setTooltipText(close, close.getContentDescription());
        TooltipCompat.setTooltipText(delete, delete.getContentDescription());
        close.setOnClickListener(v -> requestClose());
        delete.setOnClickListener(v -> deleteTask());
        saveButton.setOnClickListener(v -> save());
        scroll.setVisibility(View.INVISIBLE);
        saveButton.setEnabled(false);

        whenTile.setOnClickListener(v -> toggle(Tile.WHEN));
        focusTile.setOnClickListener(v -> toggle(Tile.FOCUS));
        priorityTile.setOnClickListener(v -> toggle(Tile.PRIORITY));
        tagTile.setOnClickListener(v -> toggle(Tile.TAG));
        sectionTile.setOnClickListener(v -> toggle(Tile.SECTION));
        subtasksTile.setOnClickListener(v -> toggle(Tile.SUBTASKS));

        titleField.addTextChangedListener(new SimpleWatcher(text -> {
            if (draft == null) return;
            draft.title = text;
            updateSave();
        }));
        noteField.addTextChangedListener(new SimpleWatcher(text -> {
            if (draft != null) draft.note = text;
        }));
        completionNote.addTextChangedListener(new SimpleWatcher(text -> {
            if (draft != null) draft.completionNote = text;
        }));
        addNote.setOnClickListener(v -> showNoteField(true));
        doneButton.setOnClickListener(v -> {
            if (draft == null) return;
            draft.done = !draft.done;
            renderDone();
        });

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        requestClose();
                    }
                });

        viewModel.getTags().observe(getViewLifecycleOwner(), list -> {
            tags.clear();
            if (list != null) {
                for (Tag t : list) tags.add(new TagRef(t.id, t.name, t.iconKey, t.colorKey, t.kind));
            }
            if (draft != null) {
                renderTiles();
                if (openTile == Tile.TAG && openPicker != null) openPicker.refresh();
            }
        });
        viewModel.getState().observe(getViewLifecycleOwner(), this::onLoaded);
        ScreenTransitions.postponeUntilReady(this);
        return root;
    }

    // ---------------------------------------------------------------- loading and rendering

    private void onLoaded(@Nullable TaskEditorViewModel.State state) {
        if (state == null) return;
        if (state.missing) {
            // The task was deleted while the screen was away: nothing to edit.
            pop();
            return;
        }
        loaded = state;
        draft = state.draft;
        binding = true;
        titleField.setText(draft.title);
        titleField.setSelection(titleField.getText().length());
        noteField.setText(draft.note);
        completionNote.setText(draft.completionNote);
        binding = false;
        boolean edit = state.isEdit();
        barTitle.setText(edit ? R.string.projects_editor_title_edit : R.string.projects_editor_title_new);
        deleteButton.setVisibility(edit ? View.VISIBLE : View.GONE);
        doneBlock.setVisibility(edit ? View.VISIBLE : View.GONE);
        showNoteField(draft.note != null && !draft.note.isEmpty());
        renderTiles();
        renderDone();
        updateSave();
        scroll.setVisibility(View.VISIBLE);
        ScreenTransitions.startWhenDrawn(this, root);
        if (!edit && draft.title.isEmpty() && openTile == null) {
            titleField.requestFocus();
            showKeyboard(titleField);
        }
    }

    private void showNoteField(boolean show) {
        noteField.setVisibility(show ? View.VISIBLE : View.GONE);
        addNote.setVisibility(show ? View.GONE : View.VISIBLE);
        if (show && draft != null && (draft.note == null || draft.note.isEmpty())) {
            noteField.requestFocus();
            showKeyboard(noteField);
        }
    }

    private void updateSave() {
        saveButton.setEnabled(draft != null && draft.canSave());
    }

    private void renderTiles() {
        if (draft == null || getContext() == null) return;
        Context ctx = requireContext();
        ZoneId zone = ZoneId.systemDefault();
        Locale locale = Locale.getDefault();

        // When
        Long day = draft.getDayStartMillis();
        TaskEditorTiles.DayKind kind = TaskEditorTiles.dayKind(day, System.currentTimeMillis(), zone);
        String dayText;
        switch (kind) {
            case TODAY: dayText = getString(R.string.projects_picker_date_today); break;
            case TOMORROW: dayText = getString(R.string.projects_picker_date_tomorrow); break;
            case DATE: dayText = TaskEditorTiles.dayDate(day, zone, locale); break;
            default: dayText = getString(R.string.projects_picker_date_none); break;
        }
        whenValue.setText(dayText);
        whenValue.setTextColor(ContextCompat.getColor(ctx, kind == TaskEditorTiles.DayKind.NONE
                ? R.color.bb_on_surface_muted : R.color.bb_on_surface));
        whenMeta.removeAllViews();
        StringBuilder whenDesc = new StringBuilder(getString(R.string.projects_editor_tile_when))
                .append(": ").append(dayText);
        if (draft.getTimeOfDayMinutes() != null) {
            String time = PickerLabels.timeLabel(draft.getTimeOfDayMinutes());
            addMeta(whenMeta, R.id.taskEditorMetaTime, R.drawable.ic_clock_v2_24, R.color.bb_on_surface_muted, time,
                    R.color.bb_on_surface, null);
            whenDesc.append(", ").append(time);
        }
        if (draft.getReminder() != TaskDraft.Reminder.NONE) {
            String reminder = reminderText(draft.getReminder());
            addMeta(whenMeta, R.id.taskEditorMetaReminder, R.drawable.ic_bell_24, R.color.bb_on_surface_muted, reminder,
                    R.color.bb_on_surface, null);
            whenDesc.append(", ").append(reminder);
        }
        whenTile.setContentDescription(whenDesc);

        // Focus
        String duration = TaskEditorTiles.durationText(draft);
        focusValue.setText(duration != null ? duration : getString(R.string.projects_picker_dur_free));
        focusValue.setTextColor(ContextCompat.getColor(ctx, duration == null
                ? R.color.bb_on_surface_muted : R.color.bb_on_surface));
        focusMeta.removeAllViews();
        StringBuilder focusDesc = new StringBuilder(getString(R.string.projects_editor_tile_focus))
                .append(": ").append(focusValue.getText());
        if (TaskEditorTiles.showsPomodoro(draft)) {
            String pomodoro = getString(R.string.projects_editor_pomodoro);
            addMeta(focusMeta, R.id.taskEditorMetaPomodoro, R.drawable.ic_timer_24, R.color.bb_sanct_primary, pomodoro,
                    R.color.bb_sanct_primary, null);
            focusDesc.append(", ").append(pomodoro);
        }
        if (TaskEditorTiles.showsLock(draft)) {
            String lock = getString(R.string.projects_editor_lock_desc);
            addMeta(focusMeta, R.id.taskEditorMetaLock, R.drawable.ic_lock_24, R.color.bb_on_surface_muted, "",
                    R.color.bb_on_surface, lock);
            focusDesc.append(", ").append(lock);
        }
        focusTile.setContentDescription(focusDesc);

        // Priority: icon, coloured dot and name; muted "Priority" without one.
        int pri = Math.max(0, Math.min(3, draft.priority));
        priorityTile.setCompoundDrawablesRelative(sized(R.drawable.ic_flag_24,
                pri == 0 ? R.color.bb_on_surface_muted : R.color.bb_primary), null, null, null);
        if (pri == 0) {
            priorityTile.setText(R.string.projects_editor_tile_priority);
            priorityTile.setTextColor(ContextCompat.getColor(ctx, R.color.bb_on_surface_muted));
        } else {
            SpannableStringBuilder sb = new SpannableStringBuilder(" ");
            sb.setSpan(centeredSpan(dot(PRIORITY_COLORS[pri])), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append("  ").append(getString(PRIORITY_NAMES[pri]));
            priorityTile.setText(sb);
            priorityTile.setTextColor(ContextCompat.getColor(ctx, R.color.bb_on_surface));
        }

        // Tag
        TagRef tag = findTag(draft.tagId);
        if (tag == null) {
            tagTile.setText(R.string.projects_editor_tile_tag);
            tagTile.setTextColor(ContextCompat.getColor(ctx, R.color.bb_on_surface_muted));
            tagTile.setCompoundDrawablesRelative(sized(R.drawable.ic_tag_24, R.color.bb_on_surface_muted),
                    null, null, null);
        } else {
            tagTile.setText(tag.name);
            tagTile.setTextColor(ContextCompat.getColor(ctx, R.color.bb_on_surface));
            tagTile.setCompoundDrawablesRelative(sized(ProjectAccents.tagIconRes(tag.iconKey),
                    ProjectAccents.tagColorRes(tag.colorKey)), null, null, null);
        }

        // Section
        sectionTile.setText(sectionName());
        sectionTile.setContentDescription(getString(R.string.projects_editor_tile_section) + ": " + sectionName());

        // Subtasks
        String progress = TaskEditorTiles.subtasksProgress(draft);
        subtasksLabel.setText(progress == null ? getString(R.string.projects_editor_tile_subtasks)
                : getString(R.string.projects_editor_subtasks_header, progress));
        subtasksLines.removeAllViews();
        if (progress == null) {
            subtasksLines.addView(muted(R.string.projects_editor_subtasks_none));
        } else {
            for (TaskDraft.Subtask s : TaskEditorTiles.visibleSubtasks(draft)) {
                subtasksLines.addView(subtaskLine(s));
            }
            int more = TaskEditorTiles.moreSubtasks(draft);
            if (more > 0) {
                TextView m = muted(0);
                m.setText(getString(R.string.projects_editor_subtasks_more, more));
                subtasksLines.addView(m);
            }
        }

        whenTile.setSelected(openTile == Tile.WHEN);
        focusTile.setSelected(openTile == Tile.FOCUS);
        priorityTile.setSelected(openTile == Tile.PRIORITY);
        tagTile.setSelected(openTile == Tile.TAG);
        sectionTile.setSelected(openTile == Tile.SECTION);
        subtasksTile.setSelected(openTile == Tile.SUBTASKS);
    }

    private String reminderText(TaskDraft.Reminder reminder) {
        switch (reminder) {
            case AT_TIME: return getString(R.string.projects_picker_rem_at_time);
            case MIN_15: return getString(R.string.projects_picker_rem_15);
            case HOUR_1: return getString(R.string.projects_picker_rem_60);
            default: return "";
        }
    }

    private void renderDone() {
        if (draft == null || getContext() == null) return;
        Context ctx = requireContext();
        boolean done = draft.done;
        if (done) {
            doneButton.setText(R.string.projects_editor_done_done);
            doneButton.setIconResource(R.drawable.ic_sprout_check);
            doneButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.bb_primary)));
            doneButton.setTextColor(ContextCompat.getColor(ctx, R.color.bb_on_primary));
            doneButton.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.bb_on_primary)));
            doneButton.setStrokeWidth(0);
        } else {
            doneButton.setText(R.string.projects_editor_done_mark);
            doneButton.setIconResource(R.drawable.ic_check_24);
            doneButton.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            doneButton.setTextColor(ContextCompat.getColor(ctx, R.color.bb_on_surface));
            doneButton.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.bb_on_surface)));
            doneButton.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.projects_editor_tile_stroke));
        }
        doneButton.setSelected(done);
        completionNote.setVisibility(done ? View.VISIBLE : View.GONE);
        int minutes = loaded == null ? 0 : loaded.attributedMinutes;
        if (minutes > 0) {
            focusedMinutes.setText(getString(R.string.projects_editor_focused_minutes, minutes));
            focusedMinutes.setVisibility(View.VISIBLE);
        } else {
            focusedMinutes.setVisibility(View.GONE);
        }
    }

    private void addMeta(LinearLayout parent, @IdRes int id, @DrawableRes int icon, @ColorRes int iconColor, CharSequence text,
                         @ColorRes int textColor, @Nullable CharSequence description) {
        TextView t = new TextView(requireContext());
        t.setId(id);
        t.setTextAppearance(R.style.TextAppearance_BBetter_EditorTileMeta);
        t.setTextColor(ContextCompat.getColor(requireContext(), textColor));
        t.setText(text);
        t.setGravity(android.view.Gravity.CENTER_VERTICAL);
        t.setCompoundDrawablePadding(getResources().getDimensionPixelSize(R.dimen.spacing_xs));
        Drawable d = ContextCompat.getDrawable(requireContext(), icon).mutate();
        d.setTint(ContextCompat.getColor(requireContext(), iconColor));
        int size = getResources().getDimensionPixelSize(R.dimen.projects_editor_meta_icon);
        d.setBounds(0, 0, size, size);
        t.setCompoundDrawablesRelative(d, null, null, null);
        if (description != null) t.setContentDescription(description);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = getResources().getDimensionPixelSize(R.dimen.spacing_xs);
        parent.addView(t, lp);
    }

    private TextView muted(int textRes) {
        TextView t = new TextView(requireContext());
        t.setTextAppearance(R.style.TextAppearance_BBetter_Body);
        t.setTextColor(ContextCompat.getColor(requireContext(), R.color.bb_on_surface_muted));
        if (textRes != 0) t.setText(textRes);
        return t;
    }

    private View subtaskLine(TaskDraft.Subtask s) {
        Context ctx = requireContext();
        LinearLayout line = new LinearLayout(ctx);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int box = getResources().getDimensionPixelSize(R.dimen.projects_editor_sub_box);
        View check = new View(ctx);
        check.setBackgroundResource(s.done ? R.drawable.bg_task_editor_sub_on : R.drawable.bg_task_editor_sub_off);
        check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(box, box);
        boxLp.setMarginEnd(getResources().getDimensionPixelSize(R.dimen.projects_editor_gap));
        line.addView(check, boxLp);
        TextView name = new TextView(ctx);
        name.setTextAppearance(R.style.TextAppearance_BBetter_Body);
        name.setText(s.title);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (s.done) {
            name.setPaintFlags(name.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            name.setTextColor(ContextCompat.getColor(ctx, R.color.bb_on_surface_muted));
        }
        line.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = getResources().getDimensionPixelSize(R.dimen.spacing_xs);
        line.setLayoutParams(lp);
        return line;
    }

    private Drawable sized(@DrawableRes int res, @ColorRes int color) {
        Drawable d = ContextCompat.getDrawable(requireContext(), res).mutate();
        d.setTint(ContextCompat.getColor(requireContext(), color));
        int size = getResources().getDimensionPixelSize(R.dimen.projects_editor_icon);
        d.setBounds(0, 0, size, size);
        return d;
    }

    /** An ImageSpan drawn at the vertical centre of its line (ALIGN_CENTER needs API 29). */
    private static ImageSpan centeredSpan(Drawable d) {
        return new ImageSpan(d) {
            @Override
            public void draw(@NonNull android.graphics.Canvas canvas, CharSequence text, int start, int end,
                             float x, int top, int y, int bottom, @NonNull android.graphics.Paint paint) {
                Drawable drawable = getDrawable();
                canvas.save();
                canvas.translate(x, top + (bottom - top - drawable.getBounds().bottom) / 2f);
                drawable.draw(canvas);
                canvas.restore();
            }
        };
    }

    private Drawable dot(@ColorRes int color) {
        int size = getResources().getDimensionPixelSize(R.dimen.projects_picker_dot);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(ContextCompat.getColor(requireContext(), color));
        d.setBounds(0, 0, size, size);
        return d;
    }

    @Nullable
    private TagRef findTag(long id) {
        if (id == 0) return null;
        for (TagRef t : tags) {
            if (t.id == id) return t;
        }
        return null;
    }

    @Nullable
    private String sectionName() {
        if (loaded == null || draft == null) return null;
        for (SectionRef s : loaded.sections) {
            if (s.id == draft.sectionId) return s.name;
        }
        return loaded.sections.isEmpty() ? null : loaded.sections.get(0).name;
    }

    // ---------------------------------------------------------------- tiles and pickers

    private void toggle(Tile tile) {
        if (draft == null) return;
        boolean same = openTile == tile;
        closePicker();
        if (!same) openPicker(tile);
        renderTiles();
    }

    private void closePicker() {
        if (openPicker != null && openPicker.getParent() instanceof ViewGroup) {
            FrameLayout slot = (FrameLayout) openPicker.getParent();
            slot.removeView(openPicker);
            slot.setVisibility(View.GONE);
        }
        openPicker = null;
        openTile = null;
    }

    private void openPicker(Tile tile) {
        hideKeyboard();
        PickerView picker;
        int slotIndex;
        switch (tile) {
            case WHEN: picker = new WhenPickerView(requireContext()); slotIndex = 0; break;
            case FOCUS: picker = new DurationPickerView(requireContext()); slotIndex = 0; break;
            case PRIORITY: picker = new PriorityPickerView(requireContext()); slotIndex = 1; break;
            case TAG: picker = new TagPickerView(requireContext()); slotIndex = 1; break;
            case SECTION: picker = new SectionPickerView(requireContext()); slotIndex = 2; break;
            default: picker = new SubtasksPickerView(requireContext()); slotIndex = 3; break;
        }
        FrameLayout slot = slots[slotIndex];
        slot.setBackgroundResource(R.drawable.bg_task_editor_tile);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slot.addView(picker, lp);
        ViewGroup.MarginLayoutParams slotLp = (ViewGroup.MarginLayoutParams) slot.getLayoutParams();
        slotLp.topMargin = getResources().getDimensionPixelSize(R.dimen.projects_editor_gap);
        slot.setLayoutParams(slotLp);
        slot.setVisibility(View.VISIBLE);
        picker.bind(draft, this);
        openTile = tile;
        openPicker = picker;
        slot.post(() -> {
            if (slot.getParent() != null) {
                slot.requestRectangleOnScreen(new Rect(0, 0, slot.getWidth(), slot.getHeight()), false);
            }
        });
    }

    // ---- PickerHost ----

    @NonNull
    @Override
    public FragmentManager getPickerFragmentManager() {
        return getChildFragmentManager();
    }

    @Override
    public void onDraftChanged() {
        if (binding) return;
        renderTiles();
        updateSave();
        if (openPicker != null) openPicker.refresh();
    }

    @Override
    public void onDone() {
        closePicker();
        renderTiles();
    }

    @Override
    public void onNewTag() {
        new ProjectsNavigator(this).openTagEditor(Tag.KIND_TAG, 0);
    }

    @Override
    public void onEditTag(long tagId) {
        new ProjectsNavigator(this).openTagEditor(Tag.KIND_TAG, (int) tagId);
    }

    @NonNull
    @Override
    public List<TagRef> getTags() {
        return tags;
    }

    @NonNull
    @Override
    public List<SectionRef> getSections() {
        return loaded == null ? new ArrayList<SectionRef>() : loaded.sections;
    }

    // ---------------------------------------------------------------- close, save, delete

    private boolean hasChanges() {
        return draft != null && loaded != null
                && !TaskEditorTiles.signature(draft).equals(loaded.baselineSignature);
    }

    private void requestClose() {
        if (!hasChanges()) {
            pop();
            return;
        }
        hideKeyboard();
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.projects_editor_discard_title)
                .setPositiveButton(R.string.projects_editor_discard_confirm, (d, w) -> pop())
                .setNegativeButton(R.string.projects_editor_discard_keep, null)
                .show();
    }

    private void save() {
        if (draft == null || !draft.canSave() || closing) return;
        boolean isNew = draft.editId == 0;
        String section = sectionName();
        viewModel.save(draft);
        ProjectsResults.post(getParentFragmentManager(), ProjectsResults.TASK_SAVED,
                ProjectsResults.taskSaved((int) draft.projectId, section, isNew));
        pop();
    }

    private void deleteTask() {
        if (draft == null || draft.editId == 0 || closing) return;
        ProjectsResults.post(getParentFragmentManager(), ProjectsResults.TASK_DELETE_REQUESTED,
                ProjectsResults.taskDeleteRequested((int) draft.editId));
        pop();
    }

    private void pop() {
        if (closing) return;
        closing = true;
        hideKeyboard();
        NavHostFragment.findNavController(this).popBackStack();
    }

    private void hideKeyboard() {
        if (root == null) return;
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
        View focused = root.findFocus();
        if (focused instanceof EditText) focused.clearFocus();
    }

    private void showKeyboard(View field) {
        field.post(() -> {
            if (getContext() == null) return;
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        openPicker = null;
        openTile = null;
        root = null;
    }

    // ---------------------------------------------------------------- helpers

    private interface TextCallback {
        void onText(String text);
    }

    /** Reports the text after every change, except while the screen sets the fields itself. */
    private final class SimpleWatcher implements TextWatcher {
        private final TextCallback callback;

        SimpleWatcher(TextCallback callback) {
            this.callback = callback;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            if (!binding) callback.onText(s.toString());
        }
    }
}
