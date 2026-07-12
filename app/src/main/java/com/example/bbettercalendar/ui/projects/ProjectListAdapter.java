package com.example.bbettercalendar.ui.projects;

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
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.ProjectDeadlineState;

import java.util.ArrayList;
import java.util.List;

public class ProjectListAdapter extends RecyclerView.Adapter<ProjectListAdapter.ProjectViewHolder> {

    public interface OnProjectClickListener {
        void onProjectClick(Project project);
    }

    // Franjas de acento de la lista: cicla la paleta bb_* por colorIndex (spec projects-mvp).
    private static final int[] ACCENT_COLORS = {
            R.color.bb_primary, R.color.bb_secondary, R.color.bb_accent_energy, R.color.bb_accent_reward
    };

    private final List<ProjectListItem> items = new ArrayList<>();
    private final OnProjectClickListener listener;

    public ProjectListAdapter(OnProjectClickListener listener) {
        this.listener = listener;
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

        holder.name.setText(project.name);

        int accentIndex = project.colorIndex % ACCENT_COLORS.length;
        if (accentIndex < 0) {
            accentIndex += ACCENT_COLORS.length;
        }
        int accent = ACCENT_COLORS[accentIndex];
        holder.colorAccent.setBackgroundColor(ContextCompat.getColor(context, accent));

        holder.progressBar.setProgress(item.percent());
        holder.progressText.setText(item.totalCount == 0
                ? context.getString(R.string.project_progress_empty)
                : context.getString(R.string.project_progress_format, item.doneCount, item.totalCount));

        bindDeadlineChip(holder, project, context);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onProjectClick(project);
            }
        });
    }

    private void bindDeadlineChip(ProjectViewHolder holder, Project project, Context context) {
        ProjectDeadlineState state = ProjectDeadlineState.from(project.softDeadlineMillis, System.currentTimeMillis());
        if (state == ProjectDeadlineState.NONE) {
            holder.deadlineChip.setVisibility(View.GONE);
            return;
        }
        holder.deadlineChip.setVisibility(View.VISIBLE);
        int tint = state == ProjectDeadlineState.PASSED ? R.color.bb_danger : R.color.bb_accent_reward;
        holder.deadlineChip.setBackgroundTintList(ContextCompat.getColorStateList(context, tint));
        holder.deadlineChip.setText(state == ProjectDeadlineState.PASSED
                ? R.string.project_deadline_passed
                : R.string.project_deadline_approaching);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ProjectViewHolder extends RecyclerView.ViewHolder {
        final View colorAccent;
        final TextView name;
        final TextView deadlineChip;
        final ProgressBar progressBar;
        final TextView progressText;

        ProjectViewHolder(@NonNull View itemView) {
            super(itemView);
            colorAccent = itemView.findViewById(R.id.projectColorAccent);
            name = itemView.findViewById(R.id.projectNameText);
            deadlineChip = itemView.findViewById(R.id.projectDeadlineChip);
            progressBar = itemView.findViewById(R.id.projectProgressBar);
            progressText = itemView.findViewById(R.id.projectProgressText);
        }
    }
}
