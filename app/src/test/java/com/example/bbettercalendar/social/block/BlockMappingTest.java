package com.example.bbettercalendar.social.block;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** L-u9: {@link BlockMapping} puro: respuesta de la callable y documentos de {@code blocks/me/blocked}. */
public class BlockMappingTest {

    private static Map<String, Object> fields(Object since) {
        Map<String, Object> m = new HashMap<>();
        m.put("handle", "ana");
        m.put("displayName", "Ana");
        m.put("avatarColor", 3L);
        if (since != null) {
            m.put("since", since);
        }
        return m;
    }

    private static BlockBackend.Doc doc(String id, long millis) {
        return new BlockBackend.Doc(id, fields(new Timestamp(new Date(millis))));
    }

    @Test
    public void blockedStatusMapsToBlocked() {
        assertEquals(BlockResult.BLOCKED, BlockMapping.fromData(Collections.<String, Object>singletonMap("status", "blocked")));
    }

    @Test
    public void anyOtherStatusOrNoBodyMapsToError() {
        assertEquals(BlockResult.ERROR, BlockMapping.fromData(null));
        assertEquals(BlockResult.ERROR, BlockMapping.fromData(new HashMap<String, Object>()));
        assertEquals(BlockResult.ERROR, BlockMapping.fromData(Collections.<String, Object>singletonMap("status", "sent")));
        assertEquals(BlockResult.ERROR, BlockMapping.fromData(Collections.<String, Object>singletonMap("status", 1L)));
    }

    @Test
    public void aWellFormedDocumentBecomesACard() {
        BlockedCard c = BlockMapping.card(doc("u1", 5_000L));
        assertEquals("u1", c.uid);
        assertEquals("ana", c.handle);
        assertEquals("Ana", c.displayName);
        assertEquals(3, c.avatarColor);
        assertEquals(5_000L, c.since.getTime());
    }

    @Test
    public void aPendingServerTimestampIsAcceptedAsNullSince() {
        BlockedCard c = BlockMapping.card(new BlockBackend.Doc("u1", fields(null)));
        assertNull(c.since);
    }

    @Test
    public void malformedOrMissingDocumentsAreDropped() {
        Map<String, Object> noHandle = fields(new Timestamp(new Date(1L)));
        noHandle.remove("handle");
        Map<String, Object> badSince = fields("yesterday");
        assertNull(BlockMapping.card(new BlockBackend.Doc("gone", null)));
        assertNull(BlockMapping.card(new BlockBackend.Doc("a", noHandle)));
        assertNull(BlockMapping.card(new BlockBackend.Doc("c", badSince)));
    }

    @Test
    public void aMissingOrNonNumericColorFallsBackToZeroInsteadOfDroppingTheRow() {
        Map<String, Object> stringColor = fields(new Timestamp(new Date(1L)));
        stringColor.put("avatarColor", "");
        Map<String, Object> noColor = fields(new Timestamp(new Date(1L)));
        noColor.remove("avatarColor");
        BlockedCard a = BlockMapping.card(new BlockBackend.Doc("a", stringColor));
        BlockedCard b = BlockMapping.card(new BlockBackend.Doc("b", noColor));
        assertNotNull(a);
        assertEquals(0, a.avatarColor);
        assertNotNull(b);
        assertEquals(0, b.avatarColor);
    }

    @Test
    public void cardsAreNewestFirstWithPendingOnTopAndTiesByUid() {
        List<BlockBackend.Doc> docs = new ArrayList<>(Arrays.asList(
                doc("old", 1_000L), doc("pending", 0L), doc("zz", 2_000L), doc("aa", 2_000L)));
        docs.set(1, new BlockBackend.Doc("pending", fields(null)));
        List<BlockedCard> cards = BlockMapping.cards(docs);
        assertEquals(4, cards.size());
        assertEquals("pending", cards.get(0).uid);
        assertEquals("aa", cards.get(1).uid);
        assertEquals("zz", cards.get(2).uid);
        assertEquals("old", cards.get(3).uid);
    }
}
