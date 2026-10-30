package com.example.bbettercalendar.ui.focus;

import androidx.annotation.NonNull;

import com.example.bbettercalendar.social.Friend;
import com.example.bbettercalendar.social.SocialSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * F4-u5: pure mapper from the single social snapshot and a {@code now} to what the Focus
 * "Estudiando ahora" block draws (no Android, no clock of its own). The snapshot already orders the
 * friends (studying first, longest session first); this keeps that order and caps the grid.
 *
 * <p>Time: live ({@code now - sessionStart}) while studying, frozen while paused (label "En pausa"
 * instead of a ticking time), away friends dimmed. The crown sits on the crown uid whatever the
 * status.
 */
final class FocusFriendsUi {

    static final int MAX_CELLS = 8;
    static final float AWAY_ALPHA = 0.4f;

    /** What the cell's second line shows: the time, the paused label or the away label. */
    enum Line { TIME, PAUSED, AWAY }

    static final class Cell {
        @NonNull final String uid;
        @NonNull final String name;
        @NonNull final String initials;
        final int avatarColor;
        @NonNull final Line line;
        /** h:mm:ss (live, or frozen when paused); empty for away. */
        @NonNull final String timeText;
        final boolean crown;
        final float alpha;

        Cell(@NonNull String uid, @NonNull String name, @NonNull String initials, int avatarColor,
             @NonNull Line line, @NonNull String timeText, boolean crown, float alpha) {
            this.uid = uid;
            this.name = name;
            this.initials = initials;
            this.avatarColor = avatarColor;
            this.line = line;
            this.timeText = timeText;
            this.crown = crown;
            this.alpha = alpha;
        }

        boolean isAway() { return line == Line.AWAY; }
    }

    static final class Result {
        /** "studying / friends", e.g. "2 / 5". */
        @NonNull final String header;
        @NonNull final List<Cell> cells;
        /** No friends at all: the block shows the invite empty state. */
        final boolean empty;

        Result(@NonNull String header, @NonNull List<Cell> cells, boolean empty) {
            this.header = header;
            this.cells = cells;
            this.empty = empty;
        }
    }

    private FocusFriendsUi() { }

    @NonNull
    static Result map(@NonNull SocialSnapshot snapshot, long nowMs) {
        List<Friend> friends = snapshot.getFriends();
        String header = String.format(Locale.ROOT, "%d / %d",
                snapshot.getStudyingCount(), snapshot.getFriendCount());
        List<Cell> cells = new ArrayList<>();
        String crownUid = snapshot.getCrownUid();
        int shown = Math.min(MAX_CELLS, friends.size());
        for (int i = 0; i < shown; i++) {
            Friend f = friends.get(i);
            boolean crown = crownUid != null && crownUid.equals(f.getUid());
            String first = firstName(f.getName());
            if (!f.isStudying()) {
                cells.add(new Cell(f.getUid(), first, f.getInitials(), f.getAvatarColor(),
                        Line.AWAY, "", crown, AWAY_ALPHA));
            } else {
                cells.add(new Cell(f.getUid(), first, f.getInitials(), f.getAvatarColor(),
                        f.isPaused() ? Line.PAUSED : Line.TIME, formatTime(f.getElapsedMs(nowMs)),
                        crown, 1f));
            }
        }
        return new Result(header, Collections.unmodifiableList(cells), friends.isEmpty());
    }

    /** h:mm:ss, hours unpadded and unbounded ("1:05:09", "12:00:00"); negative counts as 0. */
    @NonNull
    static String formatTime(long elapsedMs) {
        long s = Math.max(0L, elapsedMs) / 1000L;
        return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
    }

    @NonNull
    static String firstName(@NonNull String name) {
        String t = name.trim();
        int sp = t.indexOf(' ');
        return sp < 0 ? t : t.substring(0, sp);
    }
}
