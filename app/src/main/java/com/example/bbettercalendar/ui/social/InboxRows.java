package com.example.bbettercalendar.ui.social;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.social.inbox.InboxItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * F5-u7: mapper puro (sin Android) de la bandeja de la campana. Convierte los avisos de {@code InboxRepository} en
 * filas ya ordenadas (la más nueva primero; un aviso aún sin hora del servidor cuenta como el más nuevo), con la
 * edad relativa, el acento «nuevo» de esta visita (d1) y a dónde lleva tocarla (d4). Sin contadores: solo
 * {@link #bellCount} decide si el punto de la campana se enciende.
 */
public final class InboxRows {

    private InboxRows() {
    }

    /** A dónde lleva tocar una fila. */
    public enum Destination {
        /** El perfil de quien lo envió (un ánimo, un empujón o una solicitud de quien ya es amigo). */
        PROFILE,
        /** Amigos, subido hasta las solicitudes (una solicitud de quien aún no es amigo). */
        FRIEND_REQUESTS,
        /** Nada que abrir (un ánimo de quien ya no es amigo). */
        NONE
    }

    /** Unidad de la edad relativa. */
    public enum AgeUnit {
        NOW, MINUTES, HOURS, DAYS
    }

    /** Una fila de la bandeja. */
    public static final class Row {
        @NonNull public final String id;
        @NonNull public final InboxItem.Type type;
        @NonNull public final String fromUid;
        @NonNull public final String fromHandle;
        @NonNull public final String fromName;
        public final int fromColor;
        @NonNull public final AgeUnit ageUnit;
        /** Cuántas {@link #ageUnit} (0 en NOW). */
        public final int ageValue;
        /** Sin leer al abrir la bandeja: lleva el acento de esta visita. */
        public final boolean isNew;
        @NonNull public final Destination destination;

        Row(@NonNull InboxItem item, @NonNull AgeUnit ageUnit, int ageValue, boolean isNew,
            @NonNull Destination destination) {
            this.id = item.id;
            this.type = item.type;
            this.fromUid = item.fromUid;
            this.fromHandle = item.fromHandle;
            this.fromName = item.fromName;
            this.fromColor = item.fromColor;
            this.ageUnit = ageUnit;
            this.ageValue = ageValue;
            this.isNew = isNew;
            this.destination = destination;
        }
    }

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    /** Más nuevo primero; sin hora del servidor (null) va el primero. Estable. */
    private static final Comparator<InboxItem> NEWEST_FIRST = new Comparator<InboxItem>() {
        @Override
        public int compare(InboxItem a, InboxItem b) {
            long ta = a.createdAt == null ? Long.MAX_VALUE : a.createdAt.getTime();
            long tb = b.createdAt == null ? Long.MAX_VALUE : b.createdAt.getTime();
            return Long.compare(tb, ta);
        }
    };

    /**
     * Las filas de la bandeja.
     *
     * @param friendUids uids de los amigos de ahora (decide el destino)
     * @param newIds     ids que estaban sin leer al abrir la bandeja (acento «nuevo»)
     */
    @NonNull
    public static List<Row> build(@NonNull List<InboxItem> items, @NonNull Set<String> friendUids,
                                  @NonNull Set<String> newIds, long nowMs) {
        List<InboxItem> sorted = new ArrayList<>(items);
        Collections.sort(sorted, NEWEST_FIRST);
        List<Row> rows = new ArrayList<>(sorted.size());
        for (InboxItem item : sorted) {
            long age = ageMillis(item.createdAt, nowMs);
            AgeUnit unit = ageUnit(age);
            rows.add(new Row(item, unit, ageValue(unit, age), newIds.contains(item.id),
                    destination(item.type, friendUids.contains(item.fromUid))));
        }
        return rows;
    }

    /** Tocar un ánimo o un empujón: el perfil si sigue siendo amigo. Una solicitud: el perfil o Solicitudes (d4). */
    @NonNull
    public static Destination destination(@NonNull InboxItem.Type type, boolean isFriend) {
        if (isFriend) {
            return Destination.PROFILE;
        }
        return type == InboxItem.Type.REQUEST ? Destination.FRIEND_REQUESTS : Destination.NONE;
    }

    /** Ids de los avisos sin leer (el acento «nuevo» de una visita, d1). */
    @NonNull
    public static Set<String> unreadIds(@NonNull List<InboxItem> items) {
        Set<String> ids = new HashSet<>();
        for (InboxItem item : items) {
            if (!item.read) {
                ids.add(item.id);
            }
        }
        return ids;
    }

    /**
     * Para el punto de la campana: avisos sin leer más solicitudes pendientes (null cuenta 0). Solo importa si es
     * mayor que 0; nunca se enseña un número.
     */
    public static int bellCount(@Nullable Integer pendingRequests, @Nullable Integer unreadInbox) {
        int pending = pendingRequests == null ? 0 : Math.max(0, pendingRequests);
        int unread = unreadInbox == null ? 0 : Math.max(0, unreadInbox);
        return pending + unread;
    }

    private static long ageMillis(@Nullable Date createdAt, long nowMs) {
        return createdAt == null ? 0L : Math.max(0L, nowMs - createdAt.getTime());
    }

    @NonNull
    static AgeUnit ageUnit(long ageMs) {
        if (ageMs < MINUTE) {
            return AgeUnit.NOW;
        }
        if (ageMs < HOUR) {
            return AgeUnit.MINUTES;
        }
        if (ageMs < DAY) {
            return AgeUnit.HOURS;
        }
        return AgeUnit.DAYS;
    }

    private static int ageValue(@NonNull AgeUnit unit, long ageMs) {
        switch (unit) {
            case MINUTES:
                return (int) (ageMs / MINUTE);
            case HOURS:
                return (int) (ageMs / HOUR);
            case DAYS:
                return (int) (ageMs / DAY);
            case NOW:
            default:
                return 0;
        }
    }
}
