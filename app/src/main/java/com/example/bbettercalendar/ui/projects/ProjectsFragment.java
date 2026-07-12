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
import com.example.bbettercalendar.databinding.FragmentProjectsBinding;

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
        binding.projectsList.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.projectsList.setAdapter(adapter);

        binding.projectsAddButton.setOnClickListener(v ->
                new CreateProjectDialog().show(getChildFragmentManager(), "create_project_dialog"));

        projectsViewModel.getProjects().observe(getViewLifecycleOwner(), items -> {
            adapter.submitList(items);
            binding.projectsEmptyText.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        });

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Recuenta % al volver (p.ej. desde el detalle tras marcar un item) — ver el comentario
        // de ProjectsViewModel.refresh() sobre por qué el LiveData de Room no basta solo.
        projectsViewModel.refresh();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
