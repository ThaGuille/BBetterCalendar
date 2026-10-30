package com.example.bbettercalendar.social.presence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.example.bbettercalendar.social.account.SocialPrefs;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * F4-u3: el interruptor «Compartir mi estado en directo» (D-032) y su valor por defecto d4 = B (encendido 18+,
 * apagado 14-17 y con la edad desconocida). Año actual fijo: 2026 (edad prudente = 2026 - año - 1).
 */
@SuppressWarnings("unchecked")
public class PresenceSharingTest {

    private SocialPrefs prefs;
    private PresenceSharing sharing;
    private final Map<String, Boolean> stored = new HashMap<>();
    private Integer birthYear;
    private List<Consumer<Boolean>> heldReads;

    @Before
    public void setUp() {
        prefs = mock(SocialPrefs.class);
        doAnswer(inv -> {
            Consumer<Boolean> cb = inv.getArgument(1);
            if (heldReads != null) {
                heldReads.add(cb);
            } else {
                cb.accept(stored.get((String) inv.getArgument(0)));
            }
            return null;
        }).when(prefs).readPresenceSharing(anyString(), any(Consumer.class));
        doAnswer(inv -> {
            ((Consumer<Integer>) inv.getArgument(0)).accept(birthYear);
            return null;
        }).when(prefs).readBirthYear(any(Consumer.class));
        sharing = new PresenceSharing(prefs, () -> 2026);
    }

    private Boolean read(String uid) {
        Boolean[] out = new Boolean[1];
        sharing.readSharing(uid, value -> out[0] = value);
        return out[0];
    }

    @Test
    public void defaultIsOnFrom18() {
        birthYear = 2007; // 18 en 2026 por el lado prudente
        assertEquals(Boolean.TRUE, read("a"));
    }

    @Test
    public void defaultIsOffFor14To17() {
        birthYear = 2008; // 17
        assertEquals(Boolean.FALSE, read("a"));
    }

    @Test
    public void unknownBirthYearCountsAsAMinor() {
        birthYear = null;
        assertEquals(Boolean.FALSE, read("a"));
    }

    @Test
    public void aStoredChoiceWinsOverTheDefaultPerUid() {
        birthYear = 1990;
        stored.put("a", false);
        assertEquals(Boolean.FALSE, read("a"));
        assertEquals(Boolean.TRUE, read("b"));
    }

    @Test
    public void notKnownYetCountsAsOffThenTheReadFillsTheCache() {
        birthYear = 1990;
        heldReads = new ArrayList<>();
        assertFalse(sharing.isSharing("a"));
        heldReads.get(0).accept(null);
        assertTrue(sharing.isSharing("a"));
    }

    @Test
    public void aSwitchChangeBeatsAReadStillInFlight() {
        birthYear = 1990;
        heldReads = new ArrayList<>();
        Boolean[] out = new Boolean[1];
        sharing.readSharing("a", value -> out[0] = value);
        sharing.setSharing("a", false);
        heldReads.get(0).accept(null); // la lectura vieja diría «encendido» (18+)
        assertEquals(Boolean.FALSE, out[0]);
        assertFalse(sharing.isSharing("a"));
    }
}
