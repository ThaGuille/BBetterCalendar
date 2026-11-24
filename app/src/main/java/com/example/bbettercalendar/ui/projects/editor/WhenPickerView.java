package com.example.bbettercalendar.ui.projects.editor;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.projects.domain.TaskDraft;
import com.example.bbettercalendar.ui.projects.picker.DatePickerView;
import com.example.bbettercalendar.ui.projects.picker.MorePickerView;
import com.example.bbettercalendar.ui.projects.picker.PickerHost;
import com.example.bbettercalendar.ui.projects.picker.PickerView;

/**
 * The picker of the When tile (spec 3.4): date and time ({@link DatePickerView}) with the reminder and, once
 * the repetition exists, repeat ({@link MorePickerView} without its note, the editor has its own note field).
 */
public class WhenPickerView extends PickerView {

    private final DatePickerView date;
    private final MorePickerView more;

    public WhenPickerView(@NonNull Context context) {
        this(context, null);
    }

    public WhenPickerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        date = new DatePickerView(context);
        more = new MorePickerView(context);
        more.setNoteVisible(false);
        LinearLayout c = content();
        c.setPadding(0, 0, 0, 0);
        c.addView(date, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        c.addView(more, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    @Override
    protected int headerIconRes() {
        return R.drawable.ic_calendar_24;
    }

    @Override
    protected int titleRes() {
        return R.string.projects_picker_title_date;
    }

    @Override
    public void bind(@NonNull TaskDraft draft, @NonNull PickerHost host) {
        date.bind(draft, host);
        more.bind(draft, host);
        super.bind(draft, host);
    }

    @Override
    public void refresh() {
        date.refresh();
        more.refresh();
    }
}
