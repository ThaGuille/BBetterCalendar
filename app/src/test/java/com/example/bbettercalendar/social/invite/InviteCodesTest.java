package com.example.bbettercalendar.social.invite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** F2-u6: {@link InviteCodes} — alfabeto, normalización, ruta del App Link y referrer de Play. */
public class InviteCodesTest {

    /** El formato de las reglas de invites/{code} (F2-u3). */
    private static final Pattern RULES = Pattern.compile("^[A-HJKMNP-Z2-9]{8}$");

    @Test
    public void generateGivesEightCharactersWithoutAmbiguousOnes() {
        SecureRandom random = new SecureRandom();
        Set<Character> seen = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            String code = InviteCodes.generate(random);
            assertEquals(8, code.length());
            assertTrue(code, RULES.matcher(code).matches());
            assertTrue(InviteCodes.isValid(code));
            for (char c : code.toCharArray()) {
                assertTrue("ambiguous " + c, "ILO01".indexOf(c) < 0);
                seen.add(c);
            }
            codes.add(code);
        }
        assertEquals(31, InviteCodes.ALPHABET.length());
        assertEquals("every symbol of the alphabet shows up", 31, seen.size());
        assertTrue("codes do not repeat", codes.size() > 4990);
    }

    @Test
    public void normalizeUppercasesTrimsAndRejectsBadCodes() {
        assertEquals("ABCD2345", InviteCodes.normalize("abcd2345"));
        assertEquals("ABCD2345", InviteCodes.normalize("  aBcD2345 \n"));
        assertNull(InviteCodes.normalize(null));
        assertNull(InviteCodes.normalize(""));
        assertNull(InviteCodes.normalize("ABCD234"));     // 7
        assertNull(InviteCodes.normalize("ABCD23456"));   // 9
        assertNull(InviteCodes.normalize("ABCDO234"));    // O
        assertNull(InviteCodes.normalize("ABCDI234"));    // I
        assertNull(InviteCodes.normalize("abcdl234"));    // l → L
        assertNull(InviteCodes.normalize("ABCD0234"));    // 0
        assertNull(InviteCodes.normalize("ABCD1234"));    // 1
        assertNull(InviteCodes.normalize("ABCD-234"));
        assertFalse(InviteCodes.isValid("abcd2345"));     // isValid no normaliza
    }

    @Test
    public void fromLinkReadsTheCodeOfTheInvitePath() {
        assertEquals("ABCD2345", InviteCodes.fromLink("/i/ABCD2345"));
        assertEquals("ABCD2345", InviteCodes.fromLink("/i/abcd2345"));
        assertEquals("ABCD2345", InviteCodes.fromLink("/i/ABCD2345/"));
        assertNull(InviteCodes.fromLink(null));
        assertNull(InviteCodes.fromLink("/i/"));
        assertNull(InviteCodes.fromLink("/i/ABCD1234"));
        assertNull(InviteCodes.fromLink("/x/ABCD2345"));
        assertNull(InviteCodes.fromLink("/i/ABCD2345/extra"));
        assertNull(InviteCodes.fromLink("i/ABCD2345"));
    }

    @Test
    public void fromReferrerReadsTheInviteParameter() {
        // Lo que Play entrega del enlace de la página /i/: referrer=invite%3DABCD2345 → invite=ABCD2345.
        assertEquals("ABCD2345", InviteCodes.fromReferrer("invite=ABCD2345"));
        assertEquals("ABCD2345", InviteCodes.fromReferrer("invite=abcd2345"));
        // Sin decodificar, o junto a los utm_* de Play.
        assertEquals("ABCD2345", InviteCodes.fromReferrer("invite%3DABCD2345"));
        assertEquals("ABCD2345", InviteCodes.fromReferrer("utm_source=bbetter&invite=ABCD2345&utm_medium=link"));
        assertEquals("ABCD2345", InviteCodes.fromReferrer("INVITE=ABCD2345"));
        // Instalación orgánica o referrer sin invitación válida.
        assertNull(InviteCodes.fromReferrer("utm_source=google-play&utm_medium=organic"));
        assertNull(InviteCodes.fromReferrer("invite=ABCD1234"));
        assertNull(InviteCodes.fromReferrer("invite="));
        assertNull(InviteCodes.fromReferrer("invitee=ABCD2345"));
        assertNull(InviteCodes.fromReferrer("%"));
        assertNull(InviteCodes.fromReferrer(""));
        assertNull(InviteCodes.fromReferrer(null));
    }
}
