package com.example.bbettercalendar.social;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Everything the social blocks render, in one immutable value.
 * Friends are ordered for display: studying first, then by focus time today (desc), then by name.
 * {@link #EMPTY} is what {@link EmptySocialRepository} publishes when the feature flag is off.
 */
public final class SocialSnapshot {

    public static final SocialSnapshot EMPTY =
            new SocialSnapshot(Collections.<Friend>emptyList(), null);

    private static final Comparator<Friend> DISPLAY_ORDER = new Comparator<Friend>() {
        @Override
        public int compare(Friend a, Friend b) {
            if (a.isStudying() != b.isStudying()) {
                return a.isStudying() ? -1 : 1;
            }
            int bySeconds = Long.compare(b.getFocusSecondsToday(), a.getFocusSecondsToday());
            return bySeconds != 0 ? bySeconds : a.getName().compareTo(b.getName());
        }
    };

    @NonNull private final List<Friend> friends;
    @Nullable private final SocialRank userRank;

    public SocialSnapshot(@NonNull List<Friend> friends, @Nullable SocialRank userRank) {
        List<Friend> sorted = new ArrayList<>(friends);
        Collections.sort(sorted, DISPLAY_ORDER);
        this.friends = Collections.unmodifiableList(sorted);
        this.userRank = userRank;
    }

    /** All friends, display-ordered (studying first). */
    @NonNull public List<Friend> getFriends() { return friends; }

    /** Only the friends studying right now, display-ordered. */
    @NonNull
    public List<Friend> getStudying() {
        List<Friend> out = new ArrayList<>();
        for (Friend f : friends) {
            if (f.isStudying()) out.add(f);
        }
        return out;
    }

    public int getStudyingCount() { return getStudying().size(); }

    public int getFriendCount() { return friends.size(); }

    /** Friend with the most focus time today (html 01 crown badge), or null if there are none. */
    @Nullable
    public Friend getTopFriend() {
        Friend top = null;
        for (Friend f : friends) {
            if (top == null || f.getFocusSecondsToday() > top.getFocusSecondsToday()) top = f;
        }
        return top;
    }

    /** The user's rank among friends, or null when there is no social data. */
    @Nullable public SocialRank getUserRank() { return userRank; }

    public boolean isEmpty() { return friends.isEmpty(); }
}
