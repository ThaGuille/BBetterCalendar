package com.example.bbettercalendar.social.block;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Puro (L-u9): la respuesta de {@code blockUser} a {@link BlockResult} y los documentos de
 * {@code blocks/{me}/blocked} a {@link BlockedCard}. {@code {status: 'blocked'}} → BLOCKED; cualquier otra
 * cosa → ERROR. Un documento ilegible o malformado se descarta (null) y nunca lanza.
 */
public final class BlockMapping {

    public static final String STATUS = "status";
    public static final String SINCE = "since";
    public static final String HANDLE = "handle";
    public static final String DISPLAY_NAME = "displayName";
    public static final String AVATAR_COLOR = "avatarColor";

    private BlockMapping() {
    }

    @NonNull
    public static BlockResult fromData(@Nullable Map<String, Object> data) {
        Object status = data == null ? null : data.get(STATUS);
        return "blocked".equals(status) ? BlockResult.BLOCKED : BlockResult.ERROR;
    }

    /** {@code blocks/{me}/blocked/{id}} → tarjeta; null si no existe o está malformado. */
    @Nullable
    public static BlockedCard card(@NonNull BlockBackend.Doc doc) {
        Map<String, Object> d = doc.data;
        if (d == null) {
            return null;
        }
        try {
            Object handle = d.get(HANDLE);
            Object name = d.get(DISPLAY_NAME);
            Object color = d.get(AVATAR_COLOR);
            Object since = d.get(SINCE);
            if (!(handle instanceof String) || !(name instanceof String) || !(color instanceof Number)) {
                return null;
            }
            if (since != null && !(since instanceof Timestamp) && !(since instanceof Date)) {
                return null;
            }
            return new BlockedCard(doc.id, (String) handle, (String) name, ((Number) color).intValue(),
                    date(since));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Las tarjetas legibles, la más nueva primero (hora pendiente = la más nueva; empate por uid). */
    @NonNull
    public static List<BlockedCard> cards(@NonNull List<BlockBackend.Doc> docs) {
        List<BlockedCard> out = new ArrayList<>();
        for (BlockBackend.Doc doc : docs) {
            BlockedCard card = card(doc);
            if (card != null) {
                out.add(card);
            }
        }
        Collections.sort(out, NEWEST_FIRST);
        return out;
    }

    private static final Comparator<BlockedCard> NEWEST_FIRST = (a, b) -> {
        long ta = a.since == null ? Long.MAX_VALUE : a.since.getTime();
        long tb = b.since == null ? Long.MAX_VALUE : b.since.getTime();
        if (ta != tb) {
            return ta > tb ? -1 : 1;
        }
        return a.uid.compareTo(b.uid);
    };

    @Nullable
    private static Date date(@Nullable Object value) {
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toDate();
        }
        return value instanceof Date ? (Date) value : null;
    }
}
