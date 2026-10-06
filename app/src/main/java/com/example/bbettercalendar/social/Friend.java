package com.example.bbettercalendar.social;

import androidx.annotation.NonNull;

/**
 * A friend as shown by the social blocks (Home "friends studying now", Focus "Studying now" grid,
 * Cycle complete strip). Immutable value object; today it only comes from
 * {@link MockSocialRepository} (u11 — see .claude/docs/systems/ui-placeholders.md).
 */
public final class Friend {

    /** Presence. Maps 1:1 onto {@code AvatarView.Status.STUDYING / AWAY}. */
    public enum Status { STUDYING, AWAY }

    @NonNull private final String id;
    @NonNull private final String name;
    @NonNull private final String initials;
    @NonNull private final Status status;
    private final long focusSecondsToday;

    public Friend(@NonNull String id, @NonNull String name, @NonNull String initials,
                  @NonNull Status status, long focusSecondsToday) {
        this.id = id;
        this.name = name;
        this.initials = initials;
        this.status = status;
        this.focusSecondsToday = Math.max(0, focusSecondsToday);
    }

    @NonNull public String getId() { return id; }

    @NonNull public String getName() { return name; }

    /** One or two letters for the avatar (html 01 uses two: "LM", html 04 one: "M" — first char). */
    @NonNull public String getInitials() { return initials; }

    @NonNull public Status getStatus() { return status; }

    public boolean isStudying() { return status == Status.STUDYING; }

    /** Focus time today in seconds (html 01 shows it as h:mm:ss under the avatar). */
    public long getFocusSecondsToday() { return focusSecondsToday; }

    public int getMinutesToday() { return (int) (focusSecondsToday / 60); }

    @NonNull
    @Override
    public String toString() {
        return "Friend{" + id + ", " + name + ", " + status + ", " + focusSecondsToday + "s}";
    }
}
