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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;

public class CalendarFragmentWeek extends Fragment
        implements View.OnClickListener, OnToolBarListener, OnToolbarCalendarListener {

    private FragmentCalendarWeekBinding binding;
    private CalendarViewModel viewModel;
    private ToolbarHelper toolbarHelper;
    private WeekViewItemAdapter adapter;

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
        getActivity().setTheme(R.style.ThemeColorGreen);
        binding = FragmentCalendarWeekBinding.inflate(inflater, container, false);

        viewModel = new ViewModelProvider(requireActivity()).get(CalendarViewModel.class);

        toolbarHelper = new ToolbarHelper(getContext(), getActivity(), getActivity().getMenuInflater(), R.menu.toolbar, true);
        toolbarHelper.setOnToolbarListener(this);
        toolbarHelper.setOnToolbarCalendarListener(this);
        getActivity().addMenuProvider(toolbarHelper, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        binding.calendarAddEventButton2.setOnClickListener(this);

        adapter = new WeekViewItemAdapter();
        binding.weekView.setAdapter(adapter);

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
