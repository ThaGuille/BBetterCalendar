package com.example.bbettercalendar.social;

import androidx.annotation.NonNull;

import com.example.bbettercalendar.social.presence.Presence;

import java.util.Locale;

/**
 * A friend as shown by the social blocks (Home "friends studying now", Focus "Studying now" grid,
 * Cycle complete strip, Amigos). Immutable value object built by {@link FirebaseSocialRepository}
 * (F4-u4: profile card + live presence) or by {@link MockSocialRepository} in tests.
 *
 * <p>Presence (02 §7, D-033): {@link #isStudying()} is true for {@code focus}, {@code break} and
 * {@code paused} (a paused friend is still present); {@link #isPaused()} tells the paused ones apart
 * ("En pausa", frozen time). Times are on <b>this device's clock</b>: the repository already removed
 * the server offset, so {@code System.currentTimeMillis() - getSessionStartMs()} is the live time.
 */
public final class Friend {

    /** Presence. Maps 1:1 onto {@code AvatarView.Status.STUDYING / AWAY} (paused is STUDYING). */
    public enum Status { STUDYING, AWAY }

    @NonNull private final String uid;
    @NonNull private final String handle;
    @NonNull private final String name;
    @NonNull private final String initials;
    private final int avatarColor;
    @NonNull private final Status status;
    private final boolean paused;
    private final long sessionStartMs;
    private final long frozenElapsedMs;
    private final long focusSecondsToday;

    /**
     * @param presence          what the viewer sees ({@link Presence}, server clock)
     * @param serverOffsetMs    server clock minus this device's clock ({@code .info/serverTimeOffset})
     * @param focusSecondsToday today's focus time; only the mock has it (0 from the real repository)
     */
    public Friend(@NonNull String uid, @NonNull String handle, @NonNull String name, int avatarColor,
                  @NonNull Presence presence, long serverOffsetMs, long focusSecondsToday) {
        this.uid = uid;
        this.handle = handle;
        this.name = name;
        this.initials = initialsOf(name);
        this.avatarColor = avatarColor;
        this.status = presence.isPresent() ? Status.STUDYING : Status.AWAY;
        this.paused = presence.state == Presence.State.PAUSED;
        this.sessionStartMs = presence.isPresent() ? presence.sessionStart - serverOffsetMs : 0L;
        this.frozenElapsedMs = presence.frozenElapsedMs();
        this.focusSecondsToday = Math.max(0, focusSecondsToday);
    }

    /** Up to two upper-case initials from the display name ("Laia Martí" → "LM", "Pau" → "P"). */
    @NonNull
    public static String initialsOf(@NonNull String name) {
        StringBuilder out = new StringBuilder(2);
        for (String word : name.trim().split("\\s+")) {
            if (!word.isEmpty() && out.length() < 2) {
                out.appendCodePoint(word.codePointAt(0));
            }
        }
        return out.toString().toUpperCase(Locale.ROOT);
    }

    /** Firebase uid. */
    @NonNull public String getUid() { return uid; }

    /** Same as {@link #getUid()} (kept for the screens that read it before F4). */
    @NonNull public String getId() { return uid; }

    /** {@code @usuario} without the {@code @}. */
    @NonNull public String getHandle() { return handle; }

    @NonNull public String getName() { return name; }

    /** One or two letters for the avatar (html 01 uses two: "LM", html 04 one: "M" — first char). */
    @NonNull public String getInitials() { return initials; }

    /** Avatar palette index 0–7 (as {@code FriendCard.avatarColor}). */
    public int getAvatarColor() { return avatarColor; }

    @NonNull public Status getStatus() { return status; }

    /** Studying now: {@code focus}, {@code break} or {@code paused}, fresh (at most 4 h old). */
    public boolean isStudying() { return status == Status.STUDYING; }

    /** Paused ("En pausa"): still studying, with the time frozen at {@link #getFrozenElapsedMs()}. */
    public boolean isPaused() { return paused; }

    /** Start of the continuous run on this device's clock; 0 when away. */
    public long getSessionStartMs() { return sessionStartMs; }

    /** Frozen session time while paused; 0 otherwise. */
    public long getFrozenElapsedMs() { return frozenElapsedMs; }

    /**
     * Session time to show at {@code deviceNowMs} ({@code System.currentTimeMillis()}): live while
     * studying, frozen while paused, 0 when away; never negative.
     */
    public long getElapsedMs(long deviceNowMs) {
        if (!isStudying()) {
            return 0L;
        }
        return paused ? frozenElapsedMs : Math.max(0L, deviceNowMs - sessionStartMs);
    }

    /** Focus time today in seconds; only {@link MockSocialRepository} fills it (0 for real friends). */
    public long getFocusSecondsToday() { return focusSecondsToday; }

    public int getMinutesToday() { return (int) (focusSecondsToday / 60); }

    @NonNull
    @Override
    public String toString() {
        return "Friend{" + uid + ", " + name + ", " + status + (paused ? " (paused)" : "")
                + ", start=" + sessionStartMs + "}";
    }
}
