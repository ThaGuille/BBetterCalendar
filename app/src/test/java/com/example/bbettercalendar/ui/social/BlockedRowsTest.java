package com.example.bbettercalendar.ui.social;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.social.block.BlockState;
import com.example.bbettercalendar.social.block.BlockedCard;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

/** L-u10: el mapper puro de «Usuarios bloqueados». */
public class BlockedRowsTest {

    private static BlockedCard card(String uid, String handle, String name, int color) {
        return new BlockedCard(uid, handle, name, color, new Date(0));
    }

    @Test
    public void noStateIsLoading() {
        assertEquals(BlockedRows.Status.LOADING, BlockedRows.from(null).status);
        assertEquals(BlockedRows.Status.LOADING, BlockedRows.from(BlockState.loading()).status);
    }

    @Test
    public void signedOutIsGoneAndErrorIsError() {
        assertEquals(BlockedRows.Status.GONE, BlockedRows.from(BlockState.signedOut()).status);
        assertEquals(BlockedRows.Status.ERROR,
                BlockedRows.from(BlockState.error(new RuntimeException("x"))).status);
    }

    @Test
    public void emptyListIsEmpty() {
        BlockedRows rows = BlockedRows.from(BlockState.ready(Collections.<BlockedCard>emptyList()));
        assertEquals(BlockedRows.Status.EMPTY, rows.status);
        assertTrue(rows.rows.isEmpty());
    }

    @Test
    public void rowsKeepRepositoryOrderAndShowHandleWithAt() {
        BlockedRows rows = BlockedRows.from(BlockState.ready(Arrays.asList(
                card("u2", "bob", "Bob", 3), card("u1", "ann", "Ann", 5))));
        assertEquals(BlockedRows.Status.READY, rows.status);
        assertEquals(2, rows.rows.size());
        assertEquals("u2", rows.rows.get(0).uid);
        assertEquals("Bob", rows.rows.get(0).name);
        assertEquals("@bob", rows.rows.get(0).handleText);
        assertEquals(3, rows.rows.get(0).avatarColor);
        assertEquals("u1", rows.rows.get(1).uid);
    }

    @Test
    public void blankNameFallsBackToHandle() {
        BlockedRows rows = BlockedRows.from(BlockState.ready(
                Collections.singletonList(card("u1", "ann", "  ", 0))));
        assertEquals("ann", rows.rows.get(0).name);
    }
}
