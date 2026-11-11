package com.example.bbettercalendar.ui.home;

import android.content.Context;
import android.graphics.Paint;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.feedback.HapticFeedback;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.ui.common.widget.RingButtonView;
import com.example.bbettercalendar.ui.common.widget.RingState;

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
 *
 * <p>mejora-visual u6: marcar una tarea cambia la fila AL INSTANTE (tachado, atenuado, «Hecha») y
 * avisa al fragment para la cuenta «2 de 5»; el guardado llega después (HomeViewModel) y, cuando Room
 * re-emite, DiffUtil mueve la fila a su sitio con animación. Hasta entonces un override local manda.
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

    private static final float DONE_ALPHA = 0.6f;
    private static final String META_SEPARATOR = " · ";

    private final List<CalendarEntry> tasks = new ArrayList<>();
    // La sección "older uncompleted" muestra la fecha de la tarea; la de hoy, su hora.
    private final boolean showDate;
    private final OnTaskCheckedListener listener;
    // Sólo la sección de atrasadas pasa un removeListener: habilita el botón "quitar" y el
    // contador de días fallados en las filas colapsadas.
    private final OnTaskRemoveListener removeListener;
    // Opcional (sólo la sección de hoy lo pasa): habilita el botón "focus this" en tareas con objetivo.
    private OnTaskFocusListener focusListener;
    // u6: hecho/no hecho que el usuario acaba de marcar y que la BD aún no ha devuelto (id -> done).
    private final SparseBooleanArray pendingDone = new SparseBooleanArray();

    public void setOnTaskFocusListener(OnTaskFocusListener focusListener) {
        this.focusListener = focusListener;
    }

    // u13: proyecto de cada fila = join en memoria projectId -> Project (nombre + colorIndex), sin
    // tocar el esquema. Lo alimenta HomeViewModel.getProjects() (LiveData de Room).
    private final SparseArray<Project> projectsById = new SparseArray<>();

    /** Proyectos para la línea secundaria de cada fila; null/vacío = sin proyecto. */
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

    /** Nueva lista de Room: los cambios se animan (DiffUtil); un override ya guardado se olvida. */
    public void submitList(@Nullable List<CalendarEntry> newTasks) {
        List<CalendarEntry> next = newTasks == null ? new ArrayList<>() : new ArrayList<>(newTasks);
        for (CalendarEntry entry : next) {
            int i = pendingDone.indexOfKey(entry.getId());
            if (i >= 0 && pendingDone.valueAt(i) == entry.isDone()) {
                pendingDone.removeAt(i);
            }
        }
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new TaskDiff(tasks, next));
        tasks.clear();
        tasks.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    /** Hecho tal como se ve ahora (el override del usuario manda sobre la fila de Room). */
    public boolean isDoneNow(@NonNull CalendarEntry entry) {
        return pendingDone.get(entry.getId(), entry.isDone());
    }

    /** Tareas hechas tal como se ven ahora, para «2 de 5 tareas». */
    public int countDoneNow() {
        int done = 0;
        for (CalendarEntry entry : tasks) {
            if (isDoneNow(entry)) {
                done++;
            }
        }
        return done;
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
        boolean done = isDoneNow(entry);

        // Quitar el listener antes de setChecked: el holder reciclado aún tiene el
        // callback de la fila anterior y dispararía un update fantasma.
        holder.checkbox.setOnCheckedChangeListener(null);
        holder.checkbox.setChecked(done);
        holder.checkbox.jumpDrawablesToCurrentState();

        holder.title.setText(entry.getTitle());
        bindDone(holder, entry, done);

        if (removeListener != null) {
            holder.remove.setVisibility(View.VISIBLE);
            holder.remove.setOnClickListener(v -> removeListener.onTaskRemove(entry));
        } else {
            holder.remove.setVisibility(View.GONE);
            holder.remove.setOnClickListener(null);
        }

        holder.checkbox.setOnCheckedChangeListener((button, isChecked) -> {
            if (button.isPressed()) {
                HapticFeedback.lightTap(button);
            }
            // Doherty: la fila cambia ya; el guardado lo hace el listener (HomeViewModel) después.
            pendingDone.put(entry.getId(), isChecked);
            bindDone(holder, entry, isChecked);
            if (listener != null) {
                listener.onTaskChecked(entry, isChecked);
            }
        });
    }

    /** Tachado, atenuado, línea secundaria y anillo según {@code done}. */
    private void bindDone(@NonNull TaskViewHolder holder, @NonNull CalendarEntry entry, boolean done) {
        Context ctx = holder.itemView.getContext();
        int flags = holder.title.getPaintFlags();
        if (done) {
            holder.title.setPaintFlags(flags | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.title.setTextColor(ContextCompat.getColor(ctx, R.color.bb_on_surface_muted));
        } else {
            holder.title.setPaintFlags(flags & ~Paint.STRIKE_THRU_TEXT_FLAG);
            holder.title.setTextColor(ContextCompat.getColor(ctx, R.color.bb_on_surface));
        }
        // html 04: la fila hecha se atenúa entera (opacity-60).
        holder.row.setAlpha(done ? DONE_ALPHA : 1f);

        CharSequence meta = done ? ctx.getString(R.string.home_task_done) : metaLine(ctx, entry);
        holder.meta.setText(meta);
        holder.meta.setVisibility(meta.length() == 0 ? View.GONE : View.VISIBLE);

        // "Focus this" sólo en la sección de hoy (!showDate), para tareas con objetivo sin hacer. La
        // sección de atrasadas no enriquece attributedMinutes, así que nunca lo muestra.
        boolean hasTarget = !showDate && entry.getTargetMinutes() > 0;
        if (hasTarget && !done && focusListener != null) {
            holder.focus.setVisibility(View.VISIBLE);
            holder.focus.setState(RingState.IDLE);
            holder.focus.setRemaining(entry.getAttributedMinutes() / (float) entry.getTargetMinutes());
            holder.focus.setOnClickListener(v -> focusListener.onTaskFocus(entry));
        } else {
            // En la sección de hoy la columna del botón se reserva (INVISIBLE) aunque esta fila no
            // lo lleve, para que los textos queden alineados entre filas.
            holder.focus.setVisibility(showDate ? View.GONE : View.INVISIBLE);
            holder.focus.setOnClickListener(null);
        }
    }

    /** «25 / 60 min» · hora (o fecha / «perdida N×» en atrasadas) · proyecto. */
    @NonNull
    private String metaLine(@NonNull Context ctx, @NonNull CalendarEntry entry) {
        StringBuilder sb = new StringBuilder();
        if (!showDate && entry.getTargetMinutes() > 0) {
            sb.append(ctx.getString(R.string.home_task_minutes_progress,
                    entry.getAttributedMinutes(), entry.getTargetMinutes()));
        }
        String when;
        if (showDate && entry.getSeriesMissedCount() > 1) {
            // Fila colapsada de una serie recurrente atrasada (spec tasks-recurrence).
            when = ctx.getString(R.string.home_overdue_missed_count, entry.getSeriesMissedCount());
        } else {
            when = formatWhen(entry);
        }
        appendPart(sb, when);
        // M/40: «18:00 · TFG» en el mismo tono apagado (un solo acento ámbar por pantalla: la racha).
        Project project = entry.getProjectId() > 0 ? projectsById.get(entry.getProjectId()) : null;
        if (project != null) {
            appendPart(sb, project.name);
        }
        return sb.toString();
    }

    /** Appends {@code part} after a separator. */
    private static void appendPart(@NonNull StringBuilder sb, @Nullable String part) {
        if (part == null || part.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(META_SEPARATOR);
        }
        sb.append(part);
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

    /** Misma fila = mismo id; mismo contenido = lo que pinta la fila. */
    private static final class TaskDiff extends DiffUtil.Callback {
        private final List<CalendarEntry> before;
        private final List<CalendarEntry> after;

        TaskDiff(List<CalendarEntry> before, List<CalendarEntry> after) {
            this.before = before;
            this.after = after;
        }

        @Override
        public int getOldListSize() {
            return before.size();
        }

        @Override
        public int getNewListSize() {
            return after.size();
        }

        @Override
        public boolean areItemsTheSame(int oldPos, int newPos) {
            return before.get(oldPos).getId() == after.get(newPos).getId();
        }

        @Override
        public boolean areContentsTheSame(int oldPos, int newPos) {
            CalendarEntry a = before.get(oldPos);
            CalendarEntry b = after.get(newPos);
            return a.isDone() == b.isDone()
                    && a.getStartMillis() == b.getStartMillis()
                    && a.getTargetMinutes() == b.getTargetMinutes()
                    && a.getAttributedMinutes() == b.getAttributedMinutes()
                    && a.getProjectId() == b.getProjectId()
                    && a.getSeriesMissedCount() == b.getSeriesMissedCount()
                    && equal(a.getTitle(), b.getTitle());
        }

        private static boolean equal(@Nullable String x, @Nullable String y) {
            return x == null ? y == null : x.equals(y);
        }
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        final CheckBox checkbox;
        final TextView title;
        final TextView meta;
        final TextView remove;
        final RingButtonView focus;
        final View row;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            row = itemView.findViewById(R.id.taskRow);
            checkbox = itemView.findViewById(R.id.taskDoneCheckbox);
            title = itemView.findViewById(R.id.taskTitleText);
            meta = itemView.findViewById(R.id.taskTimeText);
            remove = itemView.findViewById(R.id.taskRemoveButton);
            focus = itemView.findViewById(R.id.taskFocusButton);
        }
    }
}
