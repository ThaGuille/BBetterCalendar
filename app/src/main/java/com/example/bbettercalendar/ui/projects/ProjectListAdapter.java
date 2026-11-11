package com.example.bbettercalendar.ui.projects;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.feedback.HapticFeedback;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.ProjectDeadlineState;
import com.example.bbettercalendar.ui.common.widget.ExpandStripView;
import com.example.bbettercalendar.ui.common.widget.ProgressRingView;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// mejora-visual u11: tarjeta de dos zonas (mockup 30). La zona superior (projectCardTop) abre
// ProjectDetailFragment; la franja inferior (projectCardStrip, ExpandStripView) solo despliega las
// tareas pendientes. Se quita la pulsacion larga. Varias franjas pueden estar desplegadas a la vez.
public class ProjectListAdapter extends RecyclerView.Adapter<ProjectListAdapter.ProjectViewHolder> {

    public interface OnProjectClickListener {
        void onProjectClick(Project project);
    }

    /** Acciones dentro de la tarjeta: marcar una tarea, enfocarla y "Anadir tarea". */
    public interface OnSubtaskActionListener {
        void onSubtaskChecked(CalendarEntry entry, boolean done);

        /** "Enfocar" / play pequeno: enlaza el temporizador a la tarea (como el detalle). */
        void onSubtaskFocus(CalendarEntry entry);

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
        int accent = ContextCompat.getColor(context, accentColorResFor(project.colorIndex));

        holder.name.setText(project.name);
        holder.glow.setBackgroundTintList(ColorStateList.valueOf(accent));
        holder.ring.setRingColor(accent);
        holder.ring.setProgress(item.percent(), true);

        String tasks = context.getString(R.string.project_tasks_count, item.doneCount, item.totalCount);
        holder.tasks.setText(item.focusedMinutes > 0
                ? context.getString(R.string.project_tasks_and_time, tasks,
                        ProjectFormat.duration(context, item.focusedMinutes))
                : tasks);

        bindDeadlineChip(holder, item, context);

        holder.top.setContentDescription(context.getString(R.string.project_open_desc, project.name));
        holder.top.setOnClickListener(v -> open(project));
        bindNextTask(holder, item, context);
        bindStrip(holder, item, context);
    }

    private void open(Project project) {
        if (listener != null) {
            listener.onProjectClick(project);
        }
    }

    private void bindNextTask(ProjectViewHolder holder, ProjectListItem item, Context context) {
        CalendarEntry next = item.nextTask();
        if (next == null) {
            holder.nextRow.setVisibility(View.GONE);
            holder.focusButton.setOnClickListener(null);
            return;
        }
        holder.nextRow.setVisibility(View.VISIBLE);
        holder.nextTitle.setText(next.getTitle());
        holder.focusButton.setContentDescription(
                context.getString(R.string.project_focus_desc, next.getTitle()));
        holder.focusButton.setOnClickListener(v -> {
            if (subtaskListener != null) {
                subtaskListener.onSubtaskFocus(next);
            }
        });
    }

    private void bindStrip(ProjectViewHolder holder, ProjectListItem item, Context context) {
        Project project = item.project;
        ExpandStripView strip = holder.strip;
        strip.setBackgroundColor(ContextCompat.getColor(context, R.color.bb_surface_raised));
        strip.setOnExpandedChangeListener(null);
        strip.clearContent();

        List<CalendarEntry> pending = item.pendingSubtasks();
        strip.setTitle(pending.isEmpty()
                ? context.getString(R.string.project_pending_none)
                : context.getResources().getQuantityString(
                        R.plurals.project_pending_title, pending.size(), pending.size()));
        strip.setContentDescription(context.getString(R.string.project_strip_desc, project.name));

        LayoutInflater inflater = LayoutInflater.from(context);
        for (CalendarEntry entry : pending) {
            View row = inflater.inflate(R.layout.item_project_subtask, null, false);
            bindSubtask(row, entry, item, context);
            strip.addContentView(row);
        }
        TextView add = (TextView) inflater.inflate(R.layout.item_project_add_task, null, false);
        add.setOnClickListener(v -> {
            if (subtaskListener != null) {
                subtaskListener.onAddSubtask(project);
            }
        });
        strip.addContentView(add);

        strip.setExpanded(expandedIds.contains(project.id), false);
        strip.setOnExpandedChangeListener(expanded -> {
            if (expanded) {
                expandedIds.add(project.id);
            } else {
                expandedIds.remove(project.id);
            }
        });
    }

    private void bindSubtask(View row, CalendarEntry entry, ProjectListItem item, Context context) {
        CheckBox check = row.findViewById(R.id.projectSubtaskCheck);
        TextView title = row.findViewById(R.id.projectSubtaskTitle);
        TextView meta = row.findViewById(R.id.projectSubtaskMeta);
        View focus = row.findViewById(R.id.projectSubtaskFocus);
        boolean done = entry.isDone();

        check.setChecked(done);
        title.setText(entry.getTitle());
        int flags = title.getPaintFlags();
        title.setPaintFlags(done ? flags | Paint.STRIKE_THRU_TEXT_FLAG
                : flags & ~Paint.STRIKE_THRU_TEXT_FLAG);
        title.setTextColor(ContextCompat.getColor(context,
                done ? R.color.bb_on_surface_muted : R.color.bb_on_surface));

        int target = entry.getTargetMinutes();
        meta.setText(target > 0
                ? context.getString(R.string.project_subtask_progress,
                        Math.min(entry.getAttributedMinutes(), target), target)
                : context.getString(R.string.project_subtask_no_target));

        row.setContentDescription(context.getString(R.string.project_item_done_desc, entry.getTitle()));
        row.setOnClickListener(v -> {
            HapticFeedback.lightTap(check);
            if (subtaskListener != null) {
                subtaskListener.onSubtaskChecked(entry, !entry.isDone());
            }
        });
        focus.setContentDescription(context.getString(R.string.project_focus_desc, entry.getTitle()));
        focus.setOnClickListener(v -> {
            if (subtaskListener != null) {
                subtaskListener.onSubtaskFocus(entry);
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
        holder.deadlineChip.setText(
                ProjectFormat.cardDeadline(context, project.softDeadlineMillis, now, state));
        int color;
        int background = R.drawable.bg_project_due_chip;
        if (state == ProjectDeadlineState.PASSED) {
            color = R.color.bb_danger;
        } else if (state == ProjectDeadlineState.APPROACHING) {
            color = R.color.bb_accent_reward;
            background = R.drawable.bg_project_due_chip_reward;
        } else {
            color = R.color.bb_on_surface_muted;
        }
        holder.deadlineChip.setBackgroundResource(background);
        holder.deadlineChip.setTextColor(ContextCompat.getColor(context, color));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ProjectViewHolder extends RecyclerView.ViewHolder {
        final View top;
        final View glow;
        final ProgressRingView ring;
        final TextView name;
        final TextView deadlineChip;
        final TextView tasks;
        final View nextRow;
        final TextView nextTitle;
        final MaterialButton focusButton;
        final ExpandStripView strip;

        ProjectViewHolder(@NonNull View itemView) {
            super(itemView);
            top = itemView.findViewById(R.id.projectCardTop);
            glow = itemView.findViewById(R.id.projectGlow);
            ring = itemView.findViewById(R.id.projectRing);
            name = itemView.findViewById(R.id.projectNameText);
            deadlineChip = itemView.findViewById(R.id.projectDeadlineChip);
            tasks = itemView.findViewById(R.id.projectTasksText);
            nextRow = itemView.findViewById(R.id.projectNextRow);
            nextTitle = itemView.findViewById(R.id.projectNextTitle);
            focusButton = itemView.findViewById(R.id.projectFocusButton);
            strip = itemView.findViewById(R.id.projectCardStrip);
        }
    }
}
