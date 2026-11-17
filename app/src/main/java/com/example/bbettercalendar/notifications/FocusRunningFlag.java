package com.example.bbettercalendar.notifications;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * «Hay una concentración en marcha» visto desde fuera de la sesión (receivers de avisos diarios y
 * de amigos), que no pueden leer el {@code FocusSessionViewModel}. El único escritor es
 * {@code FocusSessionViewModel}, en cada transición (como {@code FocusBlockState}).
 *
 * Guarda el instante de reloj (wall clock) en que acaba la concentración, no un booleano: si el
 * proceso muere con la sesión viva nadie lo pondría a false, y un instante caduca solo. Un aviso
 * que llega mientras {@link #isRunning} es cierto se aplaza o sale en silencio (la investigación
 * de interrupciones: no cortar una concentración).
 */
public final class FocusRunningFlag {

    private static final String FILE = "bb_focus_running";
    private static final String KEY_ENDS_AT = "focus_ends_at_wall";
    /** Margen sobre el final previsto (pausas cortas, reloj). */
    private static final long SLACK_MILLIS = 60_000L;

    private FocusRunningFlag() {}

    /** Concentración corriendo (no descanso, no pausa) que acaba en {@code endsAtWallMillis}. */
    public static void setRunning(@NonNull Context context, long endsAtWallMillis) {
        prefs(context).edit().putLong(KEY_ENDS_AT, endsAtWallMillis).apply();
    }

    public static void clear(@NonNull Context context) {
        prefs(context).edit().remove(KEY_ENDS_AT).apply();
    }

    public static boolean isRunning(@NonNull Context context) {
        return isRunning(prefs(context).getLong(KEY_ENDS_AT, 0L), System.currentTimeMillis());
    }

    /** Fin previsto de la concentración en curso, o 0 si no hay. */
    public static long endsAtWallMillis(@NonNull Context context) {
        long endsAt = prefs(context).getLong(KEY_ENDS_AT, 0L);
        return isRunning(endsAt, System.currentTimeMillis()) ? endsAt : 0L;
    }

    static boolean isRunning(long endsAtWallMillis, long now) {
        return endsAtWallMillis > 0L && now < endsAtWallMillis + SLACK_MILLIS;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
