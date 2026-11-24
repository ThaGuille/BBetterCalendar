package com.example.bbettercalendar.ui.projects.picker;

import android.content.Context;
import android.graphics.Paint;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.projects.domain.TaskDraft;
import com.example.bbettercalendar.ui.common.widget.TaskCheckView;

/**
 * Subtasks picker: rows with a small check, the name and a remove button, and an "Add subtask" field
 * whose IME action adds the line. The field is built once so typing is never interrupted by a refresh.
 */
public class SubtasksPickerView extends PickerView {
    private final LinearLayout list;
    private final EditText input;

    public SubtasksPickerView(@NonNull Context context) {
        this(context, null);
    }

    public SubtasksPickerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        list = new LinearLayout(context);
        list.setId(R.id.pickerSubtaskList);
        list.setOrientation(VERTICAL);
        content().addView(list, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        input = (EditText) LayoutInflater.from(context).inflate(R.layout.picker_input, content(), false);
        input.setId(R.id.pickerSubtaskInput);
        input.setHint(R.string.projects_picker_subtask_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setSingleLine(true);
        input.setOnEditorActionListener((v, actionId, event) -> {
            // A hardware Enter reaches here as down and up: take the down only.
            if (event != null && event.getAction() != KeyEvent.ACTION_DOWN) return true;
            addFromInput();
            return true;
        });
        content().addView(input);
    }

    @Override
    protected int headerIconRes() {
        return R.drawable.ic_subtasks_24;
    }

    @Override
    protected int titleRes() {
        return R.string.projects_picker_title_subtasks;
    }

    private void addFromInput() {
        if (draft == null) return;
        String title = input.getText().toString().trim();
        if (title.isEmpty()) return;
        draft.subtasks.add(new TaskDraft.Subtask(title, false));
        input.setText("");
        changed();
    }

    @Override
    public void refresh() {
        if (draft == null) return;
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int i = 0; i < draft.subtasks.size(); i++) {
            final int index = i;
            final TaskDraft.Subtask sub = draft.subtasks.get(i);
            View row = inflater.inflate(R.layout.picker_subtask_row, list, false);
            TaskCheckView check = row.findViewById(R.id.pickerSubtaskCheck);
            check.setTaskTitle(sub.title);
            check.setChecked(sub.done);
            check.setOnCheckedChangeListener((v, checked) -> {
                draft.subtasks.set(index, new TaskDraft.Subtask(sub.title, checked));
                changed();
            });
            TextView name = row.findViewById(R.id.pickerSubtaskName);
            name.setText(sub.title);
            if (sub.done) {
                name.setPaintFlags(name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                name.setTextColor(ContextCompat.getColor(getContext(), R.color.bb_on_surface_muted));
            }
            ImageButton remove = row.findViewById(R.id.pickerSubtaskRemove);
            String desc = getContext().getString(R.string.projects_picker_subtask_remove, sub.title);
            remove.setContentDescription(desc);
            TooltipCompat.setTooltipText(remove, desc);
            remove.setOnClickListener(v -> {
                draft.subtasks.remove(index);
                changed();
            });
            list.addView(row);
        }
    }
}
