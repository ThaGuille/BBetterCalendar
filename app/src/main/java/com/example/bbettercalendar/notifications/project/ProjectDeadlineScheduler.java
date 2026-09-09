package com.example.bbettercalendar.notifications.project;

import android.app.AlarmManager;
import android.content.Context;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.notifications.event.AlarmReminderCore;
import com.example.bbettercalendar.notifications.event.EventReminderScheduler;
import com.example.bbettercalendar.projects.Project;

import dagger.hilt.android.qualifiers.ApplicationContext;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Segundo cliente de {@link AlarmReminderCore} (el primero es {@link EventReminderScheduler}):
 * avisos del soft deadline de un proyecto, spec project-deadlines-progress.
 *
 * Programado por alarma en "deadline menos N", nunca por sondeo periódico. Dos offsets fijos y
 * siempre activos ({3d, 1d}) — no hay UI por proyecto en esta fase, y una alarma por offset es
 * todo el presupuesto: nada repite ni escala.
 *
 * El offset de 3 días es exactamente la ventana de
 * {@code ProjectDeadlineState.APPROACHING_WINDOW_MILLIS}, así que la notificación salta en el
 * mismo instante en que el chip de la lista de proyectos se pone ámbar: un umbral, dos superficies.
 */
@Singleton
public class ProjectDeadlineScheduler {

    public static final String EXTRA_PROJECT_ID = "project_id";
    public static final String EXTRA_OFFSET_INDEX = "offset_index";

    /**
     * Offsets antes del deadline, emparejados por índice con {@link #BODY_RES_IDS}.
     *
     * <p>El orden es <strong>de mayor a menor</strong> y no es cosmético:
     * {@link ProjectDeadlineReceiver#shouldNotify} usa el offset del índice siguiente como suelo de
     * la ventana de cada alarma, así que insertar un offset fuera de orden haría que unas alarmas
     * se descartasen y otras notificasen con el texto de otro offset.
     */
    public static final long[] OFFSET_MILLIS = {
            3L * 24L * 60L * 60_000L,
            24L * 60L * 60_000L
    };

    private static final int[] BODY_RES_IDS = {
            R.string.notif_project_deadline_body_3d,
            R.string.notif_project_deadline_body_1d
    };

    // Ambos offsets siempre activos: el core recorre enabledOffsets/OFFSET_MILLIS en paralelo.
    private static final boolean[] ALL_ENABLED = { true, true };

    // Namespace de request codes DISJUNTO del de los recordatorios de entrada
    // (EventReminderScheduler.requestCodeFor = entryId * 10 + offsetIndex): los ids de project y
    // de calendarEntry arrancan ambos en 1, así que compartir el espacio de
    // PendingIntent.getBroadcast() haría que cancelar un recordatorio de tarea matara el aviso de
    // un proyecto. Techo: se solaparían a partir de entryId >= 50_000, muy por encima de lo real.
    private static final int REQUEST_CODE_BASE = 500_000;

    // Rango de ids de notificación propio, libre entre focus (50_001), usage (60_000/61_000) y los
    // recordatorios de entrada (100_000+); ver la tabla de particiones en notifications.md. Vive
    // aquí, junto al de request codes, para que los dos espacios de ids del aviso de deadline se
    // lean de un vistazo (y para que el test de particiones no tenga que cargar el receiver).
    private static final int NOTIFICATION_ID_BASE = 70_000;

    private static final AlarmReminderCore.RequestCodeNamespace NAMESPACE =
            ProjectDeadlineScheduler::requestCodeFor;

    private static final AlarmReminderCore.IntentPopulator EXTRAS =
            (intent, entityId, offsetIndex) -> {
                intent.putExtra(EXTRA_PROJECT_ID, entityId);
                intent.putExtra(EXTRA_OFFSET_INDEX, offsetIndex);
            };

    private final AlarmReminderCore core;

    @Inject
    public ProjectDeadlineScheduler(@ApplicationContext Context context, AlarmManager alarmManager) {
        this.core = new AlarmReminderCore(context, alarmManager, ProjectDeadlineReceiver.class, NAMESPACE);
    }

    public static int requestCodeFor(int projectId, int offsetIndex) {
        return REQUEST_CODE_BASE + projectId * 10 + offsetIndex;
    }

    public static int notificationIdFor(int projectId, int offsetIndex) {
        return NOTIFICATION_ID_BASE + projectId * 10 + offsetIndex;
    }

    public static int bodyResFor(int offsetIndex) {
        if (offsetIndex < 0 || offsetIndex >= BODY_RES_IDS.length) {
            return BODY_RES_IDS[0];
        }
        return BODY_RES_IDS[offsetIndex];
    }

    /**
     * Sólo los proyectos ACTIVE con deadline reciben alarmas: uno ya completado cuyo deadline
     * sigue en el futuro no debe dar la lata (decisión #11 de la propuesta). El core descarta por
     * su cuenta los offsets cuyo disparo ya pasó.
     */
    public void scheduleFor(Project project) {
        if (project == null) return;
        if (project.status != Project.STATUS_ACTIVE) return;
        if (project.softDeadlineMillis <= 0L) return;

        core.schedule(project.id, ALL_ENABLED, OFFSET_MILLIS, project.softDeadlineMillis, EXTRAS);
    }

    /** Toma el id (no el Project) porque también se cancela desde el borrado, cuando la fila ya no está. */
    public void cancelFor(int projectId) {
        core.cancel(projectId, OFFSET_MILLIS.length, EXTRAS);
    }
}
