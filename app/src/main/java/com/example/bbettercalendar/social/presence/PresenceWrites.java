package com.example.bbettercalendar.social.presence;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Los mapas de {@code status/{uid}} que escribe {@link PresencePublisher} (F4-u3), exactamente con las claves
 * del contrato (03 §5, D-033) y ninguna más:
 * <ul>
 *   <li>{@code focus} / {@code break}: {@code state}, {@code since}, {@code sessionStart};</li>
 *   <li>{@code paused}: {@code state}, {@code since}, {@code sessionStart}, {@code pausedAt};</li>
 *   <li>{@code idle}: {@code state} y {@code since} (sin {@code sessionStart} ni {@code pausedAt}).</li>
 * </ul>
 * {@code since} es el centinela que da el backend ({@code ServerValue.TIMESTAMP} en la app; las reglas exigen
 * {@code since == now}). {@code sessionStart} y {@code pausedAt} pasan del reloj del móvil a hora del servidor
 * sumando {@code .info/serverTimeOffset}. El lector (u4, {@code PresenceMapping}) lleva sus propias constantes
 * de claves; cada lado las fija con su test contra el contrato.
 */
public final class PresenceWrites {

    /** Ruta raíz de la presencia en la Realtime Database. */
    public static final String STATUS = "status";

    public static final String STATE = "state";
    public static final String SINCE = "since";
    public static final String SESSION_START = "sessionStart";
    public static final String PAUSED_AT = "pausedAt";

    public static final String FOCUS = "focus";
    public static final String BREAK = "break";
    public static final String PAUSED = "paused";
    public static final String IDLE = "idle";

    private PresenceWrites() { }

    /** Mapa de {@code idle}: lo que se registra en {@code onDisconnect} y lo que se escribe al apagar. */
    @NonNull
    public static Map<String, Object> idle(@NonNull Object since) {
        Map<String, Object> map = new HashMap<>();
        map.put(STATE, IDLE);
        map.put(SINCE, since);
        return map;
    }

    /**
     * Mapa de {@code presence} en hora del servidor.
     *
     * @param serverOffsetMs {@code .info/serverTimeOffset} (hora del servidor menos la del móvil)
     * @param since          el centinela de hora del servidor
     */
    @NonNull
    public static Map<String, Object> toMap(@NonNull OwnPresence presence, long serverOffsetMs,
                                            @NonNull Object since) {
        if (presence.kind == OwnPresence.Kind.IDLE) {
            return idle(since);
        }
        Map<String, Object> map = new HashMap<>();
        map.put(STATE, stateValue(presence.kind));
        map.put(SINCE, since);
        long sessionStart = Math.max(1L, presence.sessionStart + serverOffsetMs); // las reglas: > 0
        map.put(SESSION_START, sessionStart);
        if (presence.kind == OwnPresence.Kind.PAUSED) {
            map.put(PAUSED_AT, Math.max(sessionStart, presence.pausedAt + serverOffsetMs));
        }
        return map;
    }

    @NonNull
    static String stateValue(@NonNull OwnPresence.Kind kind) {
        switch (kind) {
            case FOCUS:
                return FOCUS;
            case BREAK:
                return BREAK;
            case PAUSED:
                return PAUSED;
            case IDLE:
            default:
                return IDLE;
        }
    }
}
