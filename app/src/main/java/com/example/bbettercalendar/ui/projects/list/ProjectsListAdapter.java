package com.example.bbettercalendar.ui.projects.list;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.example.bbettercalendar.projects.TaskSubtask;
import com.example.bbettercalendar.projects.domain.NextTaskRule;
import com.example.bbettercalendar.projects.domain.TaskSnapshot;
import com.example.bbettercalendar.ui.common.widget.FocusTimerButton;
import com.example.bbettercalendar.ui.common.widget.ProgressRingView;
import com.example.bbettercalendar.ui.common.widget.ProjectAccents;
import com.example.bbettercalendar.ui.common.widget.TaskCheckView;
import com.example.bbettercalendar.ui.projects.ProjectsScreenModel;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The scrolling content of the Projects screen: the R2 summary, the sort label, one card per section
 * (with its task rows inside) and the add-section button, or the empty state of a project without tasks.
 * Rows of a card are bound in place (views are reused, not rebuilt) so the check pop is not cut short.
 */
public class ProjectsListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    /** What the screen does with the taps. */
    public interface Host {
        void onSummaryFocus(@NonNull TaskSnapshot task);
        void onSectionToggle(int sectionId);
        void onSectionAdd(int sectionId);
        void onSectionMenu(@NonNull ProjectsScreenModel.Section section, @NonNull View anchor);
        void onAddSection();
        void onAddFirstTask();
        void onTaskChecked(@NonNull TaskSnapshot task, boolean checked);
        void onTaskOpen(@NonNull TaskSnapshot task);
        void onTaskFocus(@NonNull TaskSnapshot task);
        void onSubtaskToggle(@NonNull TaskSubtask subtask);
    }

    private static final int T_SUMMARY = 0;
    private static final int T_SORT = 1;
    private static final int T_SECTION = 2;
    private static final int T_ADD_SECTION = 3;
    private static final int T_EMPTY = 4;

    private final Host host;
    private final Set<Long> tagOpen = new HashSet<>();
    private final Set<Long> subtasksOpen = new HashSet<>();
    private ProjectsScreenModel model;
    private int runningEntryId;
    private float runningProgress;

    public ProjectsListAdapter(@NonNull Host host) {
        this.host = host;
    }

    public void submit(ProjectsScreenModel model) {
        this.model = model;
        notifyDataSetChanged();
    }

    /** The Focus session is running for {@code entryId} (0 = none) with this progress. */
    public void setRunning(int entryId, float progress) {
        if (entryId == runningEntryId && Math.abs(progress - runningProgress) < 0.01f) return;
        runningEntryId = entryId;
        runningProgress = progress;
        notifyDataSetChanged();
    }

    // ---------------------------------------------------------------- adapter

    @Override
    public int getItemCount() {
        if (model == null || model.noProjects()) return 0;
        if (model.noTasks()) return 2;
        return 3 + model.sections.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) return T_SUMMARY;
        if (model.noTasks()) return T_EMPTY;
        if (position == 1) return T_SORT;
        return position == getItemCount() - 1 ? T_ADD_SECTION : T_SECTION;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater in = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case T_SUMMARY:
                return new Holder(in.inflate(R.layout.item_projects_summary, parent, false));
            case T_SORT:
                return new Holder(in.inflate(R.layout.item_projects_sort_label, parent, false));
            case T_ADD_SECTION:
                return new Holder(in.inflate(R.layout.item_projects_add_section, parent, false));
            case T_EMPTY:
                return new Holder(in.inflate(R.layout.item_projects_empty_tasks, parent, false));
            default:
                return new Holder(in.inflate(R.layout.item_projects_section, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        View v = holder.itemView;
        switch (getItemViewType(position)) {
            case T_SUMMARY:
                bindSummary(v);
                break;
            case T_SORT:
                ((TextView) v).setText(sortLabel(v.getContext(), model.sort));
                break;
            case T_ADD_SECTION:
                v.setOnClickListener(x -> host.onAddSection());
                break;
            case T_EMPTY:
                v.findViewById(R.id.projectsAddFirstTask).setOnClickListener(x -> host.onAddFirstTask());
                break;
            default:
                bindSection(v, model.sections.get(position - 2));
                break;
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        Holder(@NonNull View itemView) {
            super(itemView);
        }
    }

    /** Text of the sort label and the content description of the sort button. */
    public static String sortLabel(Context context, com.example.bbettercalendar.projects.domain.TaskOrdering.Mode mode) {
        switch (mode) {
            case PRIORITY:
                return context.getString(R.string.projects_main_sort_priority);
            case RECENT:
                return context.getString(R.string.projects_main_sort_recent);
            case UPCOMING:
            default:
                return context.getString(R.string.projects_main_sort_upcoming);
        }
    }

    // ---------------------------------------------------------------- summary

    private void bindSummary(View v) {
        Context c = v.getContext();
        int accent = ContextCompat.getColor(c, ProjectAccents.colorResFor(
                model.selectedProject == null ? 0 : model.selectedProject.colorIndex));
        v.findViewById(R.id.projectsSummaryGlow).setBackgroundTintList(ColorStateList.valueOf(accent));
        ProgressRingView ring = v.findViewById(R.id.projectsSummaryRing);
        ring.setRingColor(accent);
        ring.setProgress(model.percent, false);

        TextView label = v.findViewById(R.id.projectsSummaryLabel);
        TextView title = v.findViewById(R.id.projectsSummaryTitle);
        FocusTimerButton focus = v.findViewById(R.id.projectsSummaryFocus);
        TaskSnapshot next = model.next;
        if (model.summaryState == NextTaskRule.SummaryState.NEXT && next != null) {
            label.setVisibility(View.VISIBLE);
            title.setText(next.title);
            focus.setVisibility(View.VISIBLE);
            focus.setGoal(next.attributedMinutes, next.targetMinutes);
            applyRunning(focus, next.id);
            focus.setContentDescription(c.getString(R.string.project_focus_desc, next.title));
            TooltipCompat.setTooltipText(focus, focus.getContentDescription());
            focus.setOnClickListener(x -> host.onSummaryFocus(next));
        } else {
            label.setVisibility(View.GONE);
            title.setText(model.summaryState == NextTaskRule.SummaryState.ALL_DONE
                    ? R.string.projects_main_summary_all_done : R.string.projects_main_summary_no_tasks);
            focus.setVisibility(View.GONE);
        }

        TextView focused = v.findViewById(R.id.projectsSummaryFocusedText);
        String minutes = FormatHelper.formatDuration(model.focusedWeekMinutes * 60_000L);
        focused.setText(minutes);
        View focusedGroup = v.findViewById(R.id.projectsSummaryFocused);
        focusedGroup.setContentDescription(c.getString(R.string.projects_main_summary_focused_desc, minutes));

        View daysGroup = v.findViewById(R.id.projectsSummaryDays);
        if (model.daysToDue == null) {
            daysGroup.setVisibility(View.GONE);
        } else {
            daysGroup.setVisibility(View.VISIBLE);
            ((TextView) v.findViewById(R.id.projectsSummaryDaysText))
                    .setText(c.getString(R.string.projects_main_summary_days, model.daysToDue));
            daysGroup.setContentDescription(c.getString(R.string.projects_main_summary_days_desc, model.daysToDue));
        }
    }

    private void applyRunning(FocusTimerButton button, long entryId) {
        boolean running = runningEntryId != 0 && runningEntryId == entryId;
        button.setRunning(running);
        if (running) button.setProgress(runningProgress);
    }

    // ---------------------------------------------------------------- sections

    private void bindSection(View card, ProjectsScreenModel.Section s) {
        Context c = card.getContext();
        ((TextView) card.findViewById(R.id.projectsSectionName)).setText(s.name);
        LinearProgressIndicator bar = card.findViewById(R.id.projectsSectionBar);
        bar.setProgressCompat(s.total == 0 ? 0 : Math.round(100f * s.done / s.total), false);
        ((TextView) card.findViewById(R.id.projectsSectionCount))
                .setText(c.getString(R.string.projects_main_section_count, s.done, s.total));
        ImageView chevron = card.findViewById(R.id.projectsSectionChevron);
        chevron.setRotation(s.collapsed ? 0f : 90f);

        View header = card.findViewById(R.id.projectsSectionHeader);
        header.setContentDescription(c.getString(s.collapsed
                ? R.string.projects_main_section_expand_desc
                : R.string.projects_main_section_collapse_desc, s.name));
        header.setOnClickListener(x -> host.onSectionToggle(s.id));
        header.setOnLongClickListener(x -> {
            host.onSectionMenu(s, header);
            return true;
        });
        ImageButton add = card.findViewById(R.id.projectsSectionAdd);
        add.setContentDescription(c.getString(R.string.projects_main_section_add_desc, s.name));
        TooltipCompat.setTooltipText(add, add.getContentDescription());
        add.setOnClickListener(x -> host.onSectionAdd(s.id));

        LinearLayout rows = card.findViewById(R.id.projectsSectionRows);
        if (s.collapsed) {
            rows.setVisibility(View.GONE);
            return;
        }
        rows.setVisibility(View.VISIBLE);
        LayoutInflater in = LayoutInflater.from(c);
        while (rows.getChildCount() < s.rows.size()) {
            View row = in.inflate(R.layout.item_projects_task_row, rows, false);
            row.setTag(new RowViews(row));
            rows.addView(row);
        }
        while (rows.getChildCount() > s.rows.size()) {
            rows.removeViewAt(rows.getChildCount() - 1);
        }
        for (int i = 0; i < s.rows.size(); i++) {
            bindRow(rows.getChildAt(i), s.rows.get(i));
        }
    }

    // ---------------------------------------------------------------- task rows

    private static final class RowViews {
        final TaskCheckView check;
        final View body;
        final TextView title;
        final View meta;
        final TextView when;
        final View duration;
        final View repeat;
        final View reminder;
        final View lock;
        final View note;
        final View tag;
        final View tagPill;
        final ImageView tagIcon;
        final TextView tagName;
        final FocusTimerButton focus;
        final SubtaskCountView subtasks;
        final LinearLayout subtaskList;

        RowViews(View row) {
            check = row.findViewById(R.id.projectsTaskCheck);
            body = row.findViewById(R.id.projectsTaskBody);
            title = row.findViewById(R.id.projectsTaskTitle);
            meta = row.findViewById(R.id.projectsTaskMeta);
            when = row.findViewById(R.id.projectsTaskWhen);
            duration = row.findViewById(R.id.projectsTaskIconDuration);
            repeat = row.findViewById(R.id.projectsTaskIconRepeat);
            reminder = row.findViewById(R.id.projectsTaskIconReminder);
            lock = row.findViewById(R.id.projectsTaskIconLock);
            note = row.findViewById(R.id.projectsTaskIconNote);
            tag = row.findViewById(R.id.projectsTaskTag);
            tagPill = row.findViewById(R.id.projectsTaskTagPill);
            tagIcon = row.findViewById(R.id.projectsTaskTagIcon);
            tagName = row.findViewById(R.id.projectsTaskTagName);
            focus = row.findViewById(R.id.projectsTaskFocus);
            subtasks = row.findViewById(R.id.projectsTaskSubtasks);
            subtaskList = row.findViewById(R.id.projectsTaskSubtaskList);
        }
    }

    private void bindRow(View row, ProjectsScreenModel.Row r) {
        Context c = row.getContext();
        RowViews v = (RowViews) row.getTag();
        TaskSnapshot t = r.task;

        v.check.setOnCheckedChangeListener(null);
        v.check.setPriority(t.priority);
        v.check.setTaskTitle(t.title);
        v.check.setChecked(t.done);
        v.check.setOnCheckedChangeListener((view, checked) -> host.onTaskChecked(t, checked));

        v.title.setText(t.title);
        int titleFlags = v.title.getPaintFlags();
        v.title.setPaintFlags(t.done ? titleFlags | Paint.STRIKE_THRU_TEXT_FLAG
                : titleFlags & ~Paint.STRIKE_THRU_TEXT_FLAG);
        v.title.setTextColor(ContextCompat.getColor(c,
                t.done ? R.color.bb_on_surface_muted : R.color.bb_on_surface));
        v.body.setOnClickListener(x -> host.onTaskOpen(t));

        bindWhen(v, t, c);
        v.duration.setVisibility(r.hasDuration ? View.VISIBLE : View.GONE);
        v.repeat.setVisibility(r.repeats ? View.VISIBLE : View.GONE);
        v.reminder.setVisibility(r.hasReminder ? View.VISIBLE : View.GONE);
        v.lock.setVisibility(t.blockOnFocus ? View.VISIBLE : View.GONE);
        v.note.setVisibility(r.hasNote ? View.VISIBLE : View.GONE);

        if (r.tag == null) {
            v.tag.setVisibility(View.GONE);
        } else {
            v.tag.setVisibility(View.VISIBLE);
            int color = ContextCompat.getColor(c, ProjectAccents.tagColorRes(r.tag.colorKey));
            v.tagIcon.setImageResource(ProjectAccents.tagIconRes(r.tag.iconKey));
            v.tagIcon.setImageTintList(ColorStateList.valueOf(color));
            boolean open = tagOpen.contains(t.id);
            v.tagName.setVisibility(open ? View.VISIBLE : View.GONE);
            v.tagName.setText(r.tag.name);
            v.tagName.setTextColor(color);
            v.tagPill.setBackgroundResource(open ? R.drawable.bg_projects_tag_pill : 0);
            v.tag.setContentDescription(c.getString(R.string.projects_main_tag_desc, r.tag.name));
            TooltipCompat.setTooltipText(v.tag, r.tag.name);
            v.tag.setOnClickListener(x -> {
                if (!tagOpen.remove(t.id)) tagOpen.add(t.id);
                notifyDataSetChanged();
            });
        }

        if (t.targetMinutes > 0 && !t.done) {
            v.focus.setVisibility(View.VISIBLE);
            v.focus.setGoal(t.attributedMinutes, t.targetMinutes);
            applyRunning(v.focus, t.id);
            v.focus.setOnClickListener(x -> host.onTaskFocus(t));
        } else {
            v.focus.setVisibility(View.GONE);
        }

        if (r.subtasks.isEmpty()) {
            v.subtasks.setVisibility(View.GONE);
            v.subtaskList.setVisibility(View.GONE);
        } else {
            v.subtasks.setVisibility(View.VISIBLE);
            v.subtasks.setCounts(r.subtasksDone, r.subtasks.size());
            TooltipCompat.setTooltipText(v.subtasks, v.subtasks.getContentDescription());
            v.subtasks.setOnClickListener(x -> {
                if (!subtasksOpen.remove(t.id)) subtasksOpen.add(t.id);
                notifyDataSetChanged();
            });
            bindSubtasks(v, r, subtasksOpen.contains(t.id));
        }
    }

    private void bindSubtasks(RowViews v, ProjectsScreenModel.Row r, boolean open) {
        if (!open) {
            v.subtaskList.setVisibility(View.GONE);
            return;
        }
        v.subtaskList.setVisibility(View.VISIBLE);
        LayoutInflater in = LayoutInflater.from(v.subtaskList.getContext());
        while (v.subtaskList.getChildCount() < r.subtasks.size()) {
            v.subtaskList.addView(in.inflate(R.layout.item_projects_subtask, v.subtaskList, false));
        }
        while (v.subtaskList.getChildCount() > r.subtasks.size()) {
            v.subtaskList.removeViewAt(v.subtaskList.getChildCount() - 1);
        }
        for (int i = 0; i < r.subtasks.size(); i++) {
            TaskSubtask sub = r.subtasks.get(i);
            View item = v.subtaskList.getChildAt(i);
            TaskCheckView check = item.findViewById(R.id.projectsSubtaskCheck);
            check.setOnCheckedChangeListener(null);
            check.setTaskTitle(sub.title);
            check.setChecked(sub.done);
            check.setOnCheckedChangeListener((view, checked) -> host.onSubtaskToggle(sub));
            TextView title = item.findViewById(R.id.projectsSubtaskTitle);
            title.setText(sub.title);
            int flags = title.getPaintFlags();
            title.setPaintFlags(sub.done ? flags | Paint.STRIKE_THRU_TEXT_FLAG
                    : flags & ~Paint.STRIKE_THRU_TEXT_FLAG);
            title.setTextColor(ContextCompat.getColor(item.getContext(),
                    sub.done ? R.color.bb_on_surface_muted : R.color.bb_on_surface));
        }
    }

    private static void bindWhen(RowViews v, TaskSnapshot t, Context c) {
        if (!t.hasDate()) {
            v.when.setVisibility(View.GONE);
            return;
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate due = Instant.ofEpochMilli(t.dueMillis).atZone(zone).toLocalDate();
        long offset = ChronoUnit.DAYS.between(today, due);
        String day;
        if (offset == 0) {
            day = c.getString(R.string.projects_main_when_today);
        } else if (offset == 1) {
            day = c.getString(R.string.projects_main_when_tomorrow);
        } else if (offset == -1) {
            day = c.getString(R.string.projects_main_when_yesterday);
        } else {
            Locale locale = Locale.getDefault();
            day = DateFormat.format(DateFormat.getBestDateTimePattern(locale, "EEEd"),
                    new Date(t.dueMillis)).toString();
        }
        String text = t.hasTime
                ? c.getString(R.string.projects_main_when_with_time, day,
                        DateFormat.getTimeFormat(c).format(new Date(t.dueMillis)))
                : day;
        v.when.setVisibility(View.VISIBLE);
        v.when.setText(text);
        int color;
        if (t.done) {
            color = R.color.bb_on_surface_muted;
        } else if (offset < 0) {
            color = R.color.bb_danger;
        } else if (offset == 0) {
            color = R.color.bb_primary;
        } else {
            color = R.color.bb_on_surface_muted;
        }
        v.when.setTextColor(ColorUtils.setAlphaComponent(ContextCompat.getColor(c, color), 255));
    }
}
