package com.example.bbettercalendar.social.friends;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * El contador diario de solicitudes salientes (F2-u5, D-028 §2): cuántas lleva hoy y cuál sería la
 * siguiente. Puro; repite en el cliente lo que exige {@code validRequestCounter} en las reglas:
 * {@code reqDay} es el día UTC ({@code floor(millis / 86 400 000)}), {@code reqCount} sube 1 respecto al
 * estado anterior o vuelve a 1 si cambió el día, y nunca pasa de {@link #DAILY_LIMIT}.
 *
 * <p>La hora del servidor manda: con el reloj del móvil desfasado justo a medianoche UTC, el lote se
 * deniega (PERMISSION_DENIED) aunque aquí saliera bien.
 */
public final class RequestQuota {

    /** Solicitudes salientes por día UTC. */
    public static final int DAILY_LIMIT = 20;
    static final long DAY_MS = 86_400_000L;

    /** El contador tras la próxima solicitud, o {@code allowed == false} si ya se llegó al tope. */
    public static final class Next {
        public final boolean allowed;
        public final long day;
        public final long count;

        Next(boolean allowed, long day, long count) {
            this.allowed = allowed;
            this.day = day;
            this.count = count;
        }
    }

    private RequestQuota() {
    }

    /** Día UTC desde epoch, como {@code math.floor(request.time.toMillis() / 86400000)} en las reglas. */
    public static long utcDay(long nowMillis) {
        return Math.floorDiv(nowMillis, DAY_MS);
    }

    /**
     * El contador siguiente a partir de {@code users/{uid}} ({@code reqDay}/{@code reqCount}, null si nunca
     * se envió nada). Mismo día: +1; otro día: 1; si ya van {@link #DAILY_LIMIT} hoy, no se permite.
     */
    @NonNull
    public static Next next(@Nullable Long reqDay, @Nullable Long reqCount, long nowMillis) {
        long today = utcDay(nowMillis);
        long sentToday = reqDay != null && reqDay == today && reqCount != null ? reqCount : 0L;
        if (sentToday >= DAILY_LIMIT) {
            return new Next(false, today, sentToday);
        }
        return new Next(true, today, sentToday + 1);
    }
}
