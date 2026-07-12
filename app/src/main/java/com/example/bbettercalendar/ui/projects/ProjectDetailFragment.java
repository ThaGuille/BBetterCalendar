package com.example.bbettercalendar.ui.projects;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.databinding.FragmentProjectDetailBinding;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.ProjectDeadlineState;
import com.example.bbettercalendar.ui.home.FocusTarget;
import com.example.bbettercalendar.ui.home.QuickAddTaskSheet;

import java.util.Calendar;
import java.util.Date;

public class ProjectDetailFragment extends Fragment {

    private static final String ARG_PROJECT_ID = "projectId";

    private FragmentProjectDetailBinding binding;
    private ProjectDetailViewModel viewModel;
    private ProjectItemAdapter itemAdapter;
    private boolean headerLoaded = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(ProjectDetailViewModel.class);
        binding = FragmentProjectDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        int projectId = requireArguments().getInt(ARG_PROJECT_ID, 0);
        viewModel.setProjectId(projectId);

        itemAdapter = new ProjectItemAdapter((entry, done) -> viewModel.setItemDone(entry, done));
        // "Focus this" (spec focus-attribution): el timer vive sólo en Home, así que vinculamos el
        // item y navegamos a Home, que consume el arranque pendiente (FocusTarget.pendingAutoStart).
        itemAdapter.setOnItemFocusListener(entry -> {
            FocusTarget.set(entry.getId(), entry.getTitle());
            NavHostFragment.findNavController(this).navigate(R.id.navigation_home);
        });
        binding.projectDetailItemsList.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.projectDetailItemsList.setAdapter(itemAdapter);

        binding.projectDetailDeadlineButton.setOnClickListener(v -> showDeadlinePicker());
        binding.projectDetailSaveHeaderButton.setOnClickListener(v -> saveHeader());
        binding.projectDetailAddItemButton.setOnClickListener(v ->
                QuickAddTaskSheet.forProject(projectId).show(getChildFragmentManager(), QuickAddTaskSheet.SHEET_TAG));
        binding.projectDetailCompleteButton.setOnClickListener(v -> viewModel.completeProject());
        binding.projectDetailDeleteButton.setOnClickListener(v -> confirmDelete());

        viewModel.getProject().observe(getViewLifecycleOwner(), this::bindProject);
        viewModel.getItems().observe(getViewLifecycleOwner(), items -> {
            itemAdapter.submitList(items);
            binding.projectDetailItemsEmptyText.setVisibility(
                    items == null || items.isEmpty() ? View.VISIBLE : View.GONE);
        });
        viewModel.getProjectDeleted().observe(getViewLifecycleOwner(), deleted -> {
            if (Boolean.TRUE.equals(deleted)) {
                NavHostFragment.findNavController(this).navigateUp();
            }
        });
    }

    private void bindProject(Project project) {
        if (project == null) {
            return;
        }
        if (!headerLoaded) {
            binding.projectDetailNameInput.setText(project.name);
            binding.projectDetailNotesInput.setText(project.notes);
            headerLoaded = true;
        }
        if (getActivity() instanceof AppCompatActivity && ((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(project.name);
        }

        bindDeadlineViews(project.softDeadlineMillis);

        boolean completed = project.status == Project.STATUS_COMPLETED;
        binding.projectDetailCompleteButton.setVisibility(completed ? View.GONE : View.VISIBLE);
    }

    private void bindDeadlineViews(long softDeadlineMillis) {
        TextView chip = binding.projectDetailDeadlineChip;
        if (softDeadlineMillis <= 0L) {
            chip.setVisibility(View.GONE);
            binding.projectDetailDeadlineButton.setText(R.string.create_project_set_deadline);
            return;
        }
        binding.projectDetailDeadlineButton.setText(
                DateFormat.getMediumDateFormat(requireContext()).format(new Date(softDeadlineMillis)));

        ProjectDeadlineState state = ProjectDeadlineState.from(softDeadlineMillis, System.currentTimeMillis());
        if (state == ProjectDeadlineState.NONE) {
            chip.setVisibility(View.GONE);
            return;
        }
        chip.setVisibility(View.VISIBLE);
        int tint = state == ProjectDeadlineState.PASSED ? R.color.bb_danger : R.color.bb_accent_reward;
        chip.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), tint));
        chip.setText(state == ProjectDeadlineState.PASSED
                ? R.string.project_deadline_passed
                : R.string.project_deadline_approaching);
    }

    private void showDeadlinePicker() {
        Calendar now = Calendar.getInstance();
        new DatePickerDialog(requireContext(), (picker, year, month, dayOfMonth) -> {
            Calendar deadline = Calendar.getInstance();
            deadline.set(year, month, dayOfMonth, 23, 59, 59);
            viewModel.updateDeadline(deadline.getTimeInMillis());
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void saveHeader() {
        String name = binding.projectDetailNameInput.getText().toString().trim();
        if (name.isEmpty()) {
            binding.projectDetailNameInput.setError(getString(R.string.create_project_name_required));
            return;
        }
        String notes = binding.projectDetailNotesInput.getText().toString().trim();
        viewModel.updateHeader(name, notes);
    }

    private void confirmDelete() {
        new AlertDialog.Builder(requireContext(), R.style.RoundedDialog)
                .setTitle(R.string.project_detail_delete_confirm_title)
                .setMessage(R.string.project_detail_delete_confirm_message)
                .setPositiveButton(R.string.project_detail_delete, (dialog, which) -> viewModel.deleteProject())
                .setNegativeButton(R.string.create_project_cancel, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
