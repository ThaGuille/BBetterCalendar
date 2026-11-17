package com.example.bbettercalendar.notifications.focus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.notifications.focus.FocusOngoingNotification.Content;
import com.example.bbettercalendar.notifications.focus.FocusOngoingNotification.Mode;
import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.State;

import org.junit.Test;

/** Plan sistema-notificaciones B.2: qué enseña la notificación del servicio para cada estado. */
public class FocusOngoingNotificationTest {

    private static final long NOW = 1_700_000_000_000L;

    @Test
    public void stopped_hasNoContent() {
        assertNull(Content.forState(State.STOPPED, 1_000L, NOW));
    }

    @Test
    public void runningFocus_countsDownToWallClockEnd() {
        Content c = Content.forState(State.RUNNING, 90_000L, NOW);
        assertEquals(Mode.FOCUS, c.mode);
        assertEquals(NOW + 90_000L, c.endsAtWallMillis);
        assertTrue(c.usesChronometer());
    }

    @Test
    public void runningRest_isOnABreak() {
        Content c = Content.forState(State.RUNNING_REST, 30_000L, NOW);
        assertEquals(Mode.REST, c.mode);
        assertTrue(c.usesChronometer());
    }

    @Test
    public void pausedStates_haveNoChronometer() {
        for (State s : new State[]{State.PAUSED, State.PAUSED_REST, State.STOPPED_REST}) {
            Content c = Content.forState(s, 61_500L, NOW);
            assertEquals(s.name(), Mode.PAUSED, c.mode);
            assertFalse(s.name(), c.usesChronometer());
            assertEquals(61_500L, c.remainingMillis);
        }
    }

    @Test
    public void formatRemaining_floorsLikeTheApp() {
        assertEquals("00:00", FocusOngoingNotification.formatRemaining(-5L));
        assertEquals("01:01", FocusOngoingNotification.formatRemaining(61_999L));
        assertEquals("25:00", FocusOngoingNotification.formatRemaining(25 * 60_000L));
        assertEquals("1:30:05", FocusOngoingNotification.formatRemaining((90 * 60 + 5) * 1000L));
    }
}
