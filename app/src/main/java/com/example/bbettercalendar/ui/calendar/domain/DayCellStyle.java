package com.example.bbettercalendar.ui.calendar.domain;

import androidx.annotation.NonNull;

/**
 * What a month-grid day cell draws (pomodoro-calendario-ui u5). Pure, so it is unit-testable on the
 * JVM; {@code MonthDayBinder} turns it into views.
 *
 * <p>The cell uses separate channels so no mark hides another:
 * <ul>
 *   <li>focus: sage wash of the cell background (only with the focus layer on), level 0..{@link CalendarHeatLevel#MAX_LEVEL}
 *       ({@code bg_calendar_focus_N}); kept whatever the other marks are;</li>
 *   <li>today: the day number inside a filled circle ({@code cellTodayIndicator}), number drawn in
 *       the on-circle colour;</li>
 *   <li>selected: a light outline around the cell ({@code cellSelectedOutline}), no fill.</li>
 * </ul>
 * A day that is both today and selected shows the circle and the outline.
 */
public final class DayCellStyle {

    /** Tint level of the background: 0 = none, 1..3 = {@code bb_cal_focus_1..3}. */
    public final int focusLevel;
    /** Draw the filled circle behind the day number. */
    public final boolean todayCircle;
    /** Draw the light outline around the cell. */
    public final boolean selectionOutline;
    /** Draw the day number in the on-circle colour (it sits on the today circle). */
    public final boolean numberOnCircle;

    private DayCellStyle(int focusLevel, boolean todayCircle, boolean selectionOutline) {
        this.focusLevel = focusLevel;
        this.todayCircle = todayCircle;
        this.selectionOutline = selectionOutline;
        this.numberOnCircle = todayCircle;
    }

    /**
     * @param isToday    the day is today (and in the shown month)
     * @param isSelected the day is the selected one (and in the shown month)
     * @param focusLevel {@link CalendarHeatLevel#levelFor(int)} of the day; clamped to 0..MAX_LEVEL
     */
    @NonNull
    public static DayCellStyle of(boolean isToday, boolean isSelected, int focusLevel) {
        int level = Math.max(0, Math.min(CalendarHeatLevel.MAX_LEVEL, focusLevel));
        return new DayCellStyle(level, isToday, isSelected);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DayCellStyle)) return false;
        DayCellStyle that = (DayCellStyle) o;
        return focusLevel == that.focusLevel
                && todayCircle == that.todayCircle
                && selectionOutline == that.selectionOutline
                && numberOnCircle == that.numberOnCircle;
    }

    @Override
    public int hashCode() {
        int h = focusLevel;
        h = 31 * h + (todayCircle ? 1 : 0);
        h = 31 * h + (selectionOutline ? 1 : 0);
        h = 31 * h + (numberOnCircle ? 1 : 0);
        return h;
    }

    @NonNull
    @Override
    public String toString() {
        return "DayCellStyle{focus=" + focusLevel + ", today=" + todayCircle
                + ", selected=" + selectionOutline + ", numberOnCircle=" + numberOnCircle + '}';
    }
}
