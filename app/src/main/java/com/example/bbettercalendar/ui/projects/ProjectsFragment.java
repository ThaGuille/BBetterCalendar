package com.example.bbettercalendar.ui.projects;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.databinding.FragmentProjectsBinding;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.ui.home.FocusTarget;
import com.example.bbettercalendar.ui.home.QuickAddTaskSheet;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ProjectsFragment extends Fragment {

    private FragmentProjectsBinding binding;
    private ProjectsViewModel projectsViewModel;
    private ProjectListAdapter adapter;
    private ProjectListAdapter completedAdapter;
    private boolean completedOpen;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        projectsViewModel = new ViewModelProvider(this).get(ProjectsViewModel.class);

        binding = FragmentProjectsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        adapter = newAdapter();
        completedAdapter = newAdapter();
        binding.projectsList.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.projectsList.setAdapter(adapter);
        binding.projectsCompletedList.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.projectsCompletedList.setAdapter(completedAdapter);
        binding.projectsCompletedHeader.setOnClickListener(v -> {
            completedOpen = !completedOpen;
            renderCompletedOpen();
        });

        binding.projectsAddButton.setOnClickListener(v ->
                new CreateProjectDialog().show(getChildFragmentManager(), "create_project_dialog"));

        projectsViewModel.getProjects().observe(getViewLifecycleOwner(), items -> {
            List<ProjectListItem> active = new ArrayList<>();
            List<ProjectListItem> completed = new ArrayList<>();
            for (ProjectListItem item : items) {
                (item.project.status == Project.STATUS_ACTIVE ? active : completed).add(item);
            }
            adapter.submitList(active);
            completedAdapter.submitList(completed);
            binding.projectsEmptyState.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            binding.projectsCompletedHeader.setVisibility(completed.isEmpty() ? View.GONE : View.VISIBLE);
            binding.projectsCompletedTitle.setText(
                    getString(R.string.projects_completed_title, completed.size()));
            renderCompletedOpen();
        });

        projectsViewModel.getSummary().observe(getViewLifecycleOwner(), this::bindSummary);

        return root;
    }

    private ProjectListAdapter newAdapter() {
        ProjectListAdapter result = new ProjectListAdapter(project -> {
            Bundle args = new Bundle();
            args.putInt("projectId", project.id);
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_navigation_projects_to_navigation_project_detail, args);
        });
        result.setOnSubtaskActionListener(new ProjectListAdapter.OnSubtaskActionListener() {
            @Override
            public void onSubtaskChecked(CalendarEntry entry, boolean done) {
                projectsViewModel.setItemDone(entry, done);
            }

            @Override
            public void onSubtaskFocus(CalendarEntry entry) {
                // Igual que el detalle: el timer vive solo en Home, que consume el arranque pendiente.
                FocusTarget.set(entry.getId(), entry.getTitle(), entry.getTargetMinutes());
                NavHostFragment.findNavController(ProjectsFragment.this).navigate(R.id.navigation_home);
            }

            @Override
            public void onAddSubtask(Project project) {
                QuickAddTaskSheet.forProject(project.id)
                        .show(getChildFragmentManager(), QuickAddTaskSheet.SHEET_TAG);
            }
        });
        return result;
    }

    private void renderCompletedOpen() {
        if (binding == null) {
            return;
        }
        boolean show = completedOpen && binding.projectsCompletedHeader.getVisibility() == View.VISIBLE;
        binding.projectsCompletedList.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.projectsCompletedChevron.setRotation(show ? 90f : 0f);
    }

    private void bindSummary(ProjectsViewModel.Summary summary) {
        if (summary == null || binding == null) {
            return;
        }
        String text = getString(R.string.projects_summary_active, summary.active)
                + " " + getString(R.string.projects_summary_separator)
                + " " + getResources().getQuantityString(R.plurals.projects_summary_due,
                        summary.dueThisWeek, summary.dueThisWeek);
        binding.projectsSummaryText.setText(text);
        binding.projectsStreakChip.setVisibility(summary.streakWeeks > 0 ? View.VISIBLE : View.GONE);
        binding.projectsStreakText.setText(getString(R.string.projects_streak_chip, summary.streakWeeks));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
