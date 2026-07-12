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
import com.example.bbettercalendar.popups.OnPopupListener;
import com.example.bbettercalendar.popups.PopupHelper;
import com.example.bbettercalendar.popups.RepetitionPopup;
import com.example.bbettercalendar.popups.RepetitionSpec;
import com.example.bbettercalendar.ui.projects.ProjectDetailViewModel;
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
public class QuickAddTaskSheet extends BottomSheetDialogFragment implements OnPopupListener<Object> {

    public static final String SHEET_TAG = "quick_add_task_sheet";

    private static final String ARG_PROJECT_ID = "project_id";

    private EditText titleInput;
    private TextView timeButton;
    private TextView repeatButton;
    private int selectedHour = -1;
    private int selectedMinute = -1;
    private final RepetitionPopup repetitionPopup = new RepetitionPopup();
    private RepetitionSpec repetitionSpec = RepetitionSpec.none();
    // 0 = alta normal desde Home; si no, el proyecto dueño del item (spec projects-mvp).
    private int projectId = 0;

    /**
     * Alta de un item dentro de un proyecto (spec projects-mvp): oculta "Repeat" (decisión #4 —
     * los items de proyecto no recurren) y, si no se elige hora, el item se guarda sin fecha (vive
     * sólo dentro del proyecto) en vez de "hoy a las 00:00" como en el alta normal de Home.
     */
    public static QuickAddTaskSheet forProject(int projectId) {
        QuickAddTaskSheet sheet = new QuickAddTaskSheet();
        Bundle args = new Bundle();
        args.putInt(ARG_PROJECT_ID, projectId);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_quick_add_task, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            projectId = args.getInt(ARG_PROJECT_ID, 0);
        }

        titleInput = view.findViewById(R.id.quickAddTitleInput);
        timeButton = view.findViewById(R.id.quickAddTimeButton);
        repeatButton = view.findViewById(R.id.quickAddRepeatButton);

        if (projectId != 0) {
            repeatButton.setVisibility(View.GONE);
        }

        timeButton.setOnClickListener(v -> showTimePicker());
        repeatButton.setOnClickListener(v -> showRepeatPicker());
        view.findViewById(R.id.quickAddSaveButton).setOnClickListener(v -> save());
        view.findViewById(R.id.quickAddMoreOptionsButton).setOnClickListener(v -> openMoreOptions());

        repetitionPopup.setOnPopupListener(this);

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

    private void showRepeatPicker() {
        repetitionPopup.setInitialSpec(repetitionSpec);
        repetitionPopup.show(getChildFragmentManager(), "quick_add_repeat_popup");
    }

    // Resultado del RepetitionPopup (spec tasks-recurrence).
    @Override
    public void OnClosePopup(int popupType, Object result) {
        if (popupType == PopupHelper.REPETITION_POPUP && result instanceof RepetitionSpec) {
            repetitionSpec = (RepetitionSpec) result;
            repeatButton.setText(repetitionSpec.repeats()
                    ? repetitionSpec.describe(requireContext())
                    : getString(R.string.quick_add_repeat_label));
        }
    }

    @Override
    public void OnClosePopup(int popupType) { }

    private void save() {
        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) {
            titleInput.setError(getString(R.string.quick_add_title_required));
            return;
        }

        if (projectId != 0) {
            saveProjectItem(title);
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
        viewModel.quickAddTask(title, start, repetitionSpec);
        dismiss();
    }

    // spec projects-mvp: sin hora elegida el item queda SIN fecha (vive sólo dentro del proyecto),
    // a diferencia del alta normal de Home que siempre lo fecha a hoy.
    private void saveProjectItem(String title) {
        Calendar start = null;
        if (selectedHour >= 0) {
            start = Calendar.getInstance();
            start.set(Calendar.HOUR_OF_DAY, selectedHour);
            start.set(Calendar.MINUTE, Math.max(selectedMinute, 0));
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
        }
        ProjectDetailViewModel viewModel =
                new ViewModelProvider(requireParentFragment()).get(ProjectDetailViewModel.class);
        viewModel.addItem(title, start);
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
