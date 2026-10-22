package com.example.bbettercalendar.social.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.social.profile.HandleValidator.HandleResult;

import org.junit.Test;

/** F2-u1: falsos positivos de {@link BannedWords} («therapist», «retard» en catalán). */
public class BannedWordsTest {

    @Test
    public void innocentWordsContainingABannedOnePass() {
        for (String s : new String[]{"therapist", "Therapists", "terapist", "el retard del tren", "retardat",
                "Psychotherapist Anna", "retard"}) {
            assertFalse(s, BannedWords.containsBanned(s));
        }
        assertEquals(HandleResult.OK, HandleValidator.checkHandle("therapist"));
        assertEquals(HandleResult.OK, HandleValidator.checkHandle("retardat"));
        assertTrue(HandleValidator.isValidDisplayName("El retard del tren"));
    }

    @Test
    public void bannedWordsStayBanned() {
        for (String s : new String[]{"rapist", "retarded", "therapist_rapist", "Therapist Rapist",
                "r.a.p.i.s.t", "RETARDED_99", "therapistretarded"}) {
            assertTrue(s, BannedWords.containsBanned(s));
        }
        assertEquals(HandleResult.BANNED, HandleValidator.checkHandle("therapist_rapist"));
        assertEquals(HandleResult.BANNED, HandleValidator.checkHandle("retarded"));
        assertEquals(HandleResult.BANNED, HandleValidator.checkHandle("rapist"));
    }

    @Test
    public void exceptionsAreRemovedBeforeMatchingNotWhitelisted() {
        // Quitar la excepción no puede unir los lados en una palabra prohibida nueva ni tapar otra.
        assertFalse(BannedWords.containsBanned("sh therapist it"));
        assertTrue(BannedWords.containsBanned("therapist fuck"));
        assertFalse(BannedWords.containsBanned(""));
        assertFalse(BannedWords.containsBanned(null));
    }
}
