package com.example.bbettercalendar.social.inbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** F5-u5: de documentos de {@code users/{me}/inbox} a {@link InboxItem}, con valores escritos a mano. */
public class InboxMappingTest {

    private static Map<String, Object> fields(String type, long createdMillis, boolean read) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        m.put("from", "uidAna");
        m.put("fromHandle", "ana");
        m.put("fromName", "Ana");
        m.put("fromColor", 3L);
        m.put("createdAt", new Timestamp(new Date(createdMillis)));
        m.put("read", read);
        return m;
    }

    private static InboxBackend.Doc doc(String id, Map<String, Object> data) {
        return new InboxBackend.Doc(id, data);
    }

    @Test
    public void aWellFormedDocumentKeepsEveryField() {
        InboxItem item = InboxMapping.item(doc("cheer_uidAna_2026-11-02", fields("cheer", 5_000L, false)));
        assertNotNull(item);
        assertEquals("cheer_uidAna_2026-11-02", item.id);
        assertEquals(InboxItem.Type.CHEER, item.type);
        assertEquals("uidAna", item.fromUid);
        assertEquals("ana", item.fromHandle);
        assertEquals("Ana", item.fromName);
        assertEquals(3, item.fromColor);
        assertEquals(5_000L, item.createdAt.getTime());
        assertFalse(item.read);
    }

    @Test
    public void thethreeTypesOfTheContractAreRecognised() {
        assertEquals(InboxItem.Type.REQUEST, InboxMapping.item(doc("a", fields("request", 1L, true))).type);
        assertEquals(InboxItem.Type.CHEER, InboxMapping.item(doc("b", fields("cheer", 1L, true))).type);
        assertEquals(InboxItem.Type.NUDGE, InboxMapping.item(doc("c", fields("nudge", 1L, true))).type);
        assertTrue(InboxMapping.item(doc("c", fields("nudge", 1L, true))).read);
    }

    @Test
    public void anUnknownTypeIsDropped() {
        assertNull(InboxMapping.item(doc("x", fields("poke", 1L, false))));
    }

    @Test
    public void aDocumentThatDoesNotExistIsDropped() {
        assertNull(InboxMapping.item(doc("x", null)));
    }

    @Test
    public void aMissingOrWronglyTypedFieldIsDropped() {
        Map<String, Object> noFrom = fields("cheer", 1L, false);
        noFrom.remove("from");
        assertNull(InboxMapping.item(doc("a", noFrom)));

        Map<String, Object> colorAsText = fields("cheer", 1L, false);
        colorAsText.put("fromColor", "3");
        assertNull(InboxMapping.item(doc("b", colorAsText)));

        Map<String, Object> readAsText = fields("cheer", 1L, false);
        readAsText.put("read", "false");
        assertNull(InboxMapping.item(doc("c", readAsText)));

        Map<String, Object> nameAsNumber = fields("cheer", 1L, false);
        nameAsNumber.put("fromName", 7L);
        assertNull(InboxMapping.item(doc("d", nameAsNumber)));
    }

    @Test
    public void itemsComeNewestFirstAndMalformedOnesAreSkipped() {
        List<InboxBackend.Doc> docs = Arrays.asList(
                doc("old", fields("cheer", 1_000L, true)),
                doc("broken", fields("poke", 9_000L, false)),
                doc("new", fields("nudge", 3_000L, false)),
                doc("mid", fields("request", 2_000L, false)));
        List<InboxItem> items = InboxMapping.items(docs);
        assertEquals(3, items.size());
        assertEquals("new", items.get(0).id);
        assertEquals("mid", items.get(1).id);
        assertEquals("old", items.get(2).id);
    }

    @Test
    public void anItemWhoseServerTimeIsStillPendingCountsAsTheNewest() {
        Map<String, Object> pending = fields("cheer", 0L, false);
        pending.remove("createdAt");
        List<InboxItem> items = InboxMapping.items(Arrays.asList(
                doc("stamped", fields("cheer", 9_000L, false)), doc("pending", pending)));
        assertEquals("pending", items.get(0).id);
        assertNull(items.get(0).createdAt);
    }

    @Test
    public void theListIsCappedAtTheFiftyNewest() {
        List<InboxBackend.Doc> docs = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            docs.add(doc("i" + i, fields("cheer", 1_000L + i, false)));
        }
        List<InboxItem> items = InboxMapping.items(docs);
        assertEquals(InboxMapping.LIMIT, items.size());
        assertEquals("i59", items.get(0).id);
        assertEquals("i10", items.get(49).id);
    }
}
