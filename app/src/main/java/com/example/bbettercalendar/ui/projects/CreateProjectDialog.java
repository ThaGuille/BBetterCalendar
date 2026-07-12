package com.example.bbettercalendar.ui.projects;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.bbettercalendar.R;

import java.util.Calendar;

// Alta de proyecto (spec projects-mvp) — mismo patrón que AppLimitDialog: AlertDialog.Builder +
// diálogo hijo que comparte el ViewModel del fragment padre (ProjectsFragment).
public class CreateProjectDialog extends DialogFragment {

    private long selectedDeadlineMillis = 0L;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity(), R.style.RoundedDialog);
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_create_project, null);
        builder.setView(view);

        EditText nameInput = view.findViewById(R.id.createProjectNameInput);
        EditText notesInput = view.findViewById(R.id.createProjectNotesInput);
        TextView deadlineButton = view.findViewById(R.id.createProjectDeadlineButton);

        deadlineButton.setOnClickListener(v -> showDeadlinePicker(deadlineButton));

        ProjectsViewModel viewModel =
                new ViewModelProvider(requireParentFragment()).get(ProjectsViewModel.class);
        Dialog dialog = builder.create();

        view.findViewById(R.id.createProjectCancelButton).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.createProjectSaveButton).setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                nameInput.setError(getString(R.string.create_project_name_required));
                return;
            }
            String notes = notesInput.getText().toString().trim();
            viewModel.createProject(name, notes, selectedDeadlineMillis);
            dismiss();
        });

        return dialog;
    }

    private void showDeadlinePicker(TextView deadlineButton) {
        Calendar now = Calendar.getInstance();
        new DatePickerDialog(requireContext(), (picker, year, month, dayOfMonth) -> {
            Calendar deadline = Calendar.getInstance();
            deadline.set(year, month, dayOfMonth, 23, 59, 59);
            selectedDeadlineMillis = deadline.getTimeInMillis();
            deadlineButton.setText(DateFormat.getMediumDateFormat(requireContext())
                    .format(deadline.getTime()));
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
    }
}
