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
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.calendarEntries.AddEventActivity;
import com.example.bbettercalendar.configuration.Configuration;
import com.example.bbettercalendar.databinding.FragmentCalendarMonthBinding;
import com.example.bbettercalendar.helpers.OnToolBarListener;
import com.example.bbettercalendar.helpers.OnToolbarCalendarListener;
import com.example.bbettercalendar.helpers.ToolbarHelper;
import com.example.bbettercalendar.ui.calendar.binders.MonthDayBinder;
import com.example.bbettercalendar.ui.calendar.domain.CalendarItem;
import com.kizitonwose.calendar.view.CalendarView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

public class CalendarFragmentMonth extends Fragment
        implements OnToolBarListener, OnToolbarCalendarListener, View.OnClickListener {

    @Inject
    Configuration config;

    private FragmentCalendarMonthBinding binding;
    private CalendarViewModel viewModel;
    private ToolbarHelper toolbarHelper;
    private MonthDayBinder dayBinder;

    private final ActivityResultLauncher<Intent> addEventLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    // LiveData from Room auto-refreshes the UI; no manual reload needed.
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        getActivity().setTheme(R.style.ThemeChatGPTBlue);
        binding = FragmentCalendarMonthBinding.inflate(inflater, container, false);

        viewModel = new ViewModelProvider(requireActivity()).get(CalendarViewModel.class);

        toolbarHelper = new ToolbarHelper(getContext(), getActivity(), getActivity().getMenuInflater(), R.menu.toolbar, true);
        toolbarHelper.setOnToolbarListener(this);
        toolbarHelper.setOnToolbarCalendarListener(this);
        getActivity().addMenuProvider(toolbarHelper, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        binding.calendarAddEventButton.setOnClickListener(this);

        setupCalendar();
        observeItems();

        return binding.getRoot();
    }

    private void setupCalendar() {
        CalendarView calendarView = binding.monthCalendarView;
        dayBinder = new MonthDayBinder();
        calendarView.setDayBinder(dayBinder);

        YearMonth currentMonth = YearMonth.now();
        YearMonth startMonth = currentMonth.minusMonths(12);
        YearMonth endMonth = currentMonth.plusMonths(12);
        DayOfWeek firstDayOfWeek = java.time.temporal.WeekFields.of(java.util.Locale.getDefault()).getFirstDayOfWeek();
        calendarView.setup(startMonth, endMonth, firstDayOfWeek);
        calendarView.scrollToMonth(currentMonth);

        calendarView.setMonthScrollListener(month -> {
            updateRangeForMonth(month.getYearMonth());
            return null;
        });
        updateRangeForMonth(currentMonth);
    }

    private void updateRangeForMonth(YearMonth month) {
        long start = month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = month.atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        viewModel.setRange(start, end);
    }

    private void observeItems() {
        viewModel.getItems().observe(getViewLifecycleOwner(), items -> {
            if (binding == null) return;
            dayBinder.setItemsByDate(groupByDate(items));
            binding.monthCalendarView.notifyCalendarChanged();
        });
    }

    private Map<LocalDate, List<CalendarItem>> groupByDate(List<CalendarItem> items) {
        Map<LocalDate, List<CalendarItem>> map = new HashMap<>();
        if (items == null) return map;
        ZoneId zone = ZoneId.systemDefault();
        for (CalendarItem item : items) {
            LocalDate startDate = java.time.Instant.ofEpochMilli(item.getStartMillis()).atZone(zone).toLocalDate();
            LocalDate endDate = java.time.Instant.ofEpochMilli(item.getEndMillis()).atZone(zone).toLocalDate();
            LocalDate cursor = startDate;
            while (!cursor.isAfter(endDate)) {
                map.computeIfAbsent(cursor, k -> new ArrayList<>()).add(item);
                cursor = cursor.plusDays(1);
            }
        }
        return map;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.calendarAddEventButton) {
            Intent intent = new Intent(getActivity(), AddEventActivity.class);
            intent.putExtra("entry", AddEventActivity.TYPE_TASK);
            addEventLauncher.launch(intent);
        }
    }

    @Override
    public void onToolbarLoaded(int result) { }

    @Override
    public void switchFragment() {
        NavController navController = Navigation.findNavController(getActivity(), R.id.nav_host_fragment_activity_main);
        navController.navigate(R.id.action_navigation_calendar_month_to_navigation_calendar_week);
    }
}
