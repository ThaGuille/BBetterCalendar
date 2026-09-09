package com.example.bbettercalendar.popups;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.stats.FocusEventDAO;
import com.kizitonwose.calendar.core.CalendarDay;
import com.kizitonwose.calendar.core.CalendarMonth;
import com.kizitonwose.calendar.core.DayPosition;
import com.kizitonwose.calendar.view.CalendarView;
import com.kizitonwose.calendar.view.ViewContainer;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Calendario de rachas (spec focus-mode-and-streak): un mes navegable con los días en los que el
 * usuario completó al menos un pomodoro marcados en {@code bb_accent_reward}.
 *
 * <p>Los días salen de {@code focus_event} (una fila por sesión terminada), agregados por fecha
 * CIVIL local en SQL — ver {@code FocusEventDAO.getActiveDaysSince}. Se cargan de una sola vez
 * para los últimos 12 meses y se guardan en memoria: navegar de mes no vuelve a tocar la BD.
 */
@AndroidEntryPoint
public class FocusStreakPopup extends DialogFragment {

    public static final String POPUP_TAG = "focus_streak_popup";

    /** Ventana que se precarga y que el usuario puede recorrer con las flechas. */
    private static final int MONTHS_BACK = 12;
    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    @Inject FocusEventDAO focusEventDao;
    @Inject @IoExecutor ExecutorService ioExecutor;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final DateTimeFormatter monthTitleFormatter =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault());

    private CalendarView calendarView;
    private TextView summaryText;
    private TextView monthTitle;
    private final StreakDayBinder dayBinder = new StreakDayBinder();

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity(), R.style.RoundedDialog);
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.popup_focus_streak, null);
        builder.setView(view);

        summaryText = view.findViewById(R.id.streakSummaryText);
        monthTitle = view.findViewById(R.id.streakMonthTitle);
        calendarView = view.findViewById(R.id.streakCalendarView);
        view.findViewById(R.id.streakCloseButton).setOnClickListener(v -> dismiss());

        populateWeekdayLegend(view);
        setUpCalendar(view);
        loadDays();

        return builder.create();
    }

    /** Mismos nombres de día (y mismo primer día de semana) que la pantalla de calendario. */
    private void populateWeekdayLegend(View root) {
        DayOfWeek firstDow = WeekFields.of(Locale.getDefault()).getFirstDayOfWeek();
        int[] ids = {
                R.id.legendDay0, R.id.legendDay1, R.id.legendDay2, R.id.legendDay3,
                R.id.legendDay4, R.id.legendDay5, R.id.legendDay6
        };
        for (int i = 0; i < 7; i++) {
            TextView tv = root.findViewById(ids[i]);
            tv.setText(firstDow.plus(i).getDisplayName(TextStyle.SHORT, Locale.getDefault()));
        }
    }

    private void setUpCalendar(View root) {
        YearMonth currentMonth = YearMonth.now();
        calendarView.setDayBinder(dayBinder);
        // El futuro no puede tener rachas, así que el mes actual es el final del rango.
        calendarView.setup(currentMonth.minusMonths(MONTHS_BACK), currentMonth,
                WeekFields.of(Locale.getDefault()).getFirstDayOfWeek());
        calendarView.scrollToMonth(currentMonth);
        monthTitle.setText(currentMonth.format(monthTitleFormatter));

        calendarView.setMonthScrollListener(month -> {
            monthTitle.setText(month.getYearMonth().format(monthTitleFormatter));
            return null;
        });

        root.findViewById(R.id.streakPrevButton).setOnClickListener(v -> {
            CalendarMonth visible = calendarView.findFirstVisibleMonth();
            if (visible != null) {
                calendarView.smoothScrollToMonth(visible.getYearMonth().minusMonths(1));
            }
        });
        root.findViewById(R.id.streakNextButton).setOnClickListener(v -> {
            CalendarMonth visible = calendarView.findFirstVisibleMonth();
            if (visible != null) {
                calendarView.smoothScrollToMonth(visible.getYearMonth().plusMonths(1));
            }
        });
    }

    /** Lectura en el executor de IO (regla #3) y vuelta al main thread para repintar. */
    private void loadDays() {
        long startOfToday = startOfTodayMillis();
        long since = startOfToday - (MONTHS_BACK + 1L) * 31 * DAY_MILLIS;
        long last30Start = startOfToday - 29L * DAY_MILLIS;
        long endOfToday = startOfToday + DAY_MILLIS - 1;
        ioExecutor.execute(() -> {
            List<String> isoDays = focusEventDao.getActiveDaysSince(since);
            Set<LocalDate> days = new HashSet<>();
            for (String iso : isoDays) {
                if (iso == null) continue;
                try {
                    days.add(LocalDate.parse(iso));
                } catch (RuntimeException ignored) {
                    // Una fila con timestamp corrupto no debe tumbar el popup entero.
                }
            }
            int last30 = focusEventDao.countActiveDaysBetween(last30Start, endOfToday);
            mainHandler.post(() -> {
                if (!isAdded() || calendarView == null) {
                    return;
                }
                dayBinder.setDays(days);
                calendarView.notifyCalendarChanged();
                summaryText.setText(days.isEmpty()
                        ? getString(R.string.focus_streak_empty)
                        : getString(R.string.focus_streak_summary, last30));
            });
        });
    }

    private static long startOfTodayMillis() {
        Calendar day = Calendar.getInstance();
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        return day.getTimeInMillis();
    }

    /** Pinta el disco de recompensa en los días con pomodoro y el anillo en el día de hoy. */
    private static class StreakDayBinder
            implements com.kizitonwose.calendar.view.MonthDayBinder<StreakDayBinder.DayContainer> {

        private Set<LocalDate> days = Collections.emptySet();

        void setDays(@NonNull Set<LocalDate> days) {
            this.days = days;
        }

        @NonNull
        @Override
        public DayContainer create(@NonNull View view) {
            return new DayContainer(view);
        }

        @Override
        public void bind(@NonNull DayContainer container, CalendarDay day) {
            boolean inMonth = day.getPosition() == DayPosition.MonthDate;
            container.dayText.setText(String.valueOf(day.getDate().getDayOfMonth()));
            container.dayText.setAlpha(inMonth ? 1f : 0.25f);

            boolean marked = inMonth && days.contains(day.getDate());
            container.mark.setVisibility(marked ? View.VISIBLE : View.GONE);
            container.today.setVisibility(
                    inMonth && day.getDate().equals(LocalDate.now()) ? View.VISIBLE : View.GONE);
            // Sobre el disco lleno el texto oscuro pierde contraste: en los días marcados va en
            // bb_on_primary, en el resto en el color normal de superficie.
            container.dayText.setTextColor(ContextCompat.getColor(
                    container.dayText.getContext(),
                    marked ? R.color.bb_on_primary : R.color.bb_on_surface));
        }

        static class DayContainer extends ViewContainer {
            final TextView dayText;
            final View mark;
            final View today;

            DayContainer(@NonNull View view) {
                super(view);
                this.dayText = view.findViewById(R.id.streakDayText);
                this.mark = view.findViewById(R.id.streakDayMark);
                this.today = view.findViewById(R.id.streakDayToday);
            }
        }
    }
}
