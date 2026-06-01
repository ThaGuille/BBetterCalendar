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
import com.example.bbettercalendar.databinding.FragmentCalendarWeekBinding;
import com.example.bbettercalendar.helpers.OnToolBarListener;
import com.example.bbettercalendar.helpers.OnToolbarCalendarListener;
import com.example.bbettercalendar.helpers.ToolbarHelper;
import com.example.bbettercalendar.ui.calendar.binders.WeekViewItemAdapter;

import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

public class CalendarFragmentWeek extends Fragment
        implements View.OnClickListener, OnToolBarListener, OnToolbarCalendarListener {

    private FragmentCalendarWeekBinding binding;
    private CalendarViewModel viewModel;
    private ToolbarHelper toolbarHelper;
    private WeekViewItemAdapter adapter;
    private final SimpleDateFormat monthYearFormat =
            new SimpleDateFormat("MMMM yyyy", Locale.getDefault());

    private final ActivityResultLauncher<Intent> addEventLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    // Room's InvalidationTracker occasionally lags after the add-activity
                    // commits a row, so the just-inserted entry doesn't appear until the
                    // next insert nudges the LiveData. Force a re-query on return.
                    if (viewModel != null) viewModel.refresh();
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        getActivity().setTheme(R.style.ThemeColorGreen);
        binding = FragmentCalendarWeekBinding.inflate(inflater, container, false);

        viewModel = new ViewModelProvider(requireActivity()).get(CalendarViewModel.class);

        toolbarHelper = new ToolbarHelper(getContext(), getActivity(), getActivity().getMenuInflater(), R.menu.toolbar, true);
        toolbarHelper.setOnToolbarListener(this);
        toolbarHelper.setOnToolbarCalendarListener(this);
        getActivity().addMenuProvider(toolbarHelper, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

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

    @Override
    public void onToolbarLoaded(int result) { }

    @Override
    public void switchFragment() {
        NavController navController = Navigation.findNavController(getActivity(), R.id.nav_host_fragment_activity_main);
        navController.navigate(R.id.action_navigation_calendar_week_to_navigation_calendar_month);
    }
}
