package com.example.bbettercalendar.ui.projects.list;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.widget.ProjectAccents;
import com.example.bbettercalendar.ui.common.widget.ProjectBubbleView;
import com.example.bbettercalendar.ui.projects.ProjectsScreenModel;

import java.util.Collections;
import java.util.List;

/** The horizontal selector: one {@link ProjectBubbleView} per active project and a trailing "New" bubble. */
public class ProjectsSelectorAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Host {
        void onProjectSelected(int projectId);
        void onNewProject();
    }

    private static final int T_PROJECT = 0;
    private static final int T_NEW = 1;

    private final Host host;
    private List<ProjectsScreenModel.Bubble> bubbles = Collections.emptyList();

    public ProjectsSelectorAdapter(@NonNull Host host) {
        this.host = host;
    }

    public void submit(List<ProjectsScreenModel.Bubble> bubbles) {
        this.bubbles = bubbles;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return bubbles.size() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        return position == bubbles.size() ? T_NEW : T_PROJECT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater in = LayoutInflater.from(parent.getContext());
        int layout = viewType == T_NEW ? R.layout.item_projects_selector_new : R.layout.item_projects_selector_bubble;
        return new RecyclerView.ViewHolder(in.inflate(layout, parent, false)) { };
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        View v = holder.itemView;
        if (getItemViewType(position) == T_NEW) {
            ProjectBubbleView bubble = v.findViewById(R.id.projectsNewBubble);
            bubble.setNewProject(true);
            bubble.setOnClickListener(x -> host.onNewProject());
            v.setOnClickListener(x -> host.onNewProject());
            return;
        }
        ProjectsScreenModel.Bubble b = bubbles.get(position);
        ProjectBubbleView bubble = v.findViewById(R.id.projectsBubble);
        bubble.setAccentColor(ContextCompat.getColor(v.getContext(), ProjectAccents.colorResFor(b.colorIndex)));
        bubble.setIconRes(ProjectAccents.projectIconRes(b.iconKey));
        bubble.setProgress(b.progress);
        bubble.setBadge(b.badgeCount, b.badgeLate);
        bubble.setSelected(b.selected);
        bubble.setProjectName(b.name);
        ((TextView) v.findViewById(R.id.projectsBubbleName)).setText(b.name);
        View.OnClickListener select = x -> host.onProjectSelected(b.projectId);
        bubble.setOnClickListener(select);
        v.setOnClickListener(select);
    }
}
