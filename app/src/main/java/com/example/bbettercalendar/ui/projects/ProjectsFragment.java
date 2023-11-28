package com.example.bbettercalendar.ui.projects;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.bbettercalendar.databinding.FragmentProjectsBinding;
import com.example.bbettercalendar.ui.progress.ProgressViewModel;

public class ProjectsFragment extends Fragment {

    private FragmentProjectsBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        ProgressViewModel progressViewModel =
                new ViewModelProvider(this).get(ProgressViewModel.class);

        binding = FragmentProjectsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textProjects;
        progressViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}