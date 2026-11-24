package com.example.bbettercalendar.ui.projects.picker;

/**
 * Whether the task Repeat option exists. It stayed inert until the repetition part landed (user
 * decision); that part (spec 3.8) flipped {@link #DEFAULT} to true: the Repeat row of the More picker
 * (and its note) shows, the quick-add chip and the editor's When tile show the repeat, and
 * ProjectTasksRepository.saveDraft turns a draft with Repeat into a series. While false (debug gallery
 * only, through {@link #setEnabled(boolean)}) the row is GONE and nothing reads or writes Repeat.
 */
public final class RepeatAvailability {
    /** Default of the app build. */
    static final boolean DEFAULT = true;

    private static volatile boolean enabled = DEFAULT;

    private RepeatAvailability() {}

    public static boolean isEnabled() {
        return enabled;
    }

    /** Debug gallery only. */
    public static void setEnabled(boolean value) {
        enabled = value;
    }
}
