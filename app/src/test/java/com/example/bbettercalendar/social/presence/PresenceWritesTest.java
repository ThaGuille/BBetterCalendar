package com.example.bbettercalendar.social.presence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.State;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * F4-u3: los mapas de {@code status/{uid}} contra el contrato de 03 §5 / D-033, escrito aquí a mano:
 * {@code {state: focus|break|paused|idle, since, sessionStart? (focus, break, paused), pausedAt? (paused)}} y
 * ninguna otra clave. {@code since} es un centinela de prueba en lugar de {@code ServerValue.TIMESTAMP}.
 */
public class PresenceWritesTest {

    private static final Object SINCE = new Object() {
        @Override
        public String toString() {
            return "SERVER_TIMESTAMP";
        }
    };
    private static final long T0 = 1_793_354_400_000L;

    private static Map<String, Object> expected(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Test
    public void wireNamesMatchTheContract() {
        assertEquals("status", PresenceWrites.STATUS);
        assertEquals("state", PresenceWrites.STATE);
        assertEquals("since", PresenceWrites.SINCE);
        assertEquals("sessionStart", PresenceWrites.SESSION_START);
        assertEquals("pausedAt", PresenceWrites.PAUSED_AT);
        assertEquals("focus", PresenceWrites.FOCUS);
        assertEquals("break", PresenceWrites.BREAK);
        assertEquals("paused", PresenceWrites.PAUSED);
        assertEquals("idle", PresenceWrites.IDLE);
    }

    @Test
    public void focusCarriesStateSinceAndSessionStartInServerTime() {
        // el reloj del móvil va 1,5 s por detrás del servidor
        assertEquals(expected("state", "focus", "since", SINCE, "sessionStart", T0 + 1_500L),
                PresenceWrites.toMap(OwnPresence.focus(T0), 1_500L, SINCE));
    }

    @Test
    public void breakIsItsOwnStateWithTheRunStart() {
        assertEquals(expected("state", "break", "since", SINCE, "sessionStart", T0),
                PresenceWrites.toMap(OwnPresence.onBreak(T0), 0L, SINCE));
    }

    @Test
    public void pausedCarriesSessionStartAndPausedAt() {
        assertEquals(expected("state", "paused", "since", SINCE, "sessionStart", T0 - 2_000L,
                        "pausedAt", T0 + 600_000L - 2_000L),
                PresenceWrites.toMap(OwnPresence.paused(T0, T0 + 600_000L), -2_000L, SINCE));
    }

    @Test
    public void idleCarriesOnlyStateAndSince() {
        Map<String, Object> idle = PresenceWrites.idle(SINCE);
        assertEquals(expected("state", "idle", "since", SINCE), idle);
        assertFalse(idle.containsKey("sessionStart"));
        assertFalse(idle.containsKey("pausedAt"));
    }

    @Test
    public void idlePresenceMapsToTheIdleMapWhateverTheOffset() {
        assertEquals(expected("state", "idle", "since", SINCE),
                PresenceWrites.toMap(OwnPresence.IDLE, 99_999L, SINCE));
    }

    @Test
    public void aRestBetweenPomodorosIsSentAsBreakNeverAsFocus() {
        // d1 (usuario): el descanso viaja como estado propio y la pantalla lo pinta como estudiando.
        OwnPresence rest = PresenceTransitions.next(OwnPresence.focus(T0), State.RUNNING_REST, 0L, T0 + 1_500_000L);
        assertEquals("break", PresenceWrites.toMap(rest, 0L, SINCE).get("state"));
    }

    @Test
    public void sessionStartIsNeverZeroOrNegative() {
        // las reglas exigen sessionStart > 0 aunque el reloj del móvil esté roto
        assertEquals(1L, PresenceWrites.toMap(OwnPresence.focus(0L), 0L, SINCE).get("sessionStart"));
    }
}
