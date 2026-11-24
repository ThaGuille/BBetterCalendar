package com.example.bbettercalendar.ui.projects;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.example.bbettercalendar.projects.domain.TaskOrdering;

import java.util.HashSet;
import java.util.Set;

/**
 * What the Projects screen remembers between openings (rediseno-proyectos): selected project, sort,
 * area filter, projects with Hide done on, and the collapsed sections. Tiny values written with
 * {@code apply()} (background write), so the UI thread may call it.
 */
public final class ProjectsPrefs {

    private static final String FILE = "projects_screen";
    private static final String KEY_SELECTED = "selected_project";
    private static final String KEY_SORT = "sort_mode";
    private static final String KEY_AREA = "area_filter";
    private static final String KEY_HIDE_DONE = "hide_done_projects";
    private static final String KEY_COLLAPSED = "collapsed_sections";

    private final SharedPreferences prefs;

    public ProjectsPrefs(@NonNull Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** 0 = nothing remembered. */
    public int getSelectedProject() {
        return prefs.getInt(KEY_SELECTED, 0);
    }

    public void setSelectedProject(int projectId) {
        prefs.edit().putInt(KEY_SELECTED, projectId).apply();
    }

    public TaskOrdering.Mode getSort() {
        int ordinal = prefs.getInt(KEY_SORT, 0);
        TaskOrdering.Mode[] modes = TaskOrdering.Mode.values();
        return ordinal >= 0 && ordinal < modes.length ? modes[ordinal] : TaskOrdering.Mode.UPCOMING;
    }

    public void setSort(TaskOrdering.Mode mode) {
        prefs.edit().putInt(KEY_SORT, mode.ordinal()).apply();
    }

    /** 0 = all areas. */
    public int getAreaFilter() {
        return prefs.getInt(KEY_AREA, 0);
    }

    public void setAreaFilter(int areaId) {
        prefs.edit().putInt(KEY_AREA, areaId).apply();
    }

    public Set<Integer> getHideDoneProjects() {
        return toInts(prefs.getStringSet(KEY_HIDE_DONE, null));
    }

    public void setHideDone(int projectId, boolean hide) {
        Set<Integer> now = getHideDoneProjects();
        if (hide) now.add(projectId); else now.remove(projectId);
        prefs.edit().putStringSet(KEY_HIDE_DONE, toStrings(now)).apply();
    }

    public Set<Long> getCollapsedSections() {
        Set<Long> out = new HashSet<>();
        Set<String> raw = prefs.getStringSet(KEY_COLLAPSED, null);
        if (raw != null) {
            for (String s : raw) {
                try {
                    out.add(Long.parseLong(s));
                } catch (NumberFormatException ignored) {
                    // a corrupt entry is dropped
                }
            }
        }
        return out;
    }

    public void setCollapsed(long sectionId, boolean collapsed) {
        Set<Long> now = getCollapsedSections();
        if (collapsed) now.add(sectionId); else now.remove(sectionId);
        Set<String> out = new HashSet<>();
        for (Long id : now) out.add(String.valueOf(id));
        prefs.edit().putStringSet(KEY_COLLAPSED, out).apply();
    }

    private static Set<Integer> toInts(Set<String> raw) {
        Set<Integer> out = new HashSet<>();
        if (raw != null) {
            for (String s : raw) {
                try {
                    out.add(Integer.parseInt(s));
                } catch (NumberFormatException ignored) {
                    // a corrupt entry is dropped
                }
            }
        }
        return out;
    }

    private static Set<String> toStrings(Set<Integer> ints) {
        Set<String> out = new HashSet<>();
        for (Integer i : ints) out.add(String.valueOf(i));
        return out;
    }
}
