package com.example.bbettercalendar.ui.home;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.calendarEntries.AddEventActivity;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Calendar;
import java.util.Locale;

/**
 * Alta rápida de una tarea para hoy desde Home — spec tasks-home-today. Primer
 * BottomSheetDialogFragment de la app (decisión del proposal: quick-capture anclado
 * abajo, no un modal centrado tipo PopupHelper). Título + hora opcional; "More
 * options" salta a AddEventActivity precargada con tipo tarea + hoy + título.
 * La inserción vive en HomeViewModel (compartido con HomeFragment vía
 * requireParentFragment(), por eso hay que mostrarlo con getChildFragmentManager()).
 */
public class QuickAddTaskSheet extends BottomSheetDialogFragment {

    public static final String SHEET_TAG = "quick_add_task_sheet";

    private EditText titleInput;
    private TextView timeButton;
    private int selectedHour = -1;
    private int selectedMinute = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_quick_add_task, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        titleInput = view.findViewById(R.id.quickAddTitleInput);
        timeButton = view.findViewById(R.id.quickAddTimeButton);

        timeButton.setOnClickListener(v -> showTimePicker());
        view.findViewById(R.id.quickAddSaveButton).setOnClickListener(v -> save());
        view.findViewById(R.id.quickAddMoreOptionsButton).setOnClickListener(v -> openMoreOptions());

        // Sheet de captura rápida: teclado listo sin un tap extra.
        titleInput.requestFocus();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }

    private void showTimePicker() {
        Calendar now = Calendar.getInstance();
        int hour = selectedHour >= 0 ? selectedHour : now.get(Calendar.HOUR_OF_DAY);
        int minute = selectedMinute >= 0 ? selectedMinute : now.get(Calendar.MINUTE);
        new TimePickerDialog(requireContext(), (picker, pickedHour, pickedMinute) -> {
            selectedHour = pickedHour;
            selectedMinute = pickedMinute;
            timeButton.setText(String.format(Locale.getDefault(), "%02d:%02d",
                    pickedHour, pickedMinute));
        }, hour, minute, DateFormat.is24HourFormat(requireContext())).show();
    }

    private void save() {
        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) {
            titleInput.setError(getString(R.string.quick_add_title_required));
            return;
        }

        Calendar start = Calendar.getInstance();
        // Sin hora elegida la tarea queda a las 00:00 de hoy (la lista no muestra esa hora).
        start.set(Calendar.HOUR_OF_DAY, Math.max(selectedHour, 0));
        start.set(Calendar.MINUTE, Math.max(selectedMinute, 0));
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        HomeViewModel viewModel =
                new ViewModelProvider(requireParentFragment()).get(HomeViewModel.class);
        viewModel.quickAddTask(title, start);
        dismiss();
    }

    private void openMoreOptions() {
        Intent intent = new Intent(requireContext(), AddEventActivity.class);
        intent.putExtra("entry", AddEventActivity.TYPE_TASK);
        intent.putExtra(AddEventActivity.EXTRA_PRESELECTED_DATE_MILLIS,
                System.currentTimeMillis());
        String typedTitle = titleInput.getText().toString().trim();
        if (!typedTitle.isEmpty()) {
            intent.putExtra(AddEventActivity.EXTRA_PREFILL_TITLE, typedTitle);
        }
        startActivity(intent);
        dismiss();
    }
}
