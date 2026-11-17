package com.example.bbettercalendar.notifications;

/**
 * Registro de los ids fijos de notificación y de los request codes de PendingIntent que añadió el
 * plan sistema-notificaciones. Los rangos antiguos siguen definidos junto a su emisor (ver la tabla
 * de particiones en .claude/docs/systems/notifications.md); los nuevos viven aquí para que no
 * haya que buscarlos.
 *
 * Ids de notificación ocupados (resumen):
 *   40_000..49_999  amigos (FriendsPushHandler)
 *   50_001 / 50_002 fallo / tarea completada (focus)
 *   50_003          fin de fase del pomodoro (este plan)
 *   60_000 / 61_000 + hash  límites de apps
 *   70_000 + projectId*10 + i  deadlines de proyecto
 *   80_001..80_019  avisos diarios y resumen semanal (este plan)
 *   100_000 + entryId*10 + i  recordatorios de eventos / tareas
 *
 * Request codes ocupados (PendingIntent.getBroadcast / getActivity):
 *   entryId*10 + i              alarmas de recordatorio de entrada
 *   500_000 + projectId*10 + i  alarmas de deadline
 *   900_001..900_099            alarmas fijas de este plan (pomodoro, avisos diarios)
 *   1_000_000 + entryId*10 + i  acción «Hecho» de un recordatorio
 *   2_000_000 + entryId*10 + i  acción «Posponer» de un recordatorio
 *   3_000_000 + entryId*10 + i  alarma pospuesta de un recordatorio
 *
 * Techo: los espacios son disjuntos por desplazamiento, no por construcción. entryId*10 + i
 * alcanza 500_000 (deadlines) con entryId = 50_000, 900_001 con 90_000 y 1_000_000 con 100_000.
 * ReminderNamespaceTest / ReminderActionCodesTest lo fijan hasta ID_CEILING = 2_000; si alguna vez
 * hay decenas de miles de entradas, hay que rehacer las bases.
 */
public final class NotificationIds {

    // ---- Ids de notificación ----
    public static final int FOCUS_PHASE_END = 50_003;

    public static final int NUDGE_MORNING = 80_001;
    public static final int NUDGE_STREAK = 80_002;
    public static final int NUDGE_EVENING_PLAN = 80_003;
    public static final int WEEKLY_SUMMARY = 80_010;

    // ---- Request codes fijos ----
    public static final int REQ_FOCUS_PHASE_END = 900_001;
    public static final int REQ_FOCUS_PHASE_ACTION = 900_002;
    public static final int REQ_NUDGE_MORNING = 900_011;
    public static final int REQ_NUDGE_STREAK = 900_012;
    public static final int REQ_NUDGE_EVENING_PLAN = 900_013;
    public static final int REQ_WEEKLY_SUMMARY = 900_020;

    // ---- Bases por entrada (sumar entryId * 10 + offsetIndex) ----
    public static final int REQ_ENTRY_ACTION_DONE_BASE = 1_000_000;
    public static final int REQ_ENTRY_ACTION_SNOOZE_BASE = 2_000_000;
    public static final int REQ_ENTRY_SNOOZED_ALARM_BASE = 3_000_000;

    private NotificationIds() {}
}
