package com.example.bbettercalendar.ui.social;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.social.friends.FriendCard;
import com.example.bbettercalendar.social.friends.FriendRequest;
import com.example.bbettercalendar.social.inbox.InboxItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** F5-u7: el mapper puro de la bandeja de la campana. */
public class InboxRowsTest {

    private static final long NOW = 1_000_000_000_000L;
    private static final long MIN = 60_000L;

    private static InboxItem item(String id, InboxItem.Type type, String from, Long createdAt, boolean read) {
        return new InboxItem(id, type, from, from, from.toUpperCase(), 3,
                createdAt == null ? null : new Date(createdAt), read);
    }

    private static Set<String> set(String... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    @Test
    public void newestFirstAndPendingServerTimeOnTop() {
        List<InboxRows.Row> rows = InboxRows.build(Arrays.asList(
                item("old", InboxItem.Type.CHEER, "b", NOW - 5 * MIN, true),
                item("pending", InboxItem.Type.NUDGE, "b", null, false),
                item("mid", InboxItem.Type.CHEER, "c", NOW - MIN, true)),
                set("b", "c"), Collections.<String>emptySet(), NOW);
        assertEquals("pending", rows.get(0).id);
        assertEquals("mid", rows.get(1).id);
        assertEquals("old", rows.get(2).id);
    }

    @Test
    public void ageBucketsNowMinutesHoursDays() {
        List<InboxRows.Row> rows = InboxRows.build(Arrays.asList(
                item("a", InboxItem.Type.CHEER, "b", NOW - 30_000L, true),
                item("b", InboxItem.Type.CHEER, "b", NOW - 59 * MIN, true),
                item("c", InboxItem.Type.CHEER, "b", NOW - 3 * 60 * MIN, true),
                item("d", InboxItem.Type.CHEER, "b", NOW - 3 * 24 * 60 * MIN, true)),
                set("b"), Collections.<String>emptySet(), NOW);
        assertEquals(InboxRows.AgeUnit.NOW, rows.get(0).ageUnit);
        assertEquals(0, rows.get(0).ageValue);
        assertEquals(InboxRows.AgeUnit.MINUTES, rows.get(1).ageUnit);
        assertEquals(59, rows.get(1).ageValue);
        assertEquals(InboxRows.AgeUnit.HOURS, rows.get(2).ageUnit);
        assertEquals(3, rows.get(2).ageValue);
        assertEquals(InboxRows.AgeUnit.DAYS, rows.get(3).ageUnit);
        assertEquals(3, rows.get(3).ageValue);
    }

    @Test
    public void futureTimestampCountsAsNow() {
        List<InboxRows.Row> rows = InboxRows.build(Collections.singletonList(
                item("a", InboxItem.Type.CHEER, "b", NOW + 10 * MIN, true)),
                set("b"), Collections.<String>emptySet(), NOW);
        assertEquals(InboxRows.AgeUnit.NOW, rows.get(0).ageUnit);
    }

    @Test
    public void newAccentComesFromTheIdsUnreadAtOpening() {
        List<InboxItem> items = Arrays.asList(
                item("a", InboxItem.Type.CHEER, "b", NOW - MIN, false),
                item("b", InboxItem.Type.NUDGE, "b", NOW - 2 * MIN, true));
        Set<String> opening = InboxRows.unreadIds(items);
        assertEquals(set("a"), opening);
        // Aunque ya estén leídos (se marcaron al abrir), el acento sigue durante la visita.
        List<InboxRows.Row> rows = InboxRows.build(
                Arrays.asList(item("a", InboxItem.Type.CHEER, "b", NOW - MIN, true), items.get(1)),
                set("b"), opening, NOW);
        assertTrue(rows.get(0).isNew);
        assertFalse(rows.get(1).isNew);
    }

    @Test
    public void destinationOfCheerAndNudgeIsTheProfileOnlyForAFriend() {
        assertEquals(InboxRows.Destination.PROFILE, InboxRows.destination(InboxItem.Type.CHEER, true));
        assertEquals(InboxRows.Destination.PROFILE, InboxRows.destination(InboxItem.Type.NUDGE, true));
        assertEquals(InboxRows.Destination.NONE, InboxRows.destination(InboxItem.Type.CHEER, false));
        assertEquals(InboxRows.Destination.NONE, InboxRows.destination(InboxItem.Type.NUDGE, false));
    }

    @Test
    public void destinationOfRequestDependsOnBeingAFriendNow() {
        assertEquals(InboxRows.Destination.PROFILE, InboxRows.destination(InboxItem.Type.REQUEST, true));
        assertEquals(InboxRows.Destination.FRIEND_REQUESTS,
                InboxRows.destination(InboxItem.Type.REQUEST, false));
    }

    @Test
    public void buildUsesTheFriendSetForTheDestination() {
        List<InboxRows.Row> rows = InboxRows.build(Arrays.asList(
                item("r1", InboxItem.Type.REQUEST, "carol", NOW - MIN, false),
                item("r2", InboxItem.Type.REQUEST, "bob", NOW - 2 * MIN, false)),
                set("bob"), Collections.<String>emptySet(), NOW);
        assertEquals(InboxRows.Destination.FRIEND_REQUESTS, rows.get(0).destination);
        assertEquals(InboxRows.Destination.PROFILE, rows.get(1).destination);
        assertEquals("bob", rows.get(1).fromUid);
    }

    @Test
    public void bellCountAddsUnreadAndPendingTreatingNullAsZero() {
        assertEquals(0, InboxRows.bellCount(null, null));
        assertEquals(2, InboxRows.bellCount(2, null));
        assertEquals(3, InboxRows.bellCount(null, 3));
        assertEquals(5, InboxRows.bellCount(2, 3));
        assertEquals(0, InboxRows.bellCount(-1, -4));
    }

    @Test
    public void emptyInboxGivesNoRows() {
        assertTrue(InboxRows.build(Collections.<InboxItem>emptyList(), Collections.<String>emptySet(),
                Collections.<String>emptySet(), NOW).isEmpty());
        assertTrue(InboxRows.unreadIds(Collections.<InboxItem>emptyList()).isEmpty());
    }

    private static FriendRequest request(String from, FriendRequest.Status status) {
        return new FriendRequest(from + "_me", new FriendCard(from, from, from.toUpperCase(), 2),
                new FriendCard("me", "me", "Me", 1), status, null, new Date(NOW - MIN));
    }

    @Test
    public void pendingRequestWithoutInboxRowGetsASyntheticRow() {
        List<InboxItem> items = InboxRows.withPendingRequests(Collections.<InboxItem>emptyList(),
                Arrays.asList(request("a", FriendRequest.Status.PENDING)));
        assertEquals(1, items.size());
        assertEquals("request_a", items.get(0).id);
        assertEquals(InboxItem.Type.REQUEST, items.get(0).type);
        assertTrue(items.get(0).read);
        List<InboxRows.Row> rows = InboxRows.build(items, Collections.<String>emptySet(),
                Collections.<String>emptySet(), NOW);
        assertEquals(InboxRows.Destination.FRIEND_REQUESTS, rows.get(0).destination);
    }

    @Test
    public void requestsWithRowOrNotPendingAreNotAdded() {
        List<InboxItem> base = Arrays.asList(item("request_a", InboxItem.Type.REQUEST, "a", NOW, false));
        List<InboxItem> items = InboxRows.withPendingRequests(base, Arrays.asList(
                request("a", FriendRequest.Status.PENDING), request("b", FriendRequest.Status.FAILED)));
        assertEquals(1, items.size());
    }
}
