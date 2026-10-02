package com.example.bbettercalendar.ui.calendar;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.calendarEntries.AddEventActivity;
import com.example.bbettercalendar.databinding.FragmentCalendarWeekBinding;
import com.example.bbettercalendar.ui.calendar.binders.WeekViewItemAdapter;

import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class CalendarFragmentWeek extends Fragment implements View.OnClickListener {

    private FragmentCalendarWeekBinding binding;
    private CalendarViewModel viewModel;
    private WeekViewItemAdapter adapter;
    private final SimpleDateFormat monthYearFormat =
            new SimpleDateFormat("MMMM yyyy", Locale.getDefault());

    private final ActivityResultLauncher<Intent> addEventLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    // No-op: viewModel.getItems() is a Room LiveData query -- it invalidates
                    // and re-queries on its own as soon as AddEventActivity's insert commits
                    // (repository-layer-consolidation, T2; see data-model.md).
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        getActivity().setTheme(R.style.ThemeColorGreen);
        binding = FragmentCalendarWeekBinding.inflate(inflater, container, false);

        viewModel = new ViewModelProvider(requireActivity()).get(CalendarViewModel.class);

        // Selector Mes/Semana en el contenido (antes: icono de la toolbar).
        View root = binding.getRoot();
        root.findViewById(R.id.calendarModeWeek).setSelected(true);
        root.findViewById(R.id.calendarModeMonth).setOnClickListener(v -> switchToMonth());

        binding.calendarAddEventButton2.setOnClickListener(this);

        adapter = new WeekViewItemAdapter();
        adapter.setOnRangeChangedListener(this::updateMonthTitle);
        binding.weekView.setAdapter(adapter);
        binding.weekMonthTitleText.setText(monthYearFormat.format(new Date()));

        // Cover ~3 months around now so scrolling forward/back stays inside the queried window.
        ZonedDateTime now = ZonedDateTime.now();
        ZoneId zone = ZoneId.systemDefault();
        long start = now.minusMonths(1).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli();
        long end = now.plusMonths(2).toLocalDate().atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli();
        viewModel.setRange(start, end);

        viewModel.getItems().observe(getViewLifecycleOwner(), items -> {
            if (binding == null) return;
            adapter.submitList(items != null ? items : Collections.emptyList());
        });

        return binding.getRoot();
    }

    private void updateMonthTitle(Calendar firstVisibleDate, Calendar lastVisibleDate) {
        if (binding == null) return;
        int firstMonth = firstVisibleDate.get(Calendar.MONTH);
        int firstYear = firstVisibleDate.get(Calendar.YEAR);
        int lastMonth = lastVisibleDate.get(Calendar.MONTH);
        int lastYear = lastVisibleDate.get(Calendar.YEAR);

        String text;
        if (firstMonth == lastMonth && firstYear == lastYear) {
            text = monthYearFormat.format(firstVisibleDate.getTime());
        } else {
            SimpleDateFormat monthOnly = new SimpleDateFormat("MMM", Locale.getDefault());
            String first = monthOnly.format(firstVisibleDate.getTime());
            String last = monthOnly.format(lastVisibleDate.getTime());
            text = firstYear == lastYear
                    ? first + " – " + last + " " + lastYear
                    : first + " " + firstYear + " – " + last + " " + lastYear;
        }
        binding.weekMonthTitleText.setText(text);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.calendarAddEventButton2) {
            Intent intent = new Intent(getActivity(), AddEventActivity.class);
            intent.putExtra("entry", AddEventActivity.TYPE_TASK);
            addEventLauncher.launch(intent);
        }
    }

    private void switchToMonth() {
        NavController navController = Navigation.findNavController(getActivity(), R.id.nav_host_fragment_activity_main);
        // Normalmente se llega a semana DESDE mes: volver atrás en vez de apilar otro mes, o el
        // botón atrás alternaría mes/semana indefinidamente.
        if (!navController.popBackStack(R.id.navigation_calendar_month, false)) {
            navController.navigate(R.id.action_navigation_calendar_week_to_navigation_calendar_month);
        }
    }
}
