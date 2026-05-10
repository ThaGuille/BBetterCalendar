package com.example.bbettercalendar.ui.calendar;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.calendarEntries.AddEventActivity;
import com.example.bbettercalendar.configuration.Configuration;
import com.example.bbettercalendar.databinding.FragmentCalendarMonthBinding;
import com.example.bbettercalendar.helpers.OnToolBarListener;
import com.example.bbettercalendar.helpers.OnToolbarCalendarListener;
import com.example.bbettercalendar.helpers.ToolbarHelper;
import com.example.bbettercalendar.ui.calendar.binders.DayDetailAdapter;
import com.example.bbettercalendar.ui.calendar.binders.MonthDayBinder;
import com.example.bbettercalendar.ui.calendar.domain.CalendarItem;
import com.kizitonwose.calendar.core.CalendarMonth;
import com.kizitonwose.calendar.view.CalendarView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private DayDetailAdapter dayDetailAdapter;

    private LocalDate selectedDate = LocalDate.now();
    private Map<LocalDate, List<CalendarItem>> currentItemsByDate = Collections.emptyMap();
    private final DateTimeFormatter monthTitleFormatter =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault());
    private final DateTimeFormatter dayDetailFormatter =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault());

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

        populateWeekdayLegend();
        setupCalendar();
        setupDayDetail();
        observeItems();

        return binding.getRoot();
    }

    private void populateWeekdayLegend() {
        DayOfWeek firstDow = WeekFields.of(Locale.getDefault()).getFirstDayOfWeek();
        View root = binding.getRoot();
        int[] ids = {
                R.id.legendDay0, R.id.legendDay1, R.id.legendDay2, R.id.legendDay3,
                R.id.legendDay4, R.id.legendDay5, R.id.legendDay6
        };
        for (int i = 0; i < 7; i++) {
            TextView tv = root.findViewById(ids[i]);
            tv.setText(firstDow.plus(i).getDisplayName(TextStyle.SHORT, Locale.getDefault()));
        }
    }

    private void setupCalendar() {
        CalendarView calendarView = binding.monthCalendarView;
        dayBinder = new MonthDayBinder();
        dayBinder.setSelectedDate(selectedDate);
        dayBinder.setOnDayClickListener(this::onDaySelected);
        calendarView.setDayBinder(dayBinder);

        YearMonth currentMonth = YearMonth.now();
        YearMonth startMonth = currentMonth.minusMonths(12);
        YearMonth endMonth = currentMonth.plusMonths(12);
        DayOfWeek firstDayOfWeek = WeekFields.of(Locale.getDefault()).getFirstDayOfWeek();
        calendarView.setup(startMonth, endMonth, firstDayOfWeek);
        calendarView.scrollToMonth(currentMonth);

        binding.monthTitleText.setText(currentMonth.format(monthTitleFormatter));

        calendarView.setMonthScrollListener(month -> {
            updateRangeForMonth(month.getYearMonth());
            binding.monthTitleText.setText(month.getYearMonth().format(monthTitleFormatter));
            return null;
        });
        updateRangeForMonth(currentMonth);

        binding.monthPrevButton.setOnClickListener(v -> {
            CalendarMonth visible = calendarView.findFirstVisibleMonth();
            if (visible != null) {
                calendarView.smoothScrollToMonth(visible.getYearMonth().minusMonths(1));
            }
        });
        binding.monthNextButton.setOnClickListener(v -> {
            CalendarMonth visible = calendarView.findFirstVisibleMonth();
            if (visible != null) {
                calendarView.smoothScrollToMonth(visible.getYearMonth().plusMonths(1));
            }
        });
    }

    private void setupDayDetail() {
        dayDetailAdapter = new DayDetailAdapter();
        binding.dayDetailRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.dayDetailRecycler.setAdapter(dayDetailAdapter);
        binding.dayDetailAddButton.setOnClickListener(v -> launchAddEvent(selectedDate));
        refreshDayDetail();
    }

    private void onDaySelected(LocalDate date) {
        if (date.equals(selectedDate)) return;
        LocalDate previous = selectedDate;
        selectedDate = date;
        dayBinder.setSelectedDate(date);
        binding.monthCalendarView.notifyDateChanged(previous);
        binding.monthCalendarView.notifyDateChanged(date);
        refreshDayDetail();
    }

    private void updateRangeForMonth(YearMonth month) {
        long start = month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = month.atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        viewModel.setRange(start, end);
    }

    private void observeItems() {
        viewModel.getItems().observe(getViewLifecycleOwner(), items -> {
            if (binding == null) return;
            currentItemsByDate = groupByDate(items);
            dayBinder.setItemsByDate(currentItemsByDate);
            binding.monthCalendarView.notifyCalendarChanged();
            refreshDayDetail();
        });
    }

    private void refreshDayDetail() {
        if (binding == null) return;
        binding.dayDetailTitle.setText(getString(R.string.events_for, selectedDate.format(dayDetailFormatter)));
        List<CalendarItem> dayItems = currentItemsByDate.get(selectedDate);
        dayDetailAdapter.submitList(dayItems != null ? dayItems : Collections.emptyList());
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

    private void launchAddEvent(@Nullable LocalDate preselectedDate) {
        Intent intent = new Intent(getActivity(), AddEventActivity.class);
        intent.putExtra("entry", AddEventActivity.TYPE_TASK);
        if (preselectedDate != null) {
            long millis = preselectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            intent.putExtra(AddEventActivity.EXTRA_PRESELECTED_DATE_MILLIS, millis);
        }
        addEventLauncher.launch(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.calendarAddEventButton) {
            // FAB launches with no preselected date (defaults to today inside AddEventActivity).
            launchAddEvent(null);
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
