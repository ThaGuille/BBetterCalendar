package com.example.bbettercalendar.ui.projects.picker;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.projects.domain.TaskDraft;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

/**
 * More picker: Reminder (No, At the time, 15 min before, 1 h before), Note (multi-line text) and, only
 * while {@link RepeatAvailability#isEnabled()}, the Repeat row (No, Every day, Every week, Every month
 * with "Repeats until the project ends"). While Repeat is unavailable it is GONE and never read or written.
 */
public class MorePickerView extends PickerView {
    private static final TaskDraft.Repeat[] REPEATS = TaskDraft.Repeat.values();
    private static final TaskDraft.Reminder[] REMINDERS = TaskDraft.Reminder.values();
    private static final int[] REPEAT_IDS = {
            R.id.pickerRepNone, R.id.pickerRepDaily, R.id.pickerRepWeekly, R.id.pickerRepMonthly};
    private static final int[] REPEAT_NAMES = {
            R.string.projects_picker_no, R.string.projects_picker_rep_daily,
            R.string.projects_picker_rep_weekly, R.string.projects_picker_rep_monthly};
    private static final int[] REMINDER_IDS = {
            R.id.pickerRemNone, R.id.pickerRemAtTime, R.id.pickerRem15, R.id.pickerRem60};
    private static final int[] REMINDER_NAMES = {
            R.string.projects_picker_no, R.string.projects_picker_rem_at_time,
            R.string.projects_picker_rem_15, R.string.projects_picker_rem_60};

    private final LinearLayout repeatBlock;
    private final Chip[] repeatChips = new Chip[REPEATS.length];
    private final Chip[] reminderChips = new Chip[REMINDERS.length];
    private final EditText note;
    private final TextView noteLabel;
    private boolean binding;

    public MorePickerView(@NonNull Context context) {
        this(context, null);
    }

    public MorePickerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        LinearLayout c = content();

        repeatBlock = new LinearLayout(context);
        repeatBlock.setId(R.id.pickerRepeatGroup);
        repeatBlock.setOrientation(VERTICAL);
        c.addView(repeatBlock, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addLabel(repeatBlock, R.string.projects_picker_rep_label);
        ChipGroup repeatGroup = addGroup(repeatBlock);
        for (int i = 0; i < REPEATS.length; i++) {
            final TaskDraft.Repeat repeat = REPEATS[i];
            repeatChips[i] = addChip(repeatGroup, REPEAT_IDS[i], context.getString(REPEAT_NAMES[i]), v -> {
                if (draft != null) draft.setRepeat(repeat);
                changed();
            });
        }
        TextView repeatNote = new TextView(context);
        repeatNote.setId(R.id.pickerRepeatNote);
        repeatNote.setText(R.string.projects_picker_rep_note);
        repeatNote.setTextAppearance(R.style.TextAppearance_BBetter_Caption);
        LayoutParams noteParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = getResources().getDimensionPixelSize(R.dimen.projects_picker_gap);
        repeatBlock.addView(repeatNote, noteParams);

        addLabel(c, R.string.projects_picker_rem_label);
        ChipGroup reminderGroup = addGroup(c);
        for (int i = 0; i < REMINDERS.length; i++) {
            final TaskDraft.Reminder reminder = REMINDERS[i];
            reminderChips[i] = addChip(reminderGroup, REMINDER_IDS[i], context.getString(REMINDER_NAMES[i]), v -> {
                if (draft != null) draft.setReminder(reminder);
                changed();
            });
        }

        noteLabel = addLabel(c, R.string.projects_picker_note_label);
        note = (EditText) LayoutInflater.from(context).inflate(R.layout.picker_input, c, false);
        note.setId(R.id.pickerNoteInput);
        note.setHint(R.string.projects_picker_note_hint);
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        note.setGravity(Gravity.TOP | Gravity.START);
        note.setMinLines(3);
        note.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (binding || draft == null) return;
                draft.note = s.toString();
                // No refresh: it would not change what this field shows, and the host only needs to know.
                if (host != null) host.onDraftChanged();
            }
        });
        c.addView(note);
    }

    /** The full editor shows the note as its own field, so it hides the one of this picker. */
    public void setNoteVisible(boolean visible) {
        noteLabel.setVisibility(visible ? VISIBLE : GONE);
        note.setVisibility(visible ? VISIBLE : GONE);
    }

    @Override
    protected int headerIconRes() {
        return R.drawable.ic_more_vert_24;
    }

    @Override
    protected int titleRes() {
        return R.string.projects_picker_title_more;
    }

    @Override
    public void refresh() {
        if (draft == null) return;
        boolean repeatOn = RepeatAvailability.isEnabled();
        repeatBlock.setVisibility(repeatOn ? VISIBLE : GONE);
        if (repeatOn) {
            for (int i = 0; i < REPEATS.length; i++) repeatChips[i].setChecked(draft.getRepeat() == REPEATS[i]);
        }
        for (int i = 0; i < REMINDERS.length; i++) reminderChips[i].setChecked(draft.getReminder() == REMINDERS[i]);
        if (!note.hasFocus() && !note.getText().toString().equals(draft.note)) {
            binding = true;
            note.setText(draft.note);
            binding = false;
        }
    }
}
