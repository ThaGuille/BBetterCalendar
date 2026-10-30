package com.example.bbettercalendar.social.presence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * F4-u4: {@link PresenceMapping} contra el contrato de {@code status/{uid}} (03 §5) y la semántica de quien mira
 * (02 §7, D-033). Las claves se escriben aquí a mano tal como las fija el contrato, así que este test también las
 * fija para el lector (u3 fija las del escritor por su lado).
 */
public class PresenceMappingTest {

    private static final long MIN = 60_000L;
    private static final long HOUR = 60 * MIN;
    /** Hora del servidor de todos los casos. */
    private static final long NOW = 1_800_000_000_000L;

    private static Map<String, Object> status(String state, Long since, Long sessionStart, Long pausedAt) {
        Map<String, Object> m = new HashMap<>();
        m.put("state", state);
        if (since != null) {
            m.put("since", since);
        }
        if (sessionStart != null) {
            m.put("sessionStart", sessionStart);
        }
        if (pausedAt != null) {
            m.put("pausedAt", pausedAt);
        }
        return m;
    }

    @Test
    public void contractKeys_matchTheWireContract() {
        assertEquals("state", PresenceMapping.STATE);
        assertEquals("since", PresenceMapping.SINCE);
        assertEquals("sessionStart", PresenceMapping.SESSION_START);
        assertEquals("pausedAt", PresenceMapping.PAUSED_AT);
        assertEquals("focus", PresenceMapping.FOCUS);
        assertEquals("break", PresenceMapping.BREAK);
        assertEquals("paused", PresenceMapping.PAUSED);
        assertEquals("idle", PresenceMapping.IDLE);
        assertEquals(4 * HOUR, PresenceMapping.MAX_AGE_MS);
    }

    @Test
    public void focusFresh_isStudyingWithLiveTime() {
        Presence p = PresenceMapping.fromMap(status("focus", NOW - 5 * MIN, NOW - 42 * MIN, null), NOW);

        assertEquals(Presence.State.STUDYING, p.state);
        assertTrue(p.isPresent());
        assertEquals(42 * MIN, p.elapsedMs(NOW));
        assertEquals(43 * MIN, p.elapsedMs(NOW + MIN)); // el tiempo corre en el móvil de quien mira
    }

    @Test
    public void breakFresh_isStudyingToo() {
        // d1 = A: el descanso viaja como estado propio y se pinta como estudiando (sin «en descanso»).
        Presence p = PresenceMapping.fromMap(status("break", NOW - MIN, NOW - 30 * MIN, null), NOW);

        assertEquals(Presence.State.STUDYING, p.state);
        assertEquals(30 * MIN, p.elapsedMs(NOW));
    }

    @Test
    public void focusWithSince4hAnd1MinOld_isAway() {
        Presence p = PresenceMapping.fromMap(status("focus", NOW - 4 * HOUR - MIN, NOW - 5 * HOUR, null), NOW);

        assertSame(Presence.AWAY, p);
    }

    @Test
    public void exactlyAtThe4hLine_isStillStudying_andOneMsLaterAway() {
        Map<String, Object> raw = status("focus", NOW - 4 * HOUR, NOW - 4 * HOUR, null);

        assertEquals(Presence.State.STUDYING, PresenceMapping.fromMap(raw, NOW).state);
        assertEquals(NOW, PresenceMapping.fromMap(raw, NOW).awayAfter());
        assertSame(Presence.AWAY, PresenceMapping.fromMap(raw, NOW + 1));
    }

    @Test
    public void idle_isAway() {
        assertSame(Presence.AWAY, PresenceMapping.fromMap(status("idle", NOW - MIN, null, null), NOW));
        // Aunque traiga un inicio colado: idle es ausente.
        assertSame(Presence.AWAY, PresenceMapping.fromMap(status("idle", NOW - MIN, NOW - MIN, null), NOW));
    }

    @Test
    public void unknownState_isAway() {
        assertSame(Presence.AWAY, PresenceMapping.fromMap(status("studying", NOW - MIN, NOW - MIN, null), NOW));
        assertSame(Presence.AWAY, PresenceMapping.fromMap(status("FOCUS", NOW - MIN, NOW - MIN, null), NOW));
    }

    @Test
    public void malformedOrMissingKeys_areAway() {
        // Sin since.
        assertSame(Presence.AWAY, PresenceMapping.fromMap(status("focus", null, NOW - MIN, null), NOW));
        // focus sin sessionStart, o con 0.
        assertSame(Presence.AWAY, PresenceMapping.fromMap(status("focus", NOW - MIN, null, null), NOW));
        assertSame(Presence.AWAY, PresenceMapping.fromMap(status("focus", NOW - MIN, 0L, null), NOW));
        // Tipos equivocados.
        Map<String, Object> textSince = status("focus", null, NOW - MIN, null);
        textSince.put("since", "ayer");
        assertSame(Presence.AWAY, PresenceMapping.fromMap(textSince, NOW));
        Map<String, Object> numericState = status("focus", NOW - MIN, NOW - MIN, null);
        numericState.put("state", 1L);
        assertSame(Presence.AWAY, PresenceMapping.fromMap(numericState, NOW));
        // Sin nada.
        assertSame(Presence.AWAY, PresenceMapping.fromMap(new HashMap<String, Object>(), NOW));
    }

    @Test
    public void sessionStartInTheFuture_hasLiveTimeZero() {
        // Reloj de quien escribe adelantado (las reglas dejan hasta 1 min): presente, pero el tiempo no es negativo.
        Presence p = PresenceMapping.fromMap(status("focus", NOW, NOW + 40_000L, null), NOW);

        assertEquals(Presence.State.STUDYING, p.state);
        assertEquals(0L, p.elapsedMs(NOW));
    }

    @Test
    public void nullStatus_isAway() {
        // No existe, ilegible o PERMISSION_DENIED: el repositorio pasa null.
        Presence p = PresenceMapping.fromMap(null, NOW);

        assertSame(Presence.AWAY, p);
        assertFalse(p.isPresent());
        assertEquals(0L, p.elapsedMs(NOW));
        assertEquals(Long.MAX_VALUE, p.awayAfter());
    }

    @Test
    public void pausedFresh_isPausedWithFrozenTime() {
        // d3: «En pausa», el tiempo queda en pausedAt − sessionStart y no corre.
        Presence p = PresenceMapping.fromMap(
                status("paused", NOW - 10 * MIN, NOW - 50 * MIN, NOW - 10 * MIN), NOW);

        assertEquals(Presence.State.PAUSED, p.state);
        assertTrue(p.isPresent());
        assertEquals(40 * MIN, p.frozenElapsedMs());
        assertEquals(40 * MIN, p.elapsedMs(NOW));
        assertEquals(40 * MIN, p.elapsedMs(NOW + HOUR));
    }

    @Test
    public void pausedMalformed_isAway() {
        // Sin pausedAt.
        assertSame(Presence.AWAY, PresenceMapping.fromMap(
                status("paused", NOW - MIN, NOW - 20 * MIN, null), NOW));
        // pausedAt antes del inicio.
        assertSame(Presence.AWAY, PresenceMapping.fromMap(
                status("paused", NOW - MIN, NOW - 20 * MIN, NOW - 30 * MIN), NOW));
        // Sin sessionStart.
        assertSame(Presence.AWAY, PresenceMapping.fromMap(
                status("paused", NOW - MIN, null, NOW - MIN), NOW));
    }

    @Test
    public void pausedAtEqualToSessionStart_isPausedAtZero() {
        Presence p = PresenceMapping.fromMap(status("paused", NOW - MIN, NOW - 2 * MIN, NOW - 2 * MIN), NOW);

        assertEquals(Presence.State.PAUSED, p.state);
        assertEquals(0L, p.elapsedMs(NOW));
    }

    @Test
    public void forgottenPause_turnsAwayAfter4hFromSince() {
        // La pausa no se refresca: pasadas 4 h desde since se ve ausente.
        Map<String, Object> raw = status("paused", NOW - 4 * HOUR - MIN, NOW - 5 * HOUR, NOW - 4 * HOUR - MIN);

        assertSame(Presence.AWAY, PresenceMapping.fromMap(raw, NOW));
    }

    @Test
    public void numbersAsDouble_areAccepted() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("state", "focus");
        raw.put("since", (double) (NOW - MIN));
        raw.put("sessionStart", (double) (NOW - 3 * MIN));

        Presence p = PresenceMapping.fromMap(raw, NOW);
        assertEquals(Presence.State.STUDYING, p.state);
        assertEquals(3 * MIN, p.elapsedMs(NOW));
    }
}
