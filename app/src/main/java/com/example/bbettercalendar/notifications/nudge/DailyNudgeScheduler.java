package com.example.bbettercalendar.notifications.nudge;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * Programa los avisos diarios (Tu día, racha en juego, planear mañana) y el resumen semanal
 * (plan sistema-notificaciones). Punto único de reprogramación: lo llaman el arranque del proceso
 * ({@code DBMigration.onCreate}), {@code BootReceiver} (arranque, actualización de la app, cambio
 * de hora o zona) y la pantalla Notificaciones tras cambiar una preferencia.
 *
 * ANDAMIAJE: la implementación llega con el motor de avisos diarios; hasta entonces es un no-op
 * para que los demás trabajos puedan llamarlo ya.
 */
public final class DailyNudgeScheduler {

    private DailyNudgeScheduler() {}

    /** (Re)programa todos los avisos diarios y el semanal según {@code NotificationPrefs}. Idempotente. */
    public static void rescheduleAll(@NonNull Context context) {
        // Implementado por el motor de avisos diarios.
    }
}
