package com.example.bbettercalendar.ui.projects.picker;

/**
 * Whether the task Repeat option exists yet. It is inert until the repetition part lands (user
 * decision): while this is false the Repeat row of the More picker (and its note) is GONE and nothing
 * else in the pickers, the quick add or the editor reads or writes Repeat. The repetition part flips
 * {@link #DEFAULT} to true. {@link #setEnabled(boolean)} is used only by the debug gallery.
 */
public final class RepeatAvailability {
    /** Default of the app build. */
    static final boolean DEFAULT = false;

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
