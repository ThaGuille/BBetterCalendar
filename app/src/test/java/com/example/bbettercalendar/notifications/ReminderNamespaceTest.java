package com.example.bbettercalendar.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.notifications.event.EventReminderReceiver;
import com.example.bbettercalendar.notifications.event.EventReminderScheduler;
import com.example.bbettercalendar.notifications.project.ProjectDeadlineScheduler;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * Los avisos de deadline de proyecto (spec project-deadlines-progress) comparten dos espacios de
 * ids globales con los recordatorios de entrada: los request codes de
 * {@code PendingIntent.getBroadcast()} y los ids de notificación. Como los ids de {@code project} y
 * de {@code calendarEntry} arrancan ambos en 1, un espacio compartido significaría que cancelar el
 * recordatorio de una tarea mata el aviso de un proyecto (y que una notificación pisa a la otra).
 * Estos tests fijan esa separación contra las fórmulas reales, no contra copias suyas.
 *
 * Los dos namespaces son disjuntos por desplazamiento (base 500_000 y 70_000), no por
 * construcción, así que tienen techo: se solaparían con ids de entrada de ~50_000 (request codes) y
 * de proyecto de ~3_000 (ids de notificación). ID_CEILING acota el rango a lo realista con mucho
 * margen; si alguna vez deja de serlo, hay que rehacer las bases, no relajar el test.
 */
public class ReminderNamespaceTest {

    private static final int ID_CEILING = 2_000;
    private static final int ENTRY_OFFSET_COUNT = 7;   // NotificationOffsets.OFFSET_MILLIS.length
    private static final int PROJECT_OFFSET_COUNT = 2; // {3d, 1d}

    @Test
    public void projectOffsetTable_hasTwoEntriesThreeDaysAndOneDay() {
        assertEquals(PROJECT_OFFSET_COUNT, ProjectDeadlineScheduler.OFFSET_MILLIS.length);
        assertEquals(3L * 24 * 60 * 60_000L, ProjectDeadlineScheduler.OFFSET_MILLIS[0]);
        assertEquals(24L * 60 * 60_000L, ProjectDeadlineScheduler.OFFSET_MILLIS[1]);
        assertEquals(NotificationOffsets.OFFSET_MILLIS.length, ENTRY_OFFSET_COUNT);
    }

    @Test
    public void requestCodeSpaces_doNotOverlap() {
        Set<Integer> entryCodes = new HashSet<>();
        for (int entryId = 0; entryId <= ID_CEILING; entryId++) {
            for (int i = 0; i < ENTRY_OFFSET_COUNT; i++) {
                entryCodes.add(EventReminderScheduler.requestCodeFor(entryId, i));
            }
        }
        for (int projectId = 0; projectId <= ID_CEILING; projectId++) {
            for (int i = 0; i < PROJECT_OFFSET_COUNT; i++) {
                int code = ProjectDeadlineScheduler.requestCodeFor(projectId, i);
                assertTrue("request code " + code + " collides with the entry-reminder space",
                        !entryCodes.contains(code));
            }
        }
    }

    @Test
    public void notificationIdSpaces_doNotOverlap() {
        Set<Integer> entryIds = new HashSet<>();
        for (int entryId = 0; entryId <= ID_CEILING; entryId++) {
            for (int i = 0; i < ENTRY_OFFSET_COUNT; i++) {
                entryIds.add(EventReminderReceiver.notificationIdFor(entryId, i));
            }
        }
        for (int projectId = 0; projectId <= ID_CEILING; projectId++) {
            for (int i = 0; i < PROJECT_OFFSET_COUNT; i++) {
                int id = ProjectDeadlineScheduler.notificationIdFor(projectId, i);
                assertTrue("notification id " + id + " collides with the entry-reminder space",
                        !entryIds.contains(id));
            }
        }
    }

    @Test
    public void projectNotificationIds_avoidTheFocusAndUsageIds() {
        // focus = 50_001; usage = 60_000 / 61_000 (ver la tabla de particiones en notifications.md).
        for (int projectId = 0; projectId <= ID_CEILING; projectId++) {
            for (int i = 0; i < PROJECT_OFFSET_COUNT; i++) {
                int id = ProjectDeadlineScheduler.notificationIdFor(projectId, i);
                assertTrue("project notification id " + id + " is below the reserved 70_000 base",
                        id >= 70_000);
                assertTrue("project notification id " + id + " reaches the entry space",
                        id < 100_000);
            }
        }
    }

    @Test
    public void projectIdsAreDistinctPerOffset() {
        assertEquals(70_010, ProjectDeadlineScheduler.notificationIdFor(1, 0));
        assertEquals(70_011, ProjectDeadlineScheduler.notificationIdFor(1, 1));
        assertEquals(500_010, ProjectDeadlineScheduler.requestCodeFor(1, 0));
        assertEquals(500_011, ProjectDeadlineScheduler.requestCodeFor(1, 1));
    }
}
