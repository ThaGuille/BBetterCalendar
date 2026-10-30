package com.example.bbettercalendar.social.presence;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

/**
 * Lectura pura de {@code status/{uid}} (F4-u4; contrato de 03 §5, semántica de 02 §7 y D-033): del mapa crudo de
 * la Realtime Database (o null: no existe, ilegible o {@code PERMISSION_DENIED}) y la hora del servidor estimada,
 * la {@link Presence} que ve quien mira.
 *
 * <ul>
 *   <li>{@code focus} y {@code break} con {@code sessionStart > 0} → {@link Presence.State#STUDYING} (el descanso
 *       se pinta como estudiando).</li>
 *   <li>{@code paused} con {@code sessionStart > 0} y {@code pausedAt >= sessionStart} →
 *       {@link Presence.State#PAUSED} con el tiempo congelado; sin {@code pausedAt} o con {@code pausedAt} antes del
 *       inicio, ilegible → {@link Presence.State#AWAY}.</li>
 *   <li>{@code idle}, un estado desconocido, una clave que falta o con otro tipo → {@code AWAY}.</li>
 *   <li>Regla de 4 h: si {@code serverNow − since} pasa de {@link #MAX_AGE_MS} → {@code AWAY}; justo en la línea
 *       sigue fresco («a lo sumo 4 h»).</li>
 * </ul>
 *
 * <p>Las claves son las de este lado del contrato (el escritor, {@code PresenceWrites} de u3, lleva las suyas);
 * {@code PresenceMappingTest} las fija contra 03 §5.
 */
public final class PresenceMapping {

    static final String STATE = "state";
    static final String SINCE = "since";
    static final String SESSION_START = "sessionStart";
    static final String PAUSED_AT = "pausedAt";

    static final String FOCUS = "focus";
    static final String BREAK = "break";
    static final String PAUSED = "paused";
    static final String IDLE = "idle";

    /** Un estado sin refrescar durante más de esto se ve ausente (02 §7, 03 §8 punto 4). */
    public static final long MAX_AGE_MS = 4L * 60 * 60 * 1000;

    private PresenceMapping() {
    }

    /** La presencia que se ve de {@code raw} con la hora del servidor {@code serverNow}. */
    @NonNull
    public static Presence fromMap(@Nullable Map<String, ?> raw, long serverNow) {
        if (raw == null) {
            return Presence.AWAY;
        }
        Object state = raw.get(STATE);
        Long since = asLong(raw.get(SINCE));
        if (!(state instanceof String) || since == null) {
            return Presence.AWAY;
        }
        if (serverNow - since > MAX_AGE_MS) {
            return Presence.AWAY; // olvidado o colgado: no se ha refrescado en 4 h
        }
        Long sessionStart = asLong(raw.get(SESSION_START));
        switch ((String) state) {
            case FOCUS:
            case BREAK:
                return sessionStart != null && sessionStart > 0
                        ? Presence.studying(sessionStart, since) : Presence.AWAY;
            case PAUSED:
                Long pausedAt = asLong(raw.get(PAUSED_AT));
                return sessionStart != null && sessionStart > 0 && pausedAt != null && pausedAt >= sessionStart
                        ? Presence.paused(sessionStart, pausedAt, since) : Presence.AWAY;
            default:
                return Presence.AWAY; // idle o desconocido
        }
    }

    /** Los números de la RTDB llegan como {@code Long} (o {@code Double} si alguien escribió decimales). */
    @Nullable
    private static Long asLong(@Nullable Object value) {
        if (value instanceof Long || value instanceof Integer) {
            return ((Number) value).longValue();
        }
        if (value instanceof Double) {
            double d = (Double) value;
            return Double.isNaN(d) || Double.isInfinite(d) ? null : (long) d;
        }
        return null;
    }
}
