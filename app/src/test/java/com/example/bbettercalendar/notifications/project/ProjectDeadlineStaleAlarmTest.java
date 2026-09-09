package com.example.bbettercalendar.notifications.project;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.projects.Project;

import org.junit.Test;

/**
 * La guarda de alarma obsoleta de {@link ProjectDeadlineReceiver#shouldNotify} (spec
 * project-deadlines-progress). Una alarma se programó en el pasado, así que cuando llega el
 * proyecto puede haberse completado, borrado o movido su deadline: el receiver re-lee la fila y
 * decide con esta función pura, que es lo que se prueba aquí.
 */
public class ProjectDeadlineStaleAlarmTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final long DAY = 24L * 60 * 60_000L;
    private static final long HOUR = 60L * 60_000L;

    private static final int OFFSET_3D = 0;
    private static final int OFFSET_1D = 1;

    private static Project active(long deadline) {
        Project p = new Project();
        p.id = 1;
        p.name = "P";
        p.status = Project.STATUS_ACTIVE;
        p.softDeadlineMillis = deadline;
        return p;
    }

    @Test
    public void missingProject_doesNotNotify() {
        assertFalse(ProjectDeadlineReceiver.shouldNotify(null, OFFSET_3D, NOW));
    }

    @Test
    public void alarmOnTime_notifies() {
        assertTrue(ProjectDeadlineReceiver.shouldNotify(active(NOW + 3 * DAY), OFFSET_3D, NOW));
        assertTrue(ProjectDeadlineReceiver.shouldNotify(active(NOW + DAY), OFFSET_1D, NOW));
    }

    @Test
    public void completedProject_doesNotNotify() {
        Project p = active(NOW + 3 * DAY);
        p.status = Project.STATUS_COMPLETED;
        assertFalse(ProjectDeadlineReceiver.shouldNotify(p, OFFSET_3D, NOW));
    }

    @Test
    public void archivedProject_doesNotNotify() {
        Project p = active(NOW + 3 * DAY);
        p.status = Project.STATUS_ARCHIVED;
        assertFalse(ProjectDeadlineReceiver.shouldNotify(p, OFFSET_3D, NOW));
    }

    @Test
    public void deadlinePushedLater_dropsTheOldAlarm() {
        // La alarma se armó para "3 días antes", pero entre medias el deadline se movió un mes:
        // notificar ahora sería un aviso a destiempo.
        assertFalse(ProjectDeadlineReceiver.shouldNotify(active(NOW + 30 * DAY), OFFSET_3D, NOW));
    }

    @Test
    public void alreadyPassedDeadline_doesNotNotify() {
        assertFalse(ProjectDeadlineReceiver.shouldNotify(active(NOW - 1), OFFSET_3D, NOW));
        assertFalse(ProjectDeadlineReceiver.shouldNotify(active(NOW), OFFSET_3D, NOW));
    }

    @Test
    public void noDeadline_doesNotNotify() {
        assertFalse(ProjectDeadlineReceiver.shouldNotify(active(0L), OFFSET_3D, NOW));
    }

    @Test
    public void lateFiringAlarm_stillNotifies() {
        // Una alarma inexacta (fallback cuando el dispositivo deniega las exactas) llega tarde:
        // queda MENOS tiempo que el offset, no más. Mientras no invada la banda del offset
        // siguiente (1 día), eso no la hace obsoleta.
        assertTrue(ProjectDeadlineReceiver.shouldNotify(active(NOW + 2 * DAY), OFFSET_3D, NOW));
        assertTrue(ProjectDeadlineReceiver.shouldNotify(active(NOW + DAY + 1), OFFSET_3D, NOW));
    }

    @Test
    public void grosslyLateAlarm_dropsInsteadOfUsingTheWrongBody() {
        // El SO difiere la alarma de 3 días hasta que faltan 2 horas. El cuerpo lo elige el índice
        // de la alarma, así que notificar aquí diría "faltan 3 días" con el deadline encima; esa
        // banda es de la alarma de 1 día, que ya avisa con el texto correcto.
        assertFalse(ProjectDeadlineReceiver.shouldNotify(active(NOW + 2 * HOUR), OFFSET_3D, NOW));
        // Justo en el borde: al alcanzar el offset siguiente, la alarma de 3 días deja de mandar.
        assertFalse(ProjectDeadlineReceiver.shouldNotify(active(NOW + DAY), OFFSET_3D, NOW));
    }

    @Test
    public void lastOffsetHasNoFloor_soItStillNotifiesNearTheDeadline() {
        // La de 1 día es la última: por debajo de ella no hay otra alarma que cubra la banda, así
        // que sigue siendo la dueña hasta el propio deadline.
        assertTrue(ProjectDeadlineReceiver.shouldNotify(active(NOW + 2 * HOUR), OFFSET_1D, NOW));
        assertTrue(ProjectDeadlineReceiver.shouldNotify(active(NOW + 1), OFFSET_1D, NOW));
    }

    @Test
    public void smallClockJitter_isToleratedByTheGrace() {
        // Disparo puntual pero con unos ms de deriva: remaining sale un pelo por encima del offset
        // y sin margen se caería el aviso.
        assertTrue(ProjectDeadlineReceiver.shouldNotify(active(NOW + DAY + 1_000L), OFFSET_1D, NOW));
    }

    @Test
    public void offsetIndexOutOfRange_doesNotNotify() {
        assertFalse(ProjectDeadlineReceiver.shouldNotify(active(NOW + DAY), -1, NOW));
        assertFalse(ProjectDeadlineReceiver.shouldNotify(active(NOW + DAY), 9, NOW));
    }
}
