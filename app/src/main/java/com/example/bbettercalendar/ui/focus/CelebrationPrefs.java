package com.example.bbettercalendar.ui.focus;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

/**
 * SharedPreferences memory of {@link CelebrationOncePolicy} (d3): last ISO week celebrated for the weekly
 * goal and the ids of the projects already celebrated. Reads and writes are tiny ({@code apply()} writes
 * in the background), so the UI thread may call it.
 */
public final class CelebrationPrefs {

    private static final String FILE = "celebrations";
    private static final String KEY_WEEK = "weekly_goal_week";
    private static final String KEY_PROJECTS = "project_ids";

    private CelebrationPrefs() {
    }

    /** True when the weekly goal sheet should show now; records it so it never repeats this week. */
    public static boolean claimWeeklyGoal(@NonNull Context context, boolean goalMet) {
        SharedPreferences prefs = prefs(context);
        CelebrationOncePolicy policy = new CelebrationOncePolicy(
                prefs.getString(KEY_WEEK, null), loadProjects(prefs));
        boolean show = policy.tryWeeklyGoal(goalMet,
                CelebrationOncePolicy.isoWeekKey(Calendar.getInstance()));
        if (show) {
            prefs.edit().putString(KEY_WEEK, policy.getLastWeekKey()).apply();
        }
        return show;
    }

    /** True when the completed-project sheet should show now; records the project id. */
    public static boolean claimProject(@NonNull Context context, int projectId) {
        SharedPreferences prefs = prefs(context);
        CelebrationOncePolicy policy = new CelebrationOncePolicy(
                prefs.getString(KEY_WEEK, null), loadProjects(prefs));
        boolean show = policy.tryProject(projectId);
        if (show) {
            Set<String> out = new HashSet<>();
            for (Integer id : policy.getProjectIds()) {
                out.add(String.valueOf(id));
            }
            prefs.edit().putStringSet(KEY_PROJECTS, out).apply();
        }
        return show;
    }

    private static Set<Integer> loadProjects(SharedPreferences prefs) {
        Set<Integer> ids = new HashSet<>();
        for (String s : prefs.getStringSet(KEY_PROJECTS, new HashSet<String>())) {
            try {
                ids.add(Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
                // a corrupt entry is dropped
            }
        }
        return ids;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
