package com.example.bbettercalendar.ui.home;

import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.ui.projects.ProjectListAdapter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Lista de tareas (type task) de Home — spec tasks-home-today. Bindea CalendarEntry
 * directamente (a diferencia de DayDetailAdapter/CalendarItem, que no lleva isDone)
 * para que el toggle del checkbox sea un solo salto hasta el DAO.
 */
public class TodayTaskAdapter extends RecyclerView.Adapter<TodayTaskAdapter.TaskViewHolder> {

    public interface OnTaskCheckedListener {
        void onTaskChecked(CalendarEntry entry, boolean isDone);
    }

    /** Botón "quitar" de la sección de atrasadas (spec tasks-recurrence). */
    public interface OnTaskRemoveListener {
        void onTaskRemove(CalendarEntry entry);
    }

    /** Botón "focus this" (spec focus-attribution): arranca el timer de Home vinculado a la tarea. */
    public interface OnTaskFocusListener {
        void onTaskFocus(CalendarEntry entry);
    }

    private final List<CalendarEntry> tasks = new ArrayList<>();
    // La sección "older uncompleted" muestra la fecha de la tarea; la de hoy, su hora.
    private final boolean showDate;
    private final OnTaskCheckedListener listener;
    // Sólo la sección de atrasadas pasa un removeListener: habilita el botón "quitar" y el
    // contador de días fallados en las filas colapsadas.
    private final OnTaskRemoveListener removeListener;
    // Opcional (sólo la sección de hoy lo pasa): habilita el botón "focus this" en tareas con objetivo.
    private OnTaskFocusListener focusListener;

    public void setOnTaskFocusListener(OnTaskFocusListener focusListener) {
        this.focusListener = focusListener;
    }

    // u13: chip de proyecto = join en memoria projectId -> Project (nombre + colorIndex), sin
    // tocar el esquema. Lo alimenta HomeViewModel.getProjects() (LiveData de Room).
    private final SparseArray<Project> projectsById = new SparseArray<>();

    /** Proyectos para el chip de cada fila; null/vacío = sin chips. */
    public void setProjects(List<Project> projects) {
        projectsById.clear();
        if (projects != null) {
            for (Project project : projects) {
                projectsById.put(project.id, project);
            }
        }
        notifyDataSetChanged();
    }

    public TodayTaskAdapter(boolean showDate, OnTaskCheckedListener listener) {
        this(showDate, listener, null);
    }

    public TodayTaskAdapter(boolean showDate, OnTaskCheckedListener listener,
                            OnTaskRemoveListener removeListener) {
        this.showDate = showDate;
        this.listener = listener;
        this.removeListener = removeListener;
    }

    public void submitList(List<CalendarEntry> newTasks) {
        tasks.clear();
        if (newTasks != null) {
            tasks.addAll(newTasks);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_today_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        CalendarEntry entry = tasks.get(position);

        // Quitar el listener antes de setChecked: el holder reciclado aún tiene el
        // callback de la fila anterior y dispararía un update fantasma.
        holder.checkbox.setOnCheckedChangeListener(null);
        holder.checkbox.setChecked(entry.isDone());

        holder.title.setText(entry.getTitle());
        int flags = holder.title.getPaintFlags();
        if (entry.isDone()) {
            holder.title.setPaintFlags(flags | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.title.setTextColor(ContextCompat.getColor(
                    holder.itemView.getContext(), R.color.bb_on_surface_muted));
        } else {
            holder.title.setPaintFlags(flags & ~Paint.STRIKE_THRU_TEXT_FLAG);
            holder.title.setTextColor(ContextCompat.getColor(
                    holder.itemView.getContext(), R.color.bb_on_surface));
        }

        holder.time.setText(entry.isDone()
                ? holder.itemView.getContext().getString(R.string.home_task_done)
                : formatWhen(entry));
        // html 04: separadores sólo ENTRE filas; la fila hecha se atenúa entera (opacity-60).
        holder.divider.setVisibility(position == 0 ? View.GONE : View.VISIBLE);
        holder.row.setAlpha(entry.isDone() ? DONE_ALPHA : 1f);
        bindProjectChip(holder, entry);

        // Fila colapsada de una serie recurrente atrasada: muestra "missed N×" (spec tasks-recurrence).
        if (showDate && entry.getSeriesMissedCount() > 1) {
            holder.time.setText(holder.itemView.getContext().getString(
                    R.string.home_overdue_missed_count, entry.getSeriesMissedCount()));
        }

        if (removeListener != null) {
            holder.remove.setVisibility(View.VISIBLE);
            holder.remove.setOnClickListener(v -> removeListener.onTaskRemove(entry));
        } else {
            holder.remove.setVisibility(View.GONE);
            holder.remove.setOnClickListener(null);
        }

        // Progreso + "focus this" sólo en la sección de hoy (!showDate), para tareas con objetivo.
        // La sección de atrasadas no enriquece attributedMinutes, así que nunca los muestra.
        boolean hasTarget = !showDate && entry.getTargetMinutes() > 0;
        if (hasTarget) {
            holder.progress.setVisibility(View.VISIBLE);
            holder.progress.setText(holder.itemView.getContext().getString(
                    R.string.focus_progress_format,
                    entry.getAttributedMinutes(), entry.getTargetMinutes()));
        } else {
            holder.progress.setVisibility(View.GONE);
        }
        if (hasTarget && !entry.isDone() && focusListener != null) {
            holder.focus.setVisibility(View.VISIBLE);
            holder.focus.setOnClickListener(v -> focusListener.onTaskFocus(entry));
        } else {
            // En la sección de hoy la columna del botón se reserva (INVISIBLE) aunque esta fila no
            // lo lleve: así las horas quedan alineadas entre filas (spec frontend-structure-cleanup).
            holder.focus.setVisibility(showDate ? View.GONE : View.INVISIBLE);
            holder.focus.setOnClickListener(null);
        }

        holder.checkbox.setOnCheckedChangeListener((button, isChecked) -> {
            if (listener != null) {
                listener.onTaskChecked(entry, isChecked);
            }
        });
    }

    private static final float DONE_ALPHA = 0.6f;
    // Relleno del chip = color del proyecto al ~15 % (html bg-primary/15) sobre bb_surface_card.
    private static final int CHIP_FILL_ALPHA = 0x26;

    private void bindProjectChip(TaskViewHolder holder, CalendarEntry entry) {
        Project project = entry.getProjectId() > 0 && !entry.isDone()
                ? projectsById.get(entry.getProjectId()) : null;
        if (project == null || project.name == null || project.name.isEmpty()) {
            holder.project.setVisibility(View.GONE);
            return;
        }
        int color = ContextCompat.getColor(holder.itemView.getContext(),
                ProjectListAdapter.accentColorResFor(project.colorIndex));
        holder.project.setVisibility(View.VISIBLE);
        holder.project.setText(project.name);
        holder.project.setTextColor(color);
        ViewCompat.setBackgroundTintList(holder.project,
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, CHIP_FILL_ALPHA)));
    }

    private String formatWhen(CalendarEntry entry) {
        long startMillis = entry.getStartMillis();
        if (startMillis <= 0L) {
            return "";
        }
        if (showDate) {
            return new SimpleDateFormat("MMM d", Locale.getDefault())
                    .format(new Date(startMillis));
        }
        // Las tareas sin hora elegida se guardan a las 00:00 — no mostramos esa hora.
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(startMillis);
        if (cal.get(Calendar.HOUR_OF_DAY) == 0 && cal.get(Calendar.MINUTE) == 0) {
            return "";
        }
        return new SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(new Date(startMillis));
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        final CheckBox checkbox;
        final TextView title;
        final TextView time;
        final TextView remove;
        final TextView progress;
        final ImageView focus;
        final TextView project;
        final View divider;
        final View row;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            project = itemView.findViewById(R.id.taskProjectChip);
            divider = itemView.findViewById(R.id.taskDivider);
            row = itemView.findViewById(R.id.taskRow);
            checkbox = itemView.findViewById(R.id.taskDoneCheckbox);
            title = itemView.findViewById(R.id.taskTitleText);
            time = itemView.findViewById(R.id.taskTimeText);
            remove = itemView.findViewById(R.id.taskRemoveButton);
            progress = itemView.findViewById(R.id.taskProgressText);
            focus = itemView.findViewById(R.id.taskFocusButton);
        }
    }
}
