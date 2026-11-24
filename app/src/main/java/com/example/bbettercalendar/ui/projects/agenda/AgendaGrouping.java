package com.example.bbettercalendar.ui.projects.agenda;

import com.example.bbettercalendar.ui.projects.picker.PickerLabels;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Groups the pending tasks of a project for the agenda sheet (rediseno-proyectos spec 3.2): dated tasks by
 * local day in date order (Yesterday and older days overdue, Today, Tomorrow, then "Mon 23"), then the
 * project deadline, then the count of undated tasks. Pure Java: the texts of Yesterday / Today / Tomorrow
 * are resources, so those headers only carry a {@link AgendaRow.Day}.
 */
public final class AgendaGrouping {
    private AgendaGrouping() {}

    /**
     * @param tasks          pending (not done) tasks of the project, in any order
     * @param deadlineMillis project deadline; 0 = none
     */
    public static List<AgendaRow> build(List<AgendaTask> tasks, long deadlineMillis, long nowMillis,
                                        ZoneId zone, Locale locale) {
        List<AgendaTask> dated = new ArrayList<>();
        int undated = 0;
        for (AgendaTask t : tasks) {
            if (t.dueMillis > 0L) dated.add(t);
            else undated++;
        }
        Collections.sort(dated, new Comparator<AgendaTask>() {
            @Override
            public int compare(AgendaTask a, AgendaTask b) {
                return Long.compare(a.dueMillis, b.dueMillis);
            }
        });

        LocalDate today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate();
        List<AgendaRow> rows = new ArrayList<>();
        LocalDate last = null;
        for (AgendaTask t : dated) {
            LocalDate day = Instant.ofEpochMilli(t.dueMillis).atZone(zone).toLocalDate();
            if (!day.equals(last)) {
                rows.add(headerFor(day, today, locale));
                last = day;
            }
            String time = t.hasTime
                    ? PickerLabels.timeLabel(Instant.ofEpochMilli(t.dueMillis).atZone(zone)
                            .toLocalTime().toSecondOfDay() / 60)
                    : "";
            String duration = t.durationMinutes > 0 ? PickerLabels.durationLabel(t.durationMinutes) : "";
            rows.add(AgendaRow.task(time, t.title, duration, t.blockOnFocus));
        }
        if (deadlineMillis > 0L) {
            LocalDate d = Instant.ofEpochMilli(deadlineMillis).atZone(zone).toLocalDate();
            rows.add(AgendaRow.deadline(dayLabel(d, "EEE d MMM", locale)));
        }
        if (undated > 0) rows.add(AgendaRow.undated(undated));
        return rows;
    }

    private static AgendaRow headerFor(LocalDate day, LocalDate today, Locale locale) {
        long offset = ChronoUnit.DAYS.between(today, day);
        if (offset == -1) return AgendaRow.header(AgendaRow.Day.YESTERDAY, AgendaRow.Tone.OVERDUE, null);
        if (offset == 0) return AgendaRow.header(AgendaRow.Day.TODAY, AgendaRow.Tone.TODAY, null);
        if (offset == 1) return AgendaRow.header(AgendaRow.Day.TOMORROW, AgendaRow.Tone.NORMAL, null);
        return AgendaRow.header(AgendaRow.Day.DATE,
                offset < 0 ? AgendaRow.Tone.OVERDUE : AgendaRow.Tone.NORMAL,
                dayLabel(day, "EEE d", locale));
    }

    // "Mon 23" / "Lun 23": the weekday starts with a capital in both languages.
    private static String dayLabel(LocalDate date, String pattern, Locale locale) {
        String s = date.format(DateTimeFormatter.ofPattern(pattern, locale)).replace(".", "");
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(locale) + s.substring(1);
    }
}
