package com.example.bbettercalendar.social;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Everything the social blocks render, in one immutable value: the ONE snapshot every screen uses
 * (F4-u4, 03 §10). Friends are display-ordered (02 §7): studying first, then paused, then away;
 * among studying (and among paused) the longest session first (earlier session start); then by name.
 * The crown is RankingRepository's {@code crownUid} (F3: most capped minutes today), shown on that
 * friend whatever their status. {@link #EMPTY} is the "no data" value (no session, flag off).
 */
public final class SocialSnapshot {

    public static final SocialSnapshot EMPTY =
            new SocialSnapshot(Collections.<Friend>emptyList(), null, null);

    private static final Comparator<Friend> DISPLAY_ORDER = new Comparator<Friend>() {
        @Override
        public int compare(Friend a, Friend b) {
            if (a.isStudying() != b.isStudying()) {
                return a.isStudying() ? -1 : 1;
            }
            if (a.isStudying()) {
                if (a.isPaused() != b.isPaused()) {
                    return a.isPaused() ? 1 : -1; // en pausa detrás de quien estudia
                }
                int bySession = Long.compare(a.getSessionStartMs(), b.getSessionStartMs());
                if (bySession != 0) {
                    return bySession;
                }
            }
            int byName = String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName());
            return byName != 0 ? byName : a.getUid().compareTo(b.getUid());
        }
    };

    @NonNull private final List<Friend> friends;
    @Nullable private final SocialRank userRank;
    @Nullable private final String crownUid;

    public SocialSnapshot(@NonNull List<Friend> friends, @Nullable SocialRank userRank,
                          @Nullable String crownUid) {
        List<Friend> sorted = new ArrayList<>(friends);
        Collections.sort(sorted, DISPLAY_ORDER);
        this.friends = Collections.unmodifiableList(sorted);
        this.userRank = userRank;
        this.crownUid = crownUid;
    }

    /** All friends, display-ordered (studying first). */
    @NonNull public List<Friend> getFriends() { return friends; }

    /** Only the friends studying right now (paused included), display-ordered. */
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

    /** Uid of the friend with the crown (RankingRepository), or null; may be an away friend. */
    @Nullable public String getCrownUid() { return crownUid; }

    /** The friend wearing the crown ({@link #getCrownUid()}), or null if none is in the list. */
    @Nullable
    public Friend getTopFriend() {
        if (crownUid == null) {
            return null;
        }
        for (Friend f : friends) {
            if (f.getUid().equals(crownUid)) return f;
        }
        return null;
    }

    /** The user's rank among friends, or null when there is no social data. */
    @Nullable public SocialRank getUserRank() { return userRank; }

    public boolean isEmpty() { return friends.isEmpty(); }
}
