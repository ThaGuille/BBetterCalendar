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
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.calendarEntries.AddEventActivity;
import com.example.bbettercalendar.databinding.FragmentCalendarWeekBinding;
import com.example.bbettercalendar.ui.calendar.binders.WeekAgendaAdapter;
import com.example.bbettercalendar.ui.calendar.binders.WeekStripDayBinder;
import com.example.bbettercalendar.ui.calendar.domain.CalendarItem;
import com.example.bbettercalendar.ui.calendar.domain.DayPlan;
import com.example.bbettercalendar.ui.calendar.domain.ProjectDeadlineItemMapper;
import com.example.bbettercalendar.ui.calendar.domain.WeekAgenda;
import com.example.bbettercalendar.ui.common.motion.ScreenTransitions;
import com.google.android.material.chip.Chip;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Vista Semana (rediseno-calendario-color): una tira con los siete días arriba y, debajo, la agenda
 * seguida de toda la semana. Sustituye a la rejilla horaria (el WeekView vendorizado, que ya no se
 * usa desde aquí). Tocar un día de la tira lo selecciona y lleva la agenda a ese día; la tira pasa
 * de semana deslizando o con las flechas.
 */
@AndroidEntryPoint
public class CalendarFragmentWeek extends Fragment implements View.OnClickListener {

    // Lunes fijo, como el mes, la racha y Progress.
    private static final DayOfWeek FIRST_DAY_OF_WEEK = DayOfWeek.MONDAY;
    private static final int MONTHS_AROUND = 12;

    private FragmentCalendarWeekBinding binding;
    private CalendarViewModel viewModel;
    private WeekStripDayBinder dayBinder;
    private WeekAgendaAdapter agendaAdapter;
    private LinearLayoutManager agendaLayout;

    private LocalDate selectedDate = LocalDate.now();
    private LocalDate shownWeekStart = weekStartOf(LocalDate.now());
    private Map<LocalDate, List<CalendarItem>> itemsByDate = Collections.emptyMap();
    private Map<LocalDate, Integer> focusMinutes = Collections.emptyMap();
    private List<WeekAgenda.Row> rows = Collections.emptyList();

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

    // u15: transiciones de pantalla (ScreenTransitions: fundido entre pestañas, eje Z/X, contenedor).
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScreenTransitions.apply(this);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCalendarWeekBinding.inflate(inflater, container, false);

        viewModel = new ViewModelProvider(requireActivity()).get(CalendarViewModel.class);

        // Selector Mes/Semana en el contenido (antes: icono de la toolbar).
        View root = binding.getRoot();
        root.findViewById(R.id.calendarModeWeek).setSelected(true);
        root.findViewById(R.id.calendarModeMonth).setOnClickListener(v -> switchToMonth());

        binding.calendarAddEventButton2.setOnClickListener(this);

        setupAgenda();
        setupStrip();
        setupFocusLayer();
        observeData();

        return binding.getRoot();
    }

    private static LocalDate weekStartOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(FIRST_DAY_OF_WEEK));
    }

    private void setupAgenda() {
        agendaAdapter = new WeekAgendaAdapter();
        // Igual que en Mes: sólo las entregas de proyecto llevan a algún sitio (su proyecto).
        agendaAdapter.setOnItemClickListener(this::onAgendaItemClick);
        agendaLayout = new LinearLayoutManager(getContext());
        binding.weekAgendaRecycler.setLayoutManager(agendaLayout);
        binding.weekAgendaRecycler.setAdapter(agendaAdapter);
    }

    private void setupStrip() {
        dayBinder = new WeekStripDayBinder();
        dayBinder.setSelectedDate(selectedDate);
        dayBinder.setOnDayClickListener(this::onDaySelected);
        binding.weekStripView.setDayBinder(dayBinder);

        LocalDate today = LocalDate.now();
        binding.weekStripView.setup(today.minusMonths(MONTHS_AROUND), today.plusMonths(MONTHS_AROUND),
                FIRST_DAY_OF_WEEK);
        binding.weekStripView.scrollToWeek(selectedDate);
        binding.weekStripView.setWeekScrollListener(week -> {
            onWeekShown(week.getDays().get(0).getDate());
            return null;
        });
        onWeekShown(weekStartOf(selectedDate));

        binding.weekPrevButton.setOnClickListener(v ->
                binding.weekStripView.smoothScrollToWeek(shownWeekStart.minusWeeks(1)));
        binding.weekNextButton.setOnClickListener(v ->
                binding.weekStripView.smoothScrollToWeek(shownWeekStart.plusWeeks(1)));
        binding.weekTodayButton.setOnClickListener(v -> {
            LocalDate now = LocalDate.now();
            binding.weekStripView.smoothScrollToWeek(now);
            onDaySelected(now);
        });
    }

    // Nueva semana a la vista: consulta sus siete días, pone el título y, si el día seleccionado
    // queda fuera, selecciona hoy (si cae en ella) o su lunes.
    private void onWeekShown(LocalDate weekStart) {
        if (binding == null) return;
        shownWeekStart = weekStart;
        ZoneId zone = ZoneId.systemDefault();
        long start = weekStart.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = weekStart.plusDays(WeekAgenda.DAYS - 1).atTime(23, 59, 59).atZone(zone)
                .toInstant().toEpochMilli();
        viewModel.setRange(start, end);
        binding.weekMonthTitleText.setText(CalendarTitleFormatter.week(requireContext(), weekStart));

        if (!inShownWeek(selectedDate)) {
            LocalDate today = LocalDate.now();
            LocalDate previous = selectedDate;
            selectedDate = inShownWeek(today) ? today : weekStart;
            dayBinder.setSelectedDate(selectedDate);
            // Fuera del pase de scroll de la tira: no se puede notificar al adapter desde dentro.
            binding.weekStripView.post(() -> {
                if (binding == null) return;
                binding.weekStripView.notifyDateChanged(previous);
                binding.weekStripView.notifyDateChanged(selectedDate);
            });
        }
        rebuildAgenda();
    }

    private boolean inShownWeek(LocalDate date) {
        return !date.isBefore(shownWeekStart)
                && date.isBefore(shownWeekStart.plusDays(WeekAgenda.DAYS));
    }

    private void onDaySelected(LocalDate date) {
        if (binding == null) return;
        if (!date.equals(selectedDate)) {
            LocalDate previous = selectedDate;
            selectedDate = date;
            dayBinder.setSelectedDate(date);
            binding.weekStripView.notifyDateChanged(previous);
            binding.weekStripView.notifyDateChanged(date);
        }
        int position = WeekAgenda.headerPosition(rows, date);
        if (position >= 0) agendaLayout.scrollToPositionWithOffset(position, 0);
    }

    // Capa de enfoque: misma preferencia que en Mes (vive en el ViewModel compartido).
    private void setupFocusLayer() {
        Chip toggle = binding.getRoot().findViewById(R.id.calendarFocusToggle);
        toggle.setChecked(viewModel.isFocusLayerVisible());
        dayBinder.setFocusLayerVisible(viewModel.isFocusLayerVisible());
        toggle.setOnCheckedChangeListener((button, checked) -> {
            viewModel.setFocusLayerVisible(checked);
            if (binding == null) return;
            dayBinder.setFocusLayerVisible(checked);
            binding.weekStripView.notifyCalendarChanged();
            rebuildAgenda();
        });
    }

    private void observeData() {
        viewModel.getItems().observe(getViewLifecycleOwner(), items -> {
            if (binding == null) return;
            itemsByDate = DayPlan.groupByDate(items, ZoneId.systemDefault());
            dayBinder.setItemsByDate(itemsByDate);
            binding.weekStripView.notifyCalendarChanged();
            rebuildAgenda();
        });
        viewModel.getFocusMinutes().observe(getViewLifecycleOwner(), minutes -> {
            if (binding == null) return;
            focusMinutes = minutes != null ? minutes : Collections.emptyMap();
            dayBinder.setFocusMinutesByDate(focusMinutes);
            binding.weekStripView.notifyCalendarChanged();
            rebuildAgenda();
        });
    }

    private void rebuildAgenda() {
        rows = WeekAgenda.rows(shownWeekStart, itemsByDate);
        agendaAdapter.submit(rows, focusMinutes, viewModel.isFocusLayerVisible());
    }

    // El calendario pinta la entrega pero no la edita: tocarla lleva al detalle del proyecto.
    private void onAgendaItemClick(CalendarItem item) {
        int projectId = ProjectDeadlineItemMapper.projectIdOf(item);
        if (projectId <= 0) return;
        Bundle args = new Bundle();
        args.putInt("projectId", projectId);
        Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_activity_main)
                .navigate(R.id.action_global_project_detail, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.calendarAddEventButton2) {
            // El FAB crea en el día seleccionado de la tira.
            Intent intent = new Intent(getActivity(), AddEventActivity.class);
            intent.putExtra("entry", AddEventActivity.TYPE_TASK);
            long millis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            intent.putExtra(AddEventActivity.EXTRA_PRESELECTED_DATE_MILLIS, millis);
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
