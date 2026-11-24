package com.example.bbettercalendar.ui.projects.form;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.domain.DateChoices;

import java.time.ZoneId;

/**
 * State and rules of the create / edit project form (rediseno-proyectos u10, spec 3.6). Pure Java: the
 * name, note, icon, colour, deadline and area being edited, plus the values the form opened with so it
 * can tell whether leaving needs the discard question. The view only reads and writes this.
 */
public final class ProjectFormModel {

    /** Quick deadline choices of the form; OTHER is any date picked with the date picker. */
    public enum DeadlineChoice { NONE, END_OF_MONTH, IN_A_MONTH, OTHER }

    /** Project colours (bb_project_1..4): the default one cycles through them by project count. */
    public static final int COLOR_COUNT = 4;
    /** Longest name the field accepts. */
    public static final int MAX_NAME_LENGTH = 28;

    private final String initialName;
    private final String initialNotes;
    private final String initialIconKey;
    private final int initialColorIndex;
    private final long initialDeadlineMillis;
    private final int initialAreaId;

    private String name;
    private String notes;
    private String iconKey;
    private int colorIndex;
    private long deadlineMillis;
    private int areaId;

    private ProjectFormModel(String name, String notes, String iconKey, int colorIndex,
                             long deadlineMillis, int areaId) {
        this.initialName = this.name = name;
        this.initialNotes = this.notes = notes;
        this.initialIconKey = this.iconKey = iconKey;
        this.initialColorIndex = this.colorIndex = colorIndex;
        this.initialDeadlineMillis = this.deadlineMillis = deadlineMillis;
        this.initialAreaId = this.areaId = areaId;
    }

    /** A new project: no name, default icon, the next colour of the cycle, no date, no area. */
    @NonNull
    public static ProjectFormModel forNew(int projectCount) {
        return new ProjectFormModel("", "", Project.DEFAULT_ICON_KEY, defaultColorIndex(projectCount), 0L, 0);
    }

    /** An existing project, as stored. */
    @NonNull
    public static ProjectFormModel forEdit(@NonNull Project project) {
        return new ProjectFormModel(project.name == null ? "" : project.name,
                project.notes == null ? "" : project.notes,
                project.iconKey, project.colorIndex, project.softDeadlineMillis, project.areaId);
    }

    /** Colour index a new project starts with: the next one in the cycle after {@code projectCount} projects. */
    public static int defaultColorIndex(int projectCount) {
        return Math.floorMod(projectCount, COLOR_COUNT);
    }

    // ---------------------------------------------------------------- reads

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getNotes() {
        return notes;
    }

    @NonNull
    public String getIconKey() {
        return iconKey;
    }

    public int getColorIndex() {
        return colorIndex;
    }

    /** 0 = no deadline. */
    public long getDeadlineMillis() {
        return deadlineMillis;
    }

    /** 0 = no area. */
    public int getAreaId() {
        return areaId;
    }

    /** Create / Save is enabled only with a name. */
    public boolean isSaveEnabled() {
        return !name.trim().isEmpty();
    }

    /** True when something changed since the form opened (leaving then asks to discard). */
    public boolean isDirty() {
        return !name.trim().equals(initialName.trim())
                || !notes.trim().equals(initialNotes.trim())
                || !iconKey.equals(initialIconKey)
                || colorIndex != initialColorIndex
                || deadlineMillis != initialDeadlineMillis
                || areaId != initialAreaId;
    }

    /** Which quick choice the current deadline is: none, end of this month, in a month, or another date. */
    @NonNull
    public DeadlineChoice deadlineChoice(long nowMillis, @NonNull ZoneId zone) {
        if (deadlineMillis <= 0L) return DeadlineChoice.NONE;
        if (deadlineMillis == DateChoices.endOfMonth(nowMillis, zone)) return DeadlineChoice.END_OF_MONTH;
        if (deadlineMillis == DateChoices.inOneMonth(nowMillis, zone)) return DeadlineChoice.IN_A_MONTH;
        return DeadlineChoice.OTHER;
    }

    // ---------------------------------------------------------------- writes

    public void setName(@Nullable String value) {
        name = value == null ? "" : value;
    }

    public void setNotes(@Nullable String value) {
        notes = value == null ? "" : value;
    }

    public void setIconKey(@Nullable String key) {
        iconKey = key == null ? Project.DEFAULT_ICON_KEY : key;
    }

    public void setColorIndex(int index) {
        colorIndex = index;
    }

    /** A date picked with the date picker (start of day millis); {@code <= 0} clears the deadline. */
    public void setDeadlineMillis(long millis) {
        deadlineMillis = Math.max(0L, millis);
    }

    /** Applies a quick choice; OTHER keeps the current date (the picker sets it with {@link #setDeadlineMillis}). */
    public void chooseDeadline(@NonNull DeadlineChoice choice, long nowMillis, @NonNull ZoneId zone) {
        switch (choice) {
            case NONE:
                deadlineMillis = 0L;
                break;
            case END_OF_MONTH:
                deadlineMillis = DateChoices.endOfMonth(nowMillis, zone);
                break;
            case IN_A_MONTH:
                deadlineMillis = DateChoices.inOneMonth(nowMillis, zone);
                break;
            default:
                break;
        }
    }

    /** Taps an area: picks it, or clears the area when it already is the chosen one. */
    public void toggleArea(int tappedAreaId) {
        areaId = areaId == tappedAreaId ? 0 : tappedAreaId;
    }
}
