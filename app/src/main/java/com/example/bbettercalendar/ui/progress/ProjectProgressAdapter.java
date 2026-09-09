package com.example.bbettercalendar.ui.progress;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.projects.ProjectDeadlineState;
import com.example.bbettercalendar.ui.projects.ProjectListAdapter;

import java.util.ArrayList;
import java.util.List;

// Banda de proyectos de Progress (spec project-deadlines-progress): "¿a dónde fue el trabajo?".
// El acento sale de ProjectListAdapter.ACCENT_COLORS vía colorIndex, así que un proyecto se ve
// igual aquí que en la pestaña Projects.
public class ProjectProgressAdapter
        extends RecyclerView.Adapter<ProjectProgressAdapter.ProjectProgressVH> {

    public interface OnProjectRowClickListener {
        void onProjectRowClick(ProjectProgressRow row);
    }

    private final List<ProjectProgressRow> rows = new ArrayList<>();
    private final OnProjectRowClickListener listener;

    public ProjectProgressAdapter(OnProjectRowClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<ProjectProgressRow> newRows) {
        rows.clear();
        if (newRows != null) {
            rows.addAll(newRows);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ProjectProgressVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_project_progress, parent, false);
        return new ProjectProgressVH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProjectProgressVH holder, int position) {
        ProjectProgressRow row = rows.get(position);
        Context context = holder.itemView.getContext();

        holder.name.setText(row.name);
        holder.accent.setBackgroundColor(ContextCompat.getColor(context,
                ProjectListAdapter.accentColorResFor(row.colorIndex)));

        holder.progressBar.setProgress(row.percent());
        holder.progressText.setText(row.totalCount == 0
                ? context.getString(R.string.project_progress_empty)
                : context.getString(R.string.project_progress_format, row.doneCount, row.totalCount));

        holder.minutes.setText(context.getString(R.string.progress_project_minutes_format,
                row.minutesInRange));

        bindDeadlineChip(holder, row, context);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onProjectRowClick(row);
            }
        });
    }

    // Mismo chip ámbar/rojo que la lista de proyectos: un deadline se lee igual en las dos pantallas.
    private void bindDeadlineChip(ProjectProgressVH holder, ProjectProgressRow row, Context context) {
        if (row.deadlineState == null || row.deadlineState == ProjectDeadlineState.NONE) {
            holder.deadlineChip.setVisibility(View.GONE);
            return;
        }
        holder.deadlineChip.setVisibility(View.VISIBLE);
        int tint = row.deadlineState == ProjectDeadlineState.PASSED
                ? R.color.bb_danger : R.color.bb_accent_reward;
        holder.deadlineChip.setBackgroundTintList(ContextCompat.getColorStateList(context, tint));
        holder.deadlineChip.setText(row.deadlineState == ProjectDeadlineState.PASSED
                ? R.string.project_deadline_passed
                : R.string.project_deadline_approaching);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class ProjectProgressVH extends RecyclerView.ViewHolder {
        final View accent;
        final TextView name;
        final TextView deadlineChip;
        final TextView minutes;
        final ProgressBar progressBar;
        final TextView progressText;

        ProjectProgressVH(@NonNull View itemView) {
            super(itemView);
            accent = itemView.findViewById(R.id.projectProgressAccent);
            name = itemView.findViewById(R.id.projectProgressName);
            deadlineChip = itemView.findViewById(R.id.projectProgressDeadlineChip);
            minutes = itemView.findViewById(R.id.projectProgressMinutes);
            progressBar = itemView.findViewById(R.id.projectProgressBar);
            progressText = itemView.findViewById(R.id.projectProgressText);
        }
    }
}
