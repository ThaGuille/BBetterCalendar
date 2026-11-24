package com.example.bbettercalendar.notifications.task;

/**
 * Espacios de ids del aviso de empezar. Todos suman entryId (por 10 los de alarma, que llevan indice
 * de offset); no chocan con los de NotificationIds mientras entryId sea menor que 100_000.
 *   request codes: 4_000_000 alarma, 5_000_000 alarma pospuesta, 6_000_000 accion En 10 min,
 *                  7_000_000 accion Empezar y toque de la notificacion
 *   notificacion:  8_000_000 + entryId
 */
public final class TaskStartIds {
    public static final int REQ_ALARM_BASE = 4_000_000;
    public static final int REQ_SNOOZED_ALARM_BASE = 5_000_000;
    public static final int REQ_ACTION_SNOOZE_BASE = 6_000_000;
    public static final int REQ_ACTION_START_BASE = 7_000_000;
    public static final int NOTIFICATION_BASE = 8_000_000;

    public static int notificationId(int entryId) {
        return NOTIFICATION_BASE + entryId;
    }

    private TaskStartIds() { }
}
