package com.example.bbettercalendar.ui.projects;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.ProjectDeadlineState;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// u16: tarjetas desplegables (html 03). Tocar la cabecera despliega/pliega (varias a la vez);
// pulsacion larga o "Open project" abren ProjectDetailFragment.
public class ProjectListAdapter extends RecyclerView.Adapter<ProjectListAdapter.ProjectViewHolder> {

    public interface OnProjectClickListener {
        void onProjectClick(Project project);
    }

    /** Acciones dentro de la tarjeta desplegada: marcar una subtarea y "Add subtask". */
    public interface OnSubtaskActionListener {
        void onSubtaskChecked(CalendarEntry entry, boolean done);

        void onAddSubtask(Project project);
    }

    // Franjas de acento de la lista: cicla la paleta bb_* por colorIndex (spec projects-mvp).
    // Publico desde project-deadlines-progress: la banda de proyectos de Progress pinta el mismo
    // acento para el mismo proyecto, asi que la paleta y el ciclado viven en un unico sitio.
    // Desde u4 (migracion Stitch) cicla los tokens dedicados bb_project_1..4.
    public static final int[] ACCENT_COLORS = {
            R.color.bb_project_1, R.color.bb_project_2, R.color.bb_project_3, R.color.bb_project_4
    };

    /** Color de acento de un colorIndex, tolerando indices negativos (% en Java no lo es). */
    public static int accentColorResFor(int colorIndex) {
        int index = colorIndex % ACCENT_COLORS.length;
        if (index < 0) {
            index += ACCENT_COLORS.length;
        }
        return ACCENT_COLORS[index];
    }

    private final List<ProjectListItem> items = new ArrayList<>();
    private final Set<Integer> expandedIds = new HashSet<>();
    private final OnProjectClickListener listener;
    @Nullable private OnSubtaskActionListener subtaskListener;

    public ProjectListAdapter(OnProjectClickListener listener) {
        this.listener = listener;
    }

    public void setOnSubtaskActionListener(@Nullable OnSubtaskActionListener subtaskListener) {
        this.subtaskListener = subtaskListener;
    }

    public void submitList(List<ProjectListItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ProjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_project, parent, false);
        return new ProjectViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProjectViewHolder holder, int position) {
        ProjectListItem item = items.get(position);
        Project project = item.project;
        Context context = holder.itemView.getContext();
        boolean expanded = expandedIds.contains(project.id);
        int accent = ContextCompat.getColor(context, accentColorResFor(project.colorIndex));

        holder.name.setText(project.name);
        holder.colorAccent.setBackgroundTintList(ColorStateList.valueOf(accent));
        holder.dotRing.setBackgroundTintList(ColorStateList.valueOf(accent));

        holder.tasks.setText(context.getString(R.string.project_tasks_count,
                item.doneCount, item.totalCount));
        boolean notStarted = item.doneCount == 0;
        holder.percent.setText(notStarted
                ? context.getString(R.string.project_not_started)
                : context.getString(R.string.project_percent_completed, item.percent()));
        holder.percent.setTextColor(ContextCompat.getColor(context,
                item.doneCount > 0 && item.percent() >= 40
                        ? R.color.bb_primary_bright : R.color.bb_on_surface_muted));

        holder.progress.setIndicatorColor(accent);
        holder.progress.setProgressCompat(item.percent(), false);

        bindDeadlineChip(holder, item, context);
        bindExpansion(holder, item, context, expanded);

        holder.header.setOnClickListener(v -> toggle(holder));
        holder.header.setOnLongClickListener(v -> {
            open(project);
            return true;
        });
        holder.openDetail.setOnClickListener(v -> open(project));
        holder.addSubtask.setOnClickListener(v -> {
            if (subtaskListener != null) {
                subtaskListener.onAddSubtask(project);
            }
        });
    }

    private void toggle(ProjectViewHolder holder) {
        int pos = holder.getBindingAdapterPosition();
        if (pos == RecyclerView.NO_POSITION) {
            return;
        }
        int id = items.get(pos).project.id;
        if (!expandedIds.remove(id)) {
            expandedIds.add(id);
        }
        notifyItemChanged(pos);
    }

    private void open(Project project) {
        if (listener != null) {
            listener.onProjectClick(project);
        }
    }

    private void bindExpansion(ProjectViewHolder holder, ProjectListItem item, Context context,
                               boolean expanded) {
        Project project = item.project;
        holder.card.setBackgroundResource(expanded
                ? R.drawable.bg_project_card_expanded : R.drawable.bg_project_card);
        holder.expandIcon.setRotation(expanded ? 180f : 0f);
        holder.header.setContentDescription(context.getString(expanded
                ? R.string.project_collapse_desc : R.string.project_expand_desc, project.name));
        holder.pomodoroRow.setVisibility(expanded ? View.VISIBLE : View.GONE);
        holder.expandedBody.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (!expanded) {
            holder.subtasksContainer.removeAllViews();
            return;
        }

        int today = item.pomodorosToday();
        holder.pomodorosToday.setText(context.getResources().getQuantityString(
                R.plurals.project_pomodoros_today, today, today));
        int remaining = item.estimatedRemainingMinutes();
        if (remaining > 0) {
            holder.estRemaining.setVisibility(View.VISIBLE);
            holder.estRemaining.setText(context.getString(R.string.project_est_remaining,
                    formatDuration(context, remaining)));
        } else {
            holder.estRemaining.setVisibility(View.GONE);
        }

        holder.subtasksContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);
        for (CalendarEntry entry : item.subtasks) {
            View row = inflater.inflate(R.layout.item_project_subtask,
                    holder.subtasksContainer, false);
            bindSubtask(row, entry, item, context);
            holder.subtasksContainer.addView(row);
        }
    }

    private void bindSubtask(View row, CalendarEntry entry, ProjectListItem item, Context context) {
        ImageView check = row.findViewById(R.id.projectSubtaskCheck);
        TextView title = row.findViewById(R.id.projectSubtaskTitle);
        TextView meta = row.findViewById(R.id.projectSubtaskMeta);
        boolean done = entry.isDone();

        check.setImageResource(done ? R.drawable.bg_task_check_on : R.drawable.bg_task_check_off);
        title.setText(entry.getTitle());
        int flags = title.getPaintFlags();
        title.setPaintFlags(done ? flags | Paint.STRIKE_THRU_TEXT_FLAG
                : flags & ~Paint.STRIKE_THRU_TEXT_FLAG);
        title.setTextColor(ContextCompat.getColor(context,
                done ? R.color.bb_on_surface_muted : R.color.bb_on_surface));

        int target = entry.getTargetMinutes();
        if (target <= 0) {
            meta.setVisibility(View.GONE);
        } else {
            meta.setVisibility(View.VISIBLE);
            if (done) {
                meta.setText(context.getString(R.string.project_subtask_minutes, target));
            } else {
                int pomodoros = ProjectListItem.pomodorosFor(target, item.pomodoroMinutes);
                meta.setText(context.getResources().getQuantityString(
                        R.plurals.project_subtask_pomodoros, pomodoros, pomodoros));
            }
        }

        row.setContentDescription(context.getString(R.string.project_item_done_desc, entry.getTitle()));
        row.setOnClickListener(v -> {
            if (subtaskListener != null) {
                subtaskListener.onSubtaskChecked(entry, !entry.isDone());
            }
        });
    }

    private void bindDeadlineChip(ProjectViewHolder holder, ProjectListItem item, Context context) {
        Project project = item.project;
        boolean finished = ProjectDeadlineState.isFinished(project.status, item.doneCount, item.totalCount);
        if (project.softDeadlineMillis <= 0L || finished) {
            holder.deadlineChip.setVisibility(View.GONE);
            return;
        }
        long now = System.currentTimeMillis();
        ProjectDeadlineState state = ProjectDeadlineState.from(project.softDeadlineMillis, now, false);
        holder.deadlineChip.setVisibility(View.VISIBLE);
        holder.deadlineChip.setText(deadlineLabel(context, project.softDeadlineMillis, now, state));
        int color;
        if (state == ProjectDeadlineState.PASSED) {
            color = R.color.bb_danger;
        } else if (state == ProjectDeadlineState.APPROACHING) {
            color = R.color.bb_accent_reward;
        } else {
            color = R.color.bb_on_surface_muted;
        }
        holder.deadlineChip.setTextColor(ContextCompat.getColor(context, color));
    }

    // "Due today" / "Due tomorrow" / "Due Fri" (menos de una semana) / "Due Oct 28" / "Overdue".
    private static String deadlineLabel(Context context, long deadlineMillis, long nowMillis,
                                        ProjectDeadlineState state) {
        if (state == ProjectDeadlineState.PASSED) {
            return context.getString(R.string.project_deadline_passed);
        }
        int days = daysBetween(nowMillis, deadlineMillis);
        if (days <= 0) {
            return context.getString(R.string.project_due_today);
        }
        if (days == 1) {
            return context.getString(R.string.project_due_tomorrow);
        }
        String skeleton = days < 7 ? "EEE" : "dMMM";
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton);
        String date = new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(deadlineMillis));
        return context.getString(R.string.project_due_on, date);
    }

    private static int daysBetween(long fromMillis, long toMillis) {
        Calendar a = Calendar.getInstance();
        a.setTimeInMillis(fromMillis);
        a.set(Calendar.HOUR_OF_DAY, 0);
        a.set(Calendar.MINUTE, 0);
        a.set(Calendar.SECOND, 0);
        a.set(Calendar.MILLISECOND, 0);
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(toMillis);
        b.set(Calendar.HOUR_OF_DAY, 0);
        b.set(Calendar.MINUTE, 0);
        b.set(Calendar.SECOND, 0);
        b.set(Calendar.MILLISECOND, 0);
        return (int) Math.round((b.getTimeInMillis() - a.getTimeInMillis()) / 86_400_000d);
    }

    private static String formatDuration(Context context, int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        if (h == 0) {
            return context.getString(R.string.project_duration_m, m);
        }
        if (m == 0) {
            return context.getString(R.string.project_duration_h, h);
        }
        return context.getString(R.string.project_duration_hm, h, m);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ProjectViewHolder extends RecyclerView.ViewHolder {
        final View card;
        final View header;
        final View colorAccent;
        final View dotRing;
        final TextView name;
        final TextView deadlineChip;
        final TextView tasks;
        final TextView percent;
        final ImageView expandIcon;
        final LinearProgressIndicator progress;
        final View pomodoroRow;
        final TextView pomodorosToday;
        final TextView estRemaining;
        final View expandedBody;
        final LinearLayout subtasksContainer;
        final View addSubtask;
        final View openDetail;

        ProjectViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.projectCard);
            header = itemView.findViewById(R.id.projectHeader);
            colorAccent = itemView.findViewById(R.id.projectColorAccent);
            dotRing = itemView.findViewById(R.id.projectDotRing);
            name = itemView.findViewById(R.id.projectNameText);
            deadlineChip = itemView.findViewById(R.id.projectDeadlineChip);
            tasks = itemView.findViewById(R.id.projectTasksText);
            percent = itemView.findViewById(R.id.projectPercentText);
            expandIcon = itemView.findViewById(R.id.projectExpandIcon);
            progress = itemView.findViewById(R.id.projectCardProgress);
            pomodoroRow = itemView.findViewById(R.id.projectPomodoroRow);
            pomodorosToday = itemView.findViewById(R.id.projectPomodorosToday);
            estRemaining = itemView.findViewById(R.id.projectEstRemaining);
            expandedBody = itemView.findViewById(R.id.projectExpandedBody);
            subtasksContainer = itemView.findViewById(R.id.projectSubtasksContainer);
            addSubtask = itemView.findViewById(R.id.projectAddSubtask);
            openDetail = itemView.findViewById(R.id.projectOpenDetail);
        }
    }
}
