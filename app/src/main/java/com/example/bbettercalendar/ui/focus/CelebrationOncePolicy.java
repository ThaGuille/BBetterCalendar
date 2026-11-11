package com.example.bbettercalendar.ui.focus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * mejora-visual u13 (decision d3): pure rule of the short celebration. The weekly goal celebrates once
 * per ISO week and a completed project once per project id. It only decides and updates its in-memory
 * state; {@link CelebrationPrefs} loads and saves that state in SharedPreferences.
 */
public final class CelebrationOncePolicy {

    @Nullable private String lastWeekKey;
    private final Set<Integer> projectIds = new HashSet<>();

    public CelebrationOncePolicy(@Nullable String lastWeekKey, @NonNull Set<Integer> projectIds) {
        this.lastWeekKey = lastWeekKey;
        this.projectIds.addAll(projectIds);
    }

    /** True (and remembered) the first time the goal is met in the ISO week {@code weekKey}. */
    public boolean tryWeeklyGoal(boolean goalMet, @NonNull String weekKey) {
        if (!goalMet || weekKey.equals(lastWeekKey)) {
            return false;
        }
        lastWeekKey = weekKey;
        return true;
    }

    /** True (and remembered) the first time project {@code projectId} is completed; ids <= 0 never. */
    public boolean tryProject(int projectId) {
        return projectId > 0 && projectIds.add(projectId);
    }

    @Nullable
    public String getLastWeekKey() {
        return lastWeekKey;
    }

    @NonNull
    public Set<Integer> getProjectIds() {
        return new HashSet<>(projectIds);
    }

    /** "YYYY-Www" of the ISO-8601 week (Monday first, week 1 holds Jan 4th) of {@code when}. */
    @NonNull
    public static String isoWeekKey(@NonNull Calendar when) {
        Calendar c = Calendar.getInstance(when.getTimeZone());
        c.setFirstDayOfWeek(Calendar.MONDAY);
        c.setMinimalDaysInFirstWeek(4);
        c.setTimeInMillis(when.getTimeInMillis());
        return String.format(Locale.ROOT, "%04d-W%02d", c.getWeekYear(), c.get(Calendar.WEEK_OF_YEAR));
    }
}
