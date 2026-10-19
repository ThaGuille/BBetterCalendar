package com.example.bbettercalendar.social.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.social.profile.HandleValidator.HandleResult;
import com.example.bbettercalendar.social.profile.HandleValidator.NameResult;

import org.junit.Test;

public class HandleValidatorTest {

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    // ---- @usuario: formato -----------------------------------------------------------------------------

    @Test
    public void validHandlesAreAccepted() {
        assertEquals(HandleResult.OK, HandleValidator.checkHandle("alice"));
        assertEquals(HandleResult.OK, HandleValidator.checkHandle("guille_92"));
        assertEquals(HandleResult.OK, HandleValidator.checkHandle("a.b_c.9"));
    }

    @Test
    public void handleLengthBoundaries() {
        assertEquals(HandleResult.OK, HandleValidator.checkHandle("abc"));
        assertEquals(HandleResult.OK, HandleValidator.checkHandle(repeat('a', 20)));
        assertEquals(HandleResult.TOO_SHORT, HandleValidator.checkHandle("ab"));
        assertEquals(HandleResult.TOO_LONG, HandleValidator.checkHandle(repeat('a', 21)));
        assertEquals(HandleResult.EMPTY, HandleValidator.checkHandle(""));
        assertEquals(HandleResult.EMPTY, HandleValidator.checkHandle(null));
    }

    @Test
    public void handleWithCharsOutsideTheSetIsRejected() {
        assertEquals(HandleResult.INVALID_CHARS, HandleValidator.checkHandle("ali ce"));
        assertEquals(HandleResult.INVALID_CHARS, HandleValidator.checkHandle("ali-ce"));
        assertEquals(HandleResult.INVALID_CHARS, HandleValidator.checkHandle("alicé"));
        assertEquals(HandleResult.INVALID_CHARS, HandleValidator.checkHandle("niño"));
        assertEquals(HandleResult.INVALID_CHARS, HandleValidator.checkHandle("al@ce"));
    }

    @Test
    public void upperCaseIsLoweredAndAccepted() {
        assertEquals("alice", HandleValidator.normalizeHandle("AliCe"));
        assertEquals(HandleResult.OK, HandleValidator.checkHandle("ALICE_99"));
        assertTrue(HandleValidator.isValidHandle("Guille.Dev"));
    }

    @Test
    public void leadingAtAndSpacesAreStripped() {
        assertEquals("alice", HandleValidator.normalizeHandle("  @Alice "));
        assertEquals(HandleResult.OK, HandleValidator.checkHandle("@alice"));
    }

    @Test
    public void everyAcceptedHandleMatchesTheRulesFormat() {
        String[] samples = {"abc", "a.b", "x_y_z", "user123", repeat('z', 20), "@Mixed.Case"};
        for (String s : samples) {
            if (HandleValidator.isValidHandle(s)) {
                assertTrue(s, HandleValidator.normalizeHandle(s).matches("^[a-z0-9_.]{3,20}$"));
            }
        }
    }

    // ---- @usuario: reservados y prohibidos -------------------------------------------------------------

    @Test
    public void reservedHandlesAreRejected() {
        for (String h : new String[]{"admin", "bbetter", "soporte", "support", "moderator", "root",
                "staff", "official", "moderador"}) {
            assertEquals(h, HandleResult.RESERVED, HandleValidator.checkHandle(h));
        }
    }

    @Test
    public void reservedIgnoresCase() {
        assertEquals(HandleResult.RESERVED, HandleValidator.checkHandle("ADMIN"));
        assertEquals(HandleResult.RESERVED, HandleValidator.checkHandle("@Soporte"));
    }

    @Test
    public void handleContainingTheBrandIsReserved() {
        assertEquals(HandleResult.RESERVED, HandleValidator.checkHandle("bbetter_team"));
        assertEquals(HandleResult.RESERVED, HandleValidator.checkHandle("the.bbetter.app"));
    }

    @Test
    public void bannedWordInsideTheHandleIsRejected() {
        assertEquals(HandleResult.BANNED, HandleValidator.checkHandle("xfuckx"));
        assertEquals(HandleResult.BANNED, HandleValidator.checkHandle("mierda_99"));
        assertEquals(HandleResult.BANNED, HandleValidator.checkHandle("el_gilipollas"));
        assertEquals(HandleResult.BANNED, HandleValidator.checkHandle("collons.22"));
        assertEquals(HandleResult.BANNED, HandleValidator.checkHandle("SHITHEAD"));
    }

    @Test
    public void bannedWordSplitBySeparatorsIsRejected() {
        assertEquals(HandleResult.BANNED, HandleValidator.checkHandle("f.u.c.k"));
        assertEquals(HandleResult.BANNED, HandleValidator.checkHandle("mi_er_da"));
    }

    @Test
    public void innocentWordsAreNotFlagged() {
        // Raíces cortas excluidas a propósito de BannedWords (falsos positivos como subcadena).
        for (String h : new String[]{"computadora", "reputacion", "vehiculo", "pelicula", "pollastre",
                "nazir", "classy", "document"}) {
            assertEquals(h, HandleResult.OK, HandleValidator.checkHandle(h));
        }
    }

    // ---- nombre visible --------------------------------------------------------------------------------

    @Test
    public void displayNameLengthBoundaries() {
        assertEquals(NameResult.OK, HandleValidator.checkDisplayName("A"));
        assertEquals(NameResult.OK, HandleValidator.checkDisplayName(repeat('n', 30)));
        assertEquals(NameResult.TOO_LONG, HandleValidator.checkDisplayName(repeat('n', 31)));
        assertEquals(NameResult.EMPTY, HandleValidator.checkDisplayName(""));
        assertEquals(NameResult.EMPTY, HandleValidator.checkDisplayName("   "));
        assertEquals(NameResult.EMPTY, HandleValidator.checkDisplayName(null));
    }

    @Test
    public void displayNameIsTrimmedAndKeepsCaseAndAccents() {
        assertEquals("Núria Puig", HandleValidator.normalizeDisplayName("  Núria Puig "));
        assertEquals(NameResult.OK, HandleValidator.checkDisplayName("  Núria Puig "));
        assertEquals(NameResult.OK, HandleValidator.checkDisplayName("José Ángel 🙂"));
    }

    @Test
    public void bannedWordInsideTheDisplayNameIsRejected() {
        assertEquals(NameResult.BANNED, HandleValidator.checkDisplayName("Señor Mierda"));
        assertEquals(NameResult.BANNED, HandleValidator.checkDisplayName("Gilipóllas"));
        assertEquals(NameResult.BANNED, HandleValidator.checkDisplayName("MotherFucker"));
        assertEquals(NameResult.BANNED, HandleValidator.checkDisplayName("Fill de Merda"));
        assertFalse(HandleValidator.isValidDisplayName("b.i.t.c.h"));
    }

    @Test
    public void innocentDisplayNamesAreAccepted() {
        assertTrue(HandleValidator.isValidDisplayName("Josh It"));
        assertTrue(HandleValidator.isValidDisplayName("Nazir"));
        assertTrue(HandleValidator.isValidDisplayName("Reputación"));
    }

    @Test
    public void bannedListIsBasicButNotTrivial() {
        assertTrue(BannedWords.WORDS.size() >= 30);
        for (String w : BannedWords.WORDS) {
            assertEquals(w, BannedWords.fold(w), w);
        }
    }
}
