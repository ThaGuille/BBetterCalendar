package com.example.bbettercalendar.ui.social;

import androidx.annotation.NonNull;

import com.example.bbettercalendar.social.Friend;
import com.example.bbettercalendar.social.SocialSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * F4-u6: "who is studying" as Home and Cycle complete show it, computed from the ONE social snapshot.
 * Pure (no Android): the studying count (a paused friend is present, so it counts, D-033 / replan r7k2),
 * the friends to draw as avatars (display order, at most {@code maxShown}), their first names and the
 * "and N more" remainder. Home draws {@link #getShownFirstNames()} plus {@link #getMoreCount()}; the
 * Cycle strip reads {@link #getLeadName()} and {@link #getOthersCount()} ("Laia and 3 others").
 */
public final class StudyingSummary {

    private final int friendCount;
    @NonNull private final List<Friend> studying;
    @NonNull private final List<Friend> shown;

    private StudyingSummary(int friendCount, @NonNull List<Friend> studying, @NonNull List<Friend> shown) {
        this.friendCount = friendCount;
        this.studying = studying;
        this.shown = shown;
    }

    /** @param maxShown how many avatars fit (negative counts as 0) */
    @NonNull
    public static StudyingSummary of(@NonNull SocialSnapshot snapshot, int maxShown) {
        List<Friend> studying = snapshot.getStudying();
        int n = Math.max(0, Math.min(maxShown, studying.size()));
        return new StudyingSummary(snapshot.getFriendCount(),
                Collections.unmodifiableList(studying),
                Collections.unmodifiableList(new ArrayList<>(studying.subList(0, n))));
    }

    /** First word of a display name: "Laia Martí" gives "Laia"; a blank name stays as it is. */
    @NonNull
    public static String firstName(@NonNull String name) {
        String trimmed = name.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    /** Friends studying now, paused ones included. */
    public int getStudyingCount() { return studying.size(); }

    /** All the friends, studying or not (0 means "invite someone"). */
    public int getFriendCount() { return friendCount; }

    public boolean hasFriends() { return friendCount > 0; }

    public boolean isNobodyStudying() { return studying.isEmpty(); }

    /** The friends to draw as avatars, in display order. */
    @NonNull public List<Friend> getShown() { return shown; }

    @NonNull
    public List<String> getShownFirstNames() {
        List<String> out = new ArrayList<>(shown.size());
        for (Friend f : shown) {
            out.add(firstName(f.getName()));
        }
        return out;
    }

    /** Studying friends not drawn ("+N"). */
    public int getMoreCount() { return studying.size() - shown.size(); }

    /** First name of the first studying friend, or "" when nobody studies. */
    @NonNull
    public String getLeadName() {
        return studying.isEmpty() ? "" : firstName(studying.get(0).getName());
    }

    /** Studying friends besides the lead ("Laia and N others"). */
    public int getOthersCount() { return Math.max(0, studying.size() - 1); }
}
