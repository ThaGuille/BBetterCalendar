package com.example.bbettercalendar.social.push;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.social.inbox.InboxItem;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * F5-u6: {@link FriendsPushPayload} (lectura del push de datos, texto por tipo, id de la notificación) y la decisión
 * de {@link FriendsPushHandler#deliver} (solo con flag, sesión y {@code to} igual a ella). Las cadenas son las
 * plantillas en español de {@code values-es/strings_push.xml}.
 */
public class FriendsPushPayloadTest {

    private static final Map<Integer, String> ES = new HashMap<>();

    static {
        ES.put(R.string.push_cheer_title, "%1$s te anima");
        ES.put(R.string.push_cheer_body, "¡Sigue así! Tu amigo te ha enviado un ánimo.");
        ES.put(R.string.push_nudge_title, "%1$s te da un empujón");
        ES.put(R.string.push_nudge_body, "¡Vamos! ¿Una sesión de foco hoy?");
        ES.put(R.string.push_request_title, "%1$s quiere ser tu amigo");
        ES.put(R.string.push_request_body, "@%2$s te ha enviado una solicitud de amistad.");
        ES.put(R.string.push_public_title, "Tienes un aviso de un amigo");
        ES.put(R.string.push_public_body, "Desbloquea el móvil para verlo.");
    }

    private static final FriendsPushPayload.Strings STRINGS = (id, args) -> {
        String template = ES.get(id);
        if (template == null) {
            throw new AssertionError("unexpected string id " + id);
        }
        return String.format(template, args);
    };

    private static Map<String, String> data(String type) {
        Map<String, String> d = new HashMap<>();
        d.put("to", "alice");
        d.put("type", type);
        d.put("inboxId", type + "_bob_2026-11-02");
        d.put("fromName", "Laia");
        d.put("fromHandle", "laia");
        return d;
    }

    // ---- parse ---------------------------------------------------------------------------------------------

    @Test
    public void parsesACheerWithEveryKey() {
        FriendsPushPayload p = FriendsPushPayload.parse(data("cheer"));

        assertNotNull(p);
        assertEquals(InboxItem.Type.CHEER, p.type);
        assertEquals("alice", p.to);
        assertEquals("cheer_bob_2026-11-02", p.inboxId);
        assertEquals("Laia", p.fromName);
        assertEquals("laia", p.fromHandle);
    }

    @Test
    public void parsesNudgeAndRequestTypes() {
        assertEquals(InboxItem.Type.NUDGE, FriendsPushPayload.parse(data("nudge")).type);
        assertEquals(InboxItem.Type.REQUEST, FriendsPushPayload.parse(data("request")).type);
    }

    @Test
    public void unknownTypeIsNothing() {
        assertNull(FriendsPushPayload.parse(data("started_studying")));
        assertNull(FriendsPushPayload.parse(data("CHEER")));
        assertNull(FriendsPushPayload.parse(null));
    }

    @Test
    public void anyMissingOrEmptyFieldIsNothing() {
        for (String key : new String[] {"to", "type", "inboxId", "fromName", "fromHandle"}) {
            Map<String, String> missing = data("cheer");
            missing.remove(key);
            assertNull("missing " + key, FriendsPushPayload.parse(missing));

            Map<String, String> blank = data("cheer");
            blank.put(key, " ");
            assertNull("blank " + key, FriendsPushPayload.parse(blank));
        }
    }

    // ---- texto por tipo (d7) -------------------------------------------------------------------------------

    @Test
    public void cheerTextNamesTheSender() {
        FriendsPushPayload.Content c = FriendsPushPayload.parse(data("cheer")).compose(STRINGS);

        assertEquals("Laia te anima", c.title);
        assertEquals("¡Sigue así! Tu amigo te ha enviado un ánimo.", c.body);
    }

    @Test
    public void nudgeAndRequestTextsNameTheSender() {
        FriendsPushPayload.Content nudge = FriendsPushPayload.parse(data("nudge")).compose(STRINGS);
        FriendsPushPayload.Content request = FriendsPushPayload.parse(data("request")).compose(STRINGS);

        assertEquals("Laia te da un empujón", nudge.title);
        assertEquals("¡Vamos! ¿Una sesión de foco hoy?", nudge.body);
        assertEquals("Laia quiere ser tu amigo", request.title);
        assertEquals("@laia te ha enviado una solicitud de amistad.", request.body);
    }

    @Test
    public void publicVersionNeverCarriesTheName() {
        for (String type : new String[] {"cheer", "nudge", "request"}) {
            FriendsPushPayload.Content c = FriendsPushPayload.parse(data(type)).compose(STRINGS);

            assertEquals("Tienes un aviso de un amigo", c.publicTitle);
            assertEquals("Desbloquea el móvil para verlo.", c.publicBody);
            assertFalse(c.publicTitle.contains("Laia") || c.publicBody.contains("Laia"));
            assertFalse(c.publicTitle.contains("laia") || c.publicBody.contains("laia"));
        }
    }

    // ---- id de la notificación ----------------------------------------------------------------------------

    @Test
    public void notificationIdStaysInTheFriendsRange() {
        List<String> ids = new ArrayList<>();
        ids.add("cheer_bob_2026-11-02");
        ids.add("nudge_carol_2026-11-01");
        ids.add("request_zzzzzzzzzzzzzzzzzzzzzzzzzzzz");
        ids.add("");
        ids.add("polygenelubricants"); // hashCode() == Integer.MIN_VALUE
        for (int i = 0; i < 500; i++) {
            ids.add("cheer_uid" + i + "_2026-11-" + (i % 28));
        }
        for (String inboxId : ids) {
            int id = new FriendsPushPayload(InboxItem.Type.CHEER, "alice", inboxId, "Laia", "laia").notificationId();
            assertTrue(inboxId + " -> " + id, id >= 40_000 && id <= 49_999);
        }
    }

    @Test
    public void notificationIdIsStablePerInboxItemAndHandlesNegativeHashes() {
        FriendsPushPayload a = new FriendsPushPayload(InboxItem.Type.CHEER, "alice", "cheer_bob_d", "Laia", "laia");
        FriendsPushPayload again = new FriendsPushPayload(InboxItem.Type.CHEER, "alice", "cheer_bob_d", "L", "l");
        assertEquals(a.notificationId(), again.notificationId());

        // floorMod(Integer.MIN_VALUE, 10_000) = 6_352 (-2_147_483_648 = -214_749 * 10_000 + 6_352); un % daría
        // -3_648 y el id caería fuera del rango.
        FriendsPushPayload min = new FriendsPushPayload(InboxItem.Type.CHEER, "alice", "polygenelubricants",
                "Laia", "laia");
        assertEquals(46_352, min.notificationId());
    }

    // ---- 'to' y la decisión de enseñar (revisión r1) ------------------------------------------------------

    @Test
    public void toIsParsedAndMatchedAgainstTheSession() {
        FriendsPushPayload p = FriendsPushPayload.parse(data("cheer"));

        assertTrue(p.isFor("alice"));
        assertFalse(p.isFor("bob"));
        assertFalse(p.isFor(null));
    }

    @Test
    public void pushForTheSignedInUserIsPostedOnce() {
        List<FriendsPushPayload> posted = new ArrayList<>();
        FriendsPushPayload p = FriendsPushPayload.parse(data("nudge"));

        assertTrue(FriendsPushHandler.deliver(p, "alice", () -> true, posted::add));
        assertEquals(1, posted.size());
        assertEquals("nudge_bob_2026-11-02", posted.get(0).inboxId);
    }

    @Test
    public void pushForAnotherAccountIsDropped() {
        List<FriendsPushPayload> posted = new ArrayList<>();

        FriendsPushHandler.deliver(FriendsPushPayload.parse(data("cheer")), "carol", () -> true, posted::add);

        assertTrue(posted.isEmpty());
    }

    @Test
    public void pushWithoutToIsDropped() {
        List<FriendsPushPayload> posted = new ArrayList<>();
        Map<String, String> noTo = data("cheer");
        noTo.remove("to");

        FriendsPushHandler.deliver(FriendsPushPayload.parse(noTo), "alice", () -> true, posted::add);

        assertTrue(posted.isEmpty());
    }

    @Test
    public void pushWithoutSessionOrWithoutFlagIsDropped() {
        List<FriendsPushPayload> posted = new ArrayList<>();
        FriendsPushPayload p = FriendsPushPayload.parse(data("request"));

        assertFalse(FriendsPushHandler.deliver(p, null, () -> true, posted::add));
        assertFalse(FriendsPushHandler.deliver(p, "alice", () -> false, posted::add));
        assertFalse(FriendsPushHandler.deliver(null, "alice", () -> true, posted::add));
        assertTrue(posted.isEmpty());
    }
}
