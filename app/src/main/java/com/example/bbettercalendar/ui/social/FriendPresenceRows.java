package com.example.bbettercalendar.ui.social;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.social.Friend;
import com.example.bbettercalendar.social.SocialSnapshot;
import com.example.bbettercalendar.social.friends.FriendCard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * F4-u7: mapper puro (sin Android) de la sección Amigos con presencia (02 §9 «primero quien estudia»).
 * Une la lista de {@code FriendsRepository} (las tarjetas) con la instantánea social única (quién estudia) y
 * devuelve las filas ya ordenadas con la semántica del Viewer (02 §7): primero quien estudia (la pausa cuenta),
 * entre ellos la sesión más larga primero (menor inicio), después por nombre. Un amigo que no está en la
 * instantánea (aún sin estado, sin permiso, instantánea vacía) cuenta como ausente. El descanso no se
 * distingue (Q-04): ya llega como «estudiando».
 */
public final class FriendPresenceRows {

    private FriendPresenceRows() {
    }

    /** Un amigo con su presencia. Los tiempos van en el reloj de este móvil. */
    public static final class Entry {
        @NonNull
        public final FriendCard card;
        public final boolean studying;
        public final boolean paused;
        public final long sessionStartMs;
        public final long frozenElapsedMs;

        Entry(@NonNull FriendCard card, boolean studying, boolean paused, long sessionStartMs,
              long frozenElapsedMs) {
            this.card = card;
            this.studying = studying;
            this.paused = paused;
            this.sessionStartMs = sessionStartMs;
            this.frozenElapsedMs = frozenElapsedMs;
        }

        /** Tiempo de la sesión a {@code deviceNowMs}: en vivo, congelado en pausa, 0 si está ausente. */
        public long elapsedMs(long deviceNowMs) {
            if (!studying) {
                return 0L;
            }
            return paused ? frozenElapsedMs : Math.max(0L, deviceNowMs - sessionStartMs);
        }
    }

    private static final Comparator<Entry> ORDER = new Comparator<Entry>() {
        @Override
        public int compare(Entry a, Entry b) {
            if (a.studying != b.studying) {
                return a.studying ? -1 : 1;
            }
            if (a.studying) {
                int bySession = Long.compare(a.sessionStartMs, b.sessionStartMs);
                if (bySession != 0) {
                    return bySession;
                }
            }
            int byName = String.CASE_INSENSITIVE_ORDER.compare(a.card.displayName, b.card.displayName);
            return byName != 0 ? byName : a.card.uid.compareTo(b.card.uid);
        }
    };

    /**
     * @param friends  las tarjetas de {@code FriendsRepository} (orden cualquiera)
     * @param snapshot la instantánea social, o null si aún no ha llegado
     */
    @NonNull
    public static List<Entry> from(@NonNull List<FriendCard> friends, @Nullable SocialSnapshot snapshot) {
        Map<String, Friend> byUid = new HashMap<>();
        if (snapshot != null) {
            for (Friend f : snapshot.getFriends()) {
                byUid.put(f.getUid(), f);
            }
        }
        List<Entry> out = new ArrayList<>(friends.size());
        for (FriendCard card : friends) {
            Friend f = byUid.get(card.uid);
            if (f != null && f.isStudying()) {
                out.add(new Entry(card, true, f.isPaused(), f.getSessionStartMs(), f.getFrozenElapsedMs()));
            } else {
                out.add(new Entry(card, false, false, 0L, 0L));
            }
        }
        Collections.sort(out, ORDER);
        return out;
    }

    /** {@code h:mm} (minutos enteros, sin segundos): 0 → «0:00», 3 725 000 ms → «1:02». */
    @NonNull
    public static String formatElapsed(long elapsedMs) {
        long minutes = Math.max(0L, elapsedMs) / 60_000L;
        return String.format(Locale.ROOT, "%d:%02d", minutes / 60, minutes % 60);
    }
}
