package com.example.bbettercalendar.notifications;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * Preferencias de notificaciones del usuario (plan sistema-notificaciones). Viven en
 * SharedPreferences, no en {@code Configuration}: son flags y horas sueltas, y una columna nueva
 * obligaría a subir la versión de Room (regla #6). Lecturas y escrituras son diminutas
 * ({@code apply()} escribe en segundo plano), así que el hilo de UI puede llamarla.
 *
 * Las horas se guardan como minutos desde medianoche (0..1439), en hora local.
 *
 * El silencio de cada categoría lo decide también el canal del sistema: estos flags son el
 * interruptor de la app (se ve y se cambia desde la pantalla Notificaciones) y los emisores
 * comprueban ambos.
 */
public final class NotificationPrefs {

    private static final String FILE = "bb_notification_prefs";

    // Recordatorios de eventos / tareas: offset que viene marcado al crear una entrada nueva.
    // Índice en NotificationOffsets.OFFSET_MILLIS; NO_DEFAULT_OFFSET = ninguno marcado.
    public static final int NO_DEFAULT_OFFSET = -1;
    public static final int DEFAULT_REMINDER_OFFSET_INDEX = 1; // 10 min antes
    private static final String KEY_DEFAULT_OFFSET = "reminder_default_offset_index";

    // Pomodoro: aviso al terminar una concentración o un descanso.
    private static final String KEY_FOCUS_PHASE_END = "focus_phase_end";

    // Avisos diarios (nudges), cada uno con su interruptor y su hora.
    public static final String NUDGE_MORNING = "morning";       // «Tu día»: agenda de hoy
    public static final String NUDGE_STREAK = "streak";         // racha en juego
    public static final String NUDGE_EVENING_PLAN = "evening";  // planear mañana
    public static final String NUDGE_WEEKLY = "weekly";         // resumen semanal (domingo)

    public static final int DEFAULT_MORNING_MINUTES = 8 * 60 + 30;   // 08:30
    public static final int DEFAULT_STREAK_MINUTES = 20 * 60 + 30;   // 20:30
    public static final int DEFAULT_EVENING_MINUTES = 21 * 60;       // 21:00
    public static final int DEFAULT_WEEKLY_MINUTES = 12 * 60;        // domingo 12:00

    private final SharedPreferences prefs;

    private NotificationPrefs(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public static NotificationPrefs from(@NonNull Context context) {
        return new NotificationPrefs(context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE));
    }

    // ---- Recordatorios ----

    public int getDefaultReminderOffsetIndex() {
        return prefs.getInt(KEY_DEFAULT_OFFSET, DEFAULT_REMINDER_OFFSET_INDEX);
    }

    public void setDefaultReminderOffsetIndex(int index) {
        prefs.edit().putInt(KEY_DEFAULT_OFFSET, index).apply();
    }

    // ---- Pomodoro ----

    public boolean isFocusPhaseEndEnabled() {
        return prefs.getBoolean(KEY_FOCUS_PHASE_END, true);
    }

    public void setFocusPhaseEndEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_FOCUS_PHASE_END, enabled).apply();
    }

    // ---- Avisos diarios ----

    public boolean isNudgeEnabled(@NonNull String nudge) {
        return prefs.getBoolean("nudge_enabled_" + nudge, defaultEnabled(nudge));
    }

    public void setNudgeEnabled(@NonNull String nudge, boolean enabled) {
        prefs.edit().putBoolean("nudge_enabled_" + nudge, enabled).apply();
    }

    /** Hora local del aviso, en minutos desde medianoche. */
    public int getNudgeMinutes(@NonNull String nudge) {
        return prefs.getInt("nudge_minutes_" + nudge, defaultMinutes(nudge));
    }

    public void setNudgeMinutes(@NonNull String nudge, int minutesOfDay) {
        int clamped = Math.max(0, Math.min(24 * 60 - 1, minutesOfDay));
        prefs.edit().putInt("nudge_minutes_" + nudge, clamped).apply();
    }

    /** Planear mañana sale apagado: la evidencia (intenciones de implementación) es débil. */
    static boolean defaultEnabled(String nudge) {
        return !NUDGE_EVENING_PLAN.equals(nudge);
    }

    static int defaultMinutes(String nudge) {
        switch (nudge) {
            case NUDGE_MORNING: return DEFAULT_MORNING_MINUTES;
            case NUDGE_STREAK: return DEFAULT_STREAK_MINUTES;
            case NUDGE_EVENING_PLAN: return DEFAULT_EVENING_MINUTES;
            case NUDGE_WEEKLY: return DEFAULT_WEEKLY_MINUTES;
            default: return 12 * 60;
        }
    }
}
