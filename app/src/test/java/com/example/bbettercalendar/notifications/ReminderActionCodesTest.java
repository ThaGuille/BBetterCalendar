package com.example.bbettercalendar.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.notifications.event.EventReminderScheduler;
import com.example.bbettercalendar.notifications.event.ReminderActionReceiver;
import com.example.bbettercalendar.notifications.project.ProjectDeadlineScheduler;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/** Los request codes de los botones y de la alarma pospuesta no pisan ningún otro espacio. */
public class ReminderActionCodesTest {

    private static final int ID_CEILING = 2_000;
    private static final int OFFSETS = 7;

    @Test
    public void formulas_addEntryIdTimesTenPlusOffset() {
        assertEquals(1_000_000 + 52, ReminderActionReceiver.doneRequestCode(5, 2));
        assertEquals(2_000_000 + 52, ReminderActionReceiver.snoozeRequestCode(5, 2));
        assertEquals(3_000_000 + 52, ReminderActionReceiver.snoozedAlarmRequestCode(5, 2));
    }

    @Test
    public void newCodeSpaces_areDisjointFromEachOtherAndFromExistingOnes() {
        Set<Integer> all = new HashSet<>();
        int expected = 0;
        for (int e = 0; e <= ID_CEILING; e++) {
            for (int i = 0; i < OFFSETS; i++) {
                all.add(EventReminderScheduler.requestCodeFor(e, i));
                all.add(ReminderActionReceiver.doneRequestCode(e, i));
                all.add(ReminderActionReceiver.snoozeRequestCode(e, i));
                all.add(ReminderActionReceiver.snoozedAlarmRequestCode(e, i));
                expected += 4;
            }
            for (int i = 0; i < 2; i++) {
                all.add(ProjectDeadlineScheduler.requestCodeFor(e, i));
                expected++;
            }
        }
        assertEquals("some request codes collide", expected, all.size());
        for (int fixed : new int[]{NotificationIds.REQ_FOCUS_PHASE_END, NotificationIds.REQ_FOCUS_PHASE_ACTION,
                NotificationIds.REQ_NUDGE_MORNING, NotificationIds.REQ_NUDGE_STREAK,
                NotificationIds.REQ_NUDGE_EVENING_PLAN, NotificationIds.REQ_WEEKLY_SUMMARY}) {
            assertTrue("fixed code " + fixed + " collides", !all.contains(fixed));
        }
    }
}
