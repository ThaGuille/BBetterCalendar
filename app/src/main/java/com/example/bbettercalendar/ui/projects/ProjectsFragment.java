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
import com.example.bbettercalendar.ui.home.QuickAddTaskSheet;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ProjectsFragment extends Fragment {

    private FragmentProjectsBinding binding;
    private ProjectsViewModel projectsViewModel;
    private ProjectListAdapter adapter;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        projectsViewModel = new ViewModelProvider(this).get(ProjectsViewModel.class);

        binding = FragmentProjectsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        adapter = new ProjectListAdapter(project -> {
            Bundle args = new Bundle();
            args.putInt("projectId", project.id);
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_navigation_projects_to_navigation_project_detail, args);
        });
        adapter.setOnSubtaskActionListener(new ProjectListAdapter.OnSubtaskActionListener() {
            @Override
            public void onSubtaskChecked(CalendarEntry entry, boolean done) {
                projectsViewModel.setItemDone(entry, done);
            }

            @Override
            public void onAddSubtask(Project project) {
                QuickAddTaskSheet.forProject(project.id)
                        .show(getChildFragmentManager(), QuickAddTaskSheet.SHEET_TAG);
            }
        });
        binding.projectsList.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.projectsList.setAdapter(adapter);

        binding.projectsAddButton.setOnClickListener(v ->
                new CreateProjectDialog().show(getChildFragmentManager(), "create_project_dialog"));

        projectsViewModel.getProjects().observe(getViewLifecycleOwner(), items -> {
            adapter.submitList(items);
            binding.projectsEmptyText.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        });

        projectsViewModel.getSummary().observe(getViewLifecycleOwner(), this::bindSummary);

        return root;
    }

    private void bindSummary(ProjectsViewModel.Summary summary) {
        if (summary == null || binding == null) {
            return;
        }
        String text = getString(R.string.projects_summary_active, summary.active)
                + " " + getString(R.string.projects_summary_separator)
                + " " + getString(R.string.projects_summary_due, summary.dueThisWeek);
        binding.projectsSummaryText.setText(text);
        binding.projectsStreakChip.setVisibility(summary.streakDays > 0 ? View.VISIBLE : View.GONE);
        binding.projectsStreakText.setText(getString(R.string.projects_streak_chip, summary.streakDays));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
