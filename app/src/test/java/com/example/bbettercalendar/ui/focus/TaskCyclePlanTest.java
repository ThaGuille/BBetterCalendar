package com.example.bbettercalendar.ui.focus;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * Ciclos de una tarea (rediseño de Proyectos, D-P11): lo que falta de la meta repartido en bloques
 * de la concentración configurada, el último acortado, con descansos sólo entre bloques.
 */
public class TaskCyclePlanTest {

    @Test
    public void twoHoursFromZero_withTwentyFivePlusFive_isFourFullBlocksAndATwentyWithFourRests() {
        TaskCyclePlan plan = TaskCyclePlan.of(120, 0, 25, 5, true);

        assertEquals(Arrays.asList(25, 25, 25, 25, 20), plan.getFocusBlocks());
        assertEquals(Arrays.asList(5, 5, 5, 5), plan.getRests());
    }

    @Test
    public void countsFromWhatTheTaskAlreadyHas() {
        TaskCyclePlan plan = TaskCyclePlan.of(60, 25, 20, 5, true);

        assertEquals(Arrays.asList(20, 15), plan.getFocusBlocks());
        assertEquals(Collections.singletonList(5), plan.getRests());
        assertEquals(35, plan.getRemainingMinutes());
    }

    @Test
    public void aGoalShorterThanOneCycle_isOneShortBlockWithNoRest() {
        TaskCyclePlan plan = TaskCyclePlan.of(10, 0, 25, 5, true);

        assertEquals(Collections.singletonList(10), plan.getFocusBlocks());
        assertTrue(plan.getRests().isEmpty());
    }

    @Test
    public void aReachedGoal_hasNoBlocks() {
        TaskCyclePlan plan = TaskCyclePlan.of(50, 50, 25, 5, true);

        assertTrue(plan.isEmpty());
        assertTrue(plan.getFocusBlocks().isEmpty());
        assertTrue(plan.getRests().isEmpty());
        assertEquals(0, plan.getRemainingMinutes());
    }

    @Test
    public void moreAttributedThanTheGoal_isNeverNegative() {
        TaskCyclePlan plan = TaskCyclePlan.of(30, 45, 25, 5, true);

        assertTrue(plan.isEmpty());
        assertEquals(0, plan.getRemainingMinutes());
    }

    @Test
    public void restDisabled_keepsTheBlocksButHasNoRests() {
        TaskCyclePlan plan = TaskCyclePlan.of(120, 0, 25, 5, false);

        assertEquals(Arrays.asList(25, 25, 25, 25, 20), plan.getFocusBlocks());
        assertTrue(plan.getRests().isEmpty());
    }

    @Test
    public void anExactMultiple_hasNoShortenedBlock() {
        TaskCyclePlan plan = TaskCyclePlan.of(75, 0, 25, 5, true);

        assertEquals(Arrays.asList(25, 25, 25), plan.getFocusBlocks());
        assertEquals(Arrays.asList(5, 5), plan.getRests());
    }

    @Test
    public void blocksInMillis_areWhatTheTimerRuns() {
        TaskCyclePlan plan = TaskCyclePlan.of(60, 25, 20, 5, true);

        assertArrayEquals(new long[]{20 * 60_000L, 15 * 60_000L}, plan.getFocusBlocksMillis());
        assertEquals(2, plan.getBlockCount());
        assertFalse(plan.isEmpty());
    }
}
