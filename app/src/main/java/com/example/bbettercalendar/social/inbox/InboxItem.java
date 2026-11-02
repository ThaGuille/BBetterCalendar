package com.example.bbettercalendar.social.inbox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;

/**
 * Un aviso de {@code users/{me}/inbox/{id}} (F5-u5; 03 §5, D-035): ánimo, empujón o solicitud recibidos. Lleva la
 * tarjeta de quien lo envía (la copia la Function, porque quien envía una solicitud aún no es amigo y
 * {@code profiles/} es solo de amigos). Inmutable.
 */
public final class InboxItem {

    public enum Type {
        REQUEST("request"),
        CHEER("cheer"),
        NUDGE("nudge");

        @NonNull
        public final String wire;

        Type(@NonNull String wire) {
            this.wire = wire;
        }

        /** null si no es un tipo del contrato. */
        @Nullable
        public static Type fromWire(@Nullable String wire) {
            for (Type t : values()) {
                if (t.wire.equals(wire)) {
                    return t;
                }
            }
            return null;
        }
    }

    @NonNull
    public final String id;
    @NonNull
    public final Type type;
    @NonNull
    public final String fromUid;
    @NonNull
    public final String fromHandle;
    @NonNull
    public final String fromName;
    /** Color del avatar, 0-7. */
    public final int fromColor;
    /** null mientras la hora del servidor aún no llega (cuenta como la más nueva). */
    @Nullable
    public final Date createdAt;
    public final boolean read;

    public InboxItem(@NonNull String id, @NonNull Type type, @NonNull String fromUid, @NonNull String fromHandle,
                     @NonNull String fromName, int fromColor, @Nullable Date createdAt, boolean read) {
        this.id = id;
        this.type = type;
        this.fromUid = fromUid;
        this.fromHandle = fromHandle;
        this.fromName = fromName;
        this.fromColor = fromColor;
        this.createdAt = createdAt;
        this.read = read;
    }
}
