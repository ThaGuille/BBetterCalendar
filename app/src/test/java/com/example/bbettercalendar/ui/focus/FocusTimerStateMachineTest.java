package com.example.bbettercalendar.ui.focus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.Phase;
import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.Settings;
import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.Snapshot;
import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.State;
import com.example.bbettercalendar.ui.home.FocusTarget;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * JVM tests for {@link FocusTimerStateMachine} (migración visual, u2): the transitions ported 1:1
 * from the old HomeFragment — start, pause/resume, focus->rest->focus cycles, the "cycle finished"
 * signal, cancel, skip rest, background fail, settings changes and snapshot/restore.
 */
public class FocusTimerStateMachineTest {

    private static final long FOCUS = 25 * 60_000L;
    private static final long REST = 5 * 60_000L;

    private long now;
    private Settings settings;
    private final List<String> calls = new ArrayList<>();
    private FocusTimerStateMachine machine;

    private final FocusTimerStateMachine.Listener listener = new FocusTimerStateMachine.Listener() {
        @Override
        public void onFocusCompleted(long sessionMillis) {
            calls.add("focus:" + sessionMillis);
        }

        @Override
        public void onAllCyclesCompleted(int cycles) {
            calls.add("allCycles:" + cycles);
        }

        @Override
        public void onFailed() {
            calls.add("failed");
        }
    };

    @Before
    public void setUp() {
        now = 1_000_000L;
        // rest on, 2 cycles, finite, auto-cycle off
        settings = new Settings(FOCUS, REST, true, 2, false, false);
        machine = newMachine();
    }

    private FocusTimerStateMachine newMachine() {
        return new FocusTimerStateMachine(() -> now, () -> settings, listener);
    }

    /** Moves the fake clock and lets the machine react like the ticker would. */
    private boolean elapse(long millis) {
        now += millis;
        return machine.advance();
    }

    // ------------------------------------------------------------------- start / pause / resume

    @Test
    public void initialState_isIdleShowingConfiguredFocus() {
        assertEquals(State.STOPPED, machine.getState());
        assertEquals(Phase.FOCUS, machine.getPhase());
        assertEquals(FOCUS, machine.getRemainingMillis());
        assertEquals(0L, machine.getEndsAtMillis());
        assertEquals(1, machine.getCurrentCycle());
    }

    @Test
    public void toggleFromStopped_startsConcentrationAnchoredToEndInstant() {
        assertTrue(machine.toggle());

        assertEquals(State.RUNNING, machine.getState());
        assertEquals(now + FOCUS, machine.getEndsAtMillis());
        assertFalse(elapse(10_000));
        assertEquals(FOCUS - 10_000, machine.getRemainingMillis());
    }

    @Test
    public void pauseFreezesRemaining_resumeContinuesFromIt() {
        machine.toggle();
        elapse(60_000);

        assertFalse("toggle while running pauses", machine.toggle());
        assertEquals(State.PAUSED, machine.getState());
        assertEquals(0L, machine.getEndsAtMillis());

        elapse(10 * 60_000); // time spent paused does not count
        assertEquals(FOCUS - 60_000, machine.getRemainingMillis());

        assertTrue(machine.toggle());
        assertEquals(State.RUNNING, machine.getState());
        assertEquals(now + FOCUS - 60_000, machine.getEndsAtMillis());
    }

    @Test
    public void pauseAndResume_inRest_keepRestStates() {
        machine.toggle();
        elapse(FOCUS);
        assertEquals(State.RUNNING_REST, machine.getState());

        assertTrue(machine.pause());
        assertEquals(State.PAUSED_REST, machine.getState());
        assertTrue(machine.resume());
        assertEquals(State.RUNNING_REST, machine.getState());
    }

    @Test
    public void advanceBeforeEnd_doesNothing() {
        machine.toggle();
        assertFalse(elapse(FOCUS - 1));
        assertEquals(State.RUNNING, machine.getState());
        assertEquals(1L, machine.getRemainingMillis());
        assertTrue(calls.isEmpty());
    }

    // --------------------------------------------------------------------------- cycles

    @Test
    public void focusCompletion_recordsSessionAndStartsRest() {
        machine.toggle();
        assertTrue(elapse(FOCUS));

        assertEquals("[focus:" + FOCUS + "]", calls.toString());
        assertEquals(State.RUNNING_REST, machine.getState());
        assertEquals(Phase.REST, machine.getPhase());
        assertEquals(REST, machine.getCountdownMillis());
        assertEquals(REST, machine.getRemainingMillis());
        assertEquals("a rest belongs to the cycle it follows", 1, machine.getCurrentCycle());
    }

    @Test
    public void restCompletion_withoutAutoCycle_goesIdleAndCountsTheCycle() {
        machine.toggle();
        elapse(FOCUS);
        assertTrue(elapse(REST));

        assertEquals(State.STOPPED, machine.getState());
        assertEquals(1, machine.getCyclesCompleted());
        assertEquals(2, machine.getCurrentCycle());
        assertEquals(FOCUS, machine.getRemainingMillis());
    }

    @Test
    public void autoCycle_runsAllCyclesThenSignalsCompletionAndResets() {
        settings = new Settings(FOCUS, REST, true, 2, false, true);
        machine.toggle();
        elapse(FOCUS);          // focus 1 -> rest 1
        elapse(REST);           // rest 1 -> focus 2 starts by itself
        assertEquals(State.RUNNING, machine.getState());
        assertEquals(2, machine.getCurrentCycle());
        elapse(FOCUS);          // focus 2 -> rest 2
        elapse(REST);           // rest 2 -> all cycles done

        assertEquals("[focus:" + FOCUS + ", focus:" + FOCUS + ", allCycles:2]", calls.toString());
        assertEquals(State.STOPPED, machine.getState());
        assertEquals(0, machine.getCyclesCompleted());
        assertEquals(FOCUS, machine.getRemainingMillis());
    }

    @Test
    public void manualCycles_focusAfterLastRest_signalsCompletionWithoutRest() {
        for (int i = 0; i < 2; i++) {
            machine.toggle();
            elapse(FOCUS);
            elapse(REST);
        }
        assertEquals(2, machine.getCyclesCompleted());

        machine.toggle();
        elapse(FOCUS);

        assertEquals(State.STOPPED, machine.getState());
        assertEquals(0, machine.getCyclesCompleted());
        assertTrue(calls.contains("allCycles:2"));
    }

    @Test
    public void infiniteCycles_neverSignalCompletion() {
        settings = new Settings(FOCUS, REST, true, 1, true, true);
        machine.toggle();
        for (int i = 0; i < 5; i++) {
            elapse(FOCUS);
            elapse(REST);
        }
        assertEquals(State.RUNNING, machine.getState());
        assertEquals(5, machine.getCyclesCompleted());
        assertFalse(calls.toString().contains("allCycles"));
    }

    @Test
    public void restDisabled_focusCompletionGoesIdleWithoutCountingCycles() {
        settings = new Settings(FOCUS, REST, false, 2, false, true);
        machine.toggle();
        elapse(FOCUS);

        assertEquals(State.STOPPED, machine.getState());
        assertEquals(0, machine.getCyclesCompleted());
        assertEquals(FOCUS, machine.getRemainingMillis());
        assertEquals("[focus:" + FOCUS + "]", calls.toString());
    }

    @Test
    public void toggleFromStoppedRest_startsConfiguredRest() {
        machine.restore(new Snapshot(State.STOPPED_REST, REST, 0L, REST, FOCUS, 0));

        assertTrue(machine.toggle());
        assertEquals(State.RUNNING_REST, machine.getState());
        assertEquals(REST, machine.getRemainingMillis());
    }

    // -------------------------------------------------------------------- bound sessions

    @Test
    public void boundSession_reportsItsOwnLength() {
        long target = 50 * 60_000L;
        assertTrue(machine.startFocus(target));
        elapse(target);

        assertEquals("[focus:" + target + "]", calls.toString());
        assertFalse("startFocus is a no-op unless idle", machine.startFocus(FOCUS));
    }

    @Test
    public void autoCycleRestart_keepsPreviousSessionLength_likeTheOriginal() {
        // Comportamiento heredado (lastTimerTime no se actualizaba en el auto-ciclo): ver
        // migracion-visual-stitch-questions.md (u2).
        settings = new Settings(FOCUS, REST, true, 3, false, true);
        long target = 50 * 60_000L;
        machine.startFocus(target);
        elapse(target);
        elapse(REST);                      // auto-cycle: next concentration with FOCUS length
        assertEquals(FOCUS, machine.getCountdownMillis());
        elapse(FOCUS);

        assertEquals("focus:" + target, calls.get(1));
    }

    // ------------------------------------------------------------------- cancel / skip rest

    @Test
    public void cancel_dropsTheWholeSessionWithoutFailing() {
        machine.toggle();
        elapse(FOCUS);
        elapse(REST);                      // 1 cycle done
        machine.toggle();
        elapse(60_000);

        assertTrue(machine.cancel());
        assertEquals(State.STOPPED, machine.getState());
        assertEquals(0, machine.getCyclesCompleted());
        assertEquals(FOCUS, machine.getRemainingMillis());
        assertFalse(calls.contains("failed"));
        assertFalse("nothing to cancel when idle", machine.cancel());
    }

    @Test
    public void skipRest_countsTheCycleAndGoesIdle() {
        settings = new Settings(FOCUS, REST, true, 4, false, true);
        machine.toggle();
        elapse(FOCUS);
        machine.pause();                   // works from PAUSED_REST too

        assertTrue(machine.skipRest());
        assertEquals(State.STOPPED, machine.getState());
        assertEquals("auto-cycle does not start the next focus on skip", 1,
                machine.getCyclesCompleted());
        assertEquals(FOCUS, machine.getRemainingMillis());
    }

    @Test
    public void skipRest_outsideRest_isNoOp() {
        machine.toggle();
        assertFalse(machine.skipRest());
        assertEquals(State.RUNNING, machine.getState());
    }

    // ---------------------------------------------------------------------- background fail

    @Test
    public void backgroundGrace_armsOnlyForUnarmedRunningConcentration() {
        assertFalse(machine.shouldArmBackgroundGrace(false)); // idle
        machine.toggle();
        assertTrue(machine.shouldArmBackgroundGrace(false));
        assertFalse("block mode keeps the user in", machine.shouldArmBackgroundGrace(true));
        machine.pause();
        assertFalse(machine.shouldArmBackgroundGrace(false));
        machine.resume();
        elapse(FOCUS);                     // now resting
        assertFalse(machine.shouldArmBackgroundGrace(false));
    }

    @Test
    public void fail_stopsConcentrationKeepsCyclesAndFreezesDisplay() {
        machine.toggle();
        elapse(FOCUS);
        elapse(REST);                      // 1 cycle done
        machine.toggle();
        elapse(90_000);

        assertTrue(machine.fail());
        assertEquals(State.STOPPED, machine.getState());
        assertTrue(calls.contains("failed"));
        assertEquals("fail does not reset cycles", 1, machine.getCyclesCompleted());
        assertEquals("display frozen until the alert closes", FOCUS - 90_000,
                machine.getRemainingMillis());

        machine.resetIdleDisplay();        // old fail-alert close -> resetTimer()
        assertEquals(FOCUS, machine.getRemainingMillis());
    }

    @Test
    public void fail_outsideRunningConcentration_isNoOp() {
        assertFalse(machine.fail());
        machine.toggle();
        machine.pause();
        assertFalse(machine.fail());
        machine.resume();
        elapse(FOCUS);                     // resting
        assertFalse(machine.fail());
        assertFalse(calls.contains("failed"));
    }

    // ------------------------------------------------------------------- settings changes

    @Test
    public void disablingRest_killsRunningRestAndResetsCycles() {
        settings = new Settings(FOCUS, REST, true, 4, false, true);
        machine.toggle();
        elapse(FOCUS);
        elapse(REST);
        elapse(FOCUS);                     // second rest running, 1 cycle done
        assertEquals(State.RUNNING_REST, machine.getState());

        settings = new Settings(FOCUS, REST, false, 4, false, true);
        machine.onSettingsChanged(settings);

        assertEquals(State.STOPPED, machine.getState());
        assertEquals(0, machine.getCyclesCompleted());
        assertEquals(FOCUS, machine.getRemainingMillis());
    }

    @Test
    public void disablingAutoCycle_resetsCycles_andIdleDisplayFollowsNewLength() {
        machine.toggle();
        elapse(FOCUS);
        elapse(REST);
        assertEquals(1, machine.getCyclesCompleted());

        long newFocus = 40 * 60_000L;
        settings = new Settings(newFocus, REST, true, 4, false, false);
        machine.onSettingsChanged(settings);

        assertEquals(0, machine.getCyclesCompleted());
        assertEquals(newFocus, machine.getRemainingMillis());
        machine.toggle();
        assertEquals(newFocus, machine.getCountdownMillis());
    }

    // ------------------------------------------------------------------- snapshot / restore

    @Test
    public void restoreRunning_countsTimeSpentDead() {
        machine.toggle();
        elapse(60_000);
        Snapshot saved = machine.snapshot();

        now += 120_000;                    // process dead for 2 minutes
        machine = newMachine();
        machine.restore(saved);

        assertEquals(State.RUNNING, machine.getState());
        assertEquals(FOCUS - 180_000, machine.getRemainingMillis());
    }

    @Test
    public void restoreRunning_alreadyExpired_finishesOnNextAdvance() {
        machine.toggle();
        Snapshot saved = machine.snapshot();

        now += FOCUS + 1;
        machine = newMachine();
        machine.restore(saved);

        assertTrue(machine.advance());
        assertEquals("[focus:" + FOCUS + "]", calls.toString());
        assertEquals(State.RUNNING_REST, machine.getState());
    }

    @Test
    public void restorePaused_keepsFrozenRemaining() {
        machine.toggle();
        elapse(30_000);
        machine.pause();
        Snapshot saved = machine.snapshot();

        now += 3_600_000;
        machine = newMachine();
        machine.restore(saved);

        assertEquals(State.PAUSED, machine.getState());
        assertEquals(FOCUS - 30_000, machine.getRemainingMillis());
        assertEquals(FOCUS, machine.getCountdownMillis());
    }

    // ------------------------------------------- mejora-visual u1: pausas y minutos (FocusEvent v15)

    @Test
    public void focusPauses_areCountedAndReportedWithTheCompletion() {
        final int[] pausesAtCompletion = {-1};
        final long[] elapsedAtCompletion = {-1L};
        machine = new FocusTimerStateMachine(() -> now, () -> settings, new FocusTimerStateMachine.Listener() {
            @Override
            public void onFocusCompleted(long sessionMillis) {
                pausesAtCompletion[0] = machine.getFocusPauseCount();
                elapsedAtCompletion[0] = machine.getLastFocusElapsedMillis();
            }

            @Override
            public void onAllCyclesCompleted(int cycles) { }

            @Override
            public void onFailed() { }
        });
        machine.toggle();
        machine.pause();
        machine.resume();
        machine.toggle(); // toggle while running is a pause too
        elapse(5 * 60_000); // paused time does not count
        machine.toggle();
        elapse(FOCUS);

        assertEquals(2, pausesAtCompletion[0]);
        assertEquals(FOCUS, elapsedAtCompletion[0]);
    }

    @Test
    public void restPauses_doNotCount_andANewConcentrationStartsAtZero() {
        machine.toggle();
        machine.pause();
        machine.resume();
        elapse(FOCUS);                      // -> RUNNING_REST
        machine.pause();                    // rest pause
        assertEquals(1, machine.getFocusPauseCount());
        machine.skipRest();
        machine.toggle();                   // next concentration
        assertEquals(0, machine.getFocusPauseCount());
    }

    @Test
    public void fail_reportsTheRunTimeBeforeFailing() {
        machine.toggle();
        elapse(7 * 60_000);
        machine.pause();
        elapse(60_000);
        machine.resume();
        elapse(3 * 60_000);

        assertTrue(machine.fail());

        assertEquals(10 * 60_000L, machine.getLastFocusElapsedMillis());
        assertEquals(1, machine.getFocusPauseCount());
    }

    @Test
    public void pauseCount_survivesSnapshotAndRestore() {
        machine.toggle();
        machine.pause();
        machine.resume();
        machine.pause();
        Snapshot saved = machine.snapshot();

        machine = newMachine();
        machine.restore(saved);

        assertEquals(2, machine.getFocusPauseCount());
    }

    // ------------------------------------------------- task plans (rediseño de Proyectos, D-P11)

    private static final long MIN = 60_000L;

    /** 60 min goal with 25 already done, cycles of 20 + 5: blocks of 20 and 15. */
    private static final long[] TWO_BLOCKS = {20 * MIN, 15 * MIN};

    @Test
    public void plan_eachFocusBlockRunsAndRecordsItsOwnLength() {
        settings = new Settings(FOCUS, REST, true, 4, false, true);

        assertTrue(machine.startPlan(TWO_BLOCKS));
        assertEquals(State.RUNNING, machine.getState());
        assertEquals(20 * MIN, machine.getRemainingMillis());

        elapse(20 * MIN);
        elapse(REST);
        assertEquals(State.RUNNING, machine.getState());
        assertEquals(15 * MIN, machine.getRemainingMillis());
        elapse(15 * MIN);

        assertEquals(Arrays.asList("focus:" + 20 * MIN, "focus:" + 15 * MIN, "allCycles:2"), calls);
    }

    @Test
    public void plan_restsBetweenBlocksFollowAutoCycleOff_playStartsTheNextBlock() {
        // auto-cycle off (setUp): after the rest the timer waits, showing the next block.
        machine.startPlan(TWO_BLOCKS);
        elapse(20 * MIN);
        assertEquals(State.RUNNING_REST, machine.getState());
        assertEquals(REST, machine.getRemainingMillis());

        elapse(REST);
        assertEquals(State.STOPPED, machine.getState());
        assertEquals(15 * MIN, machine.getRemainingMillis());

        assertTrue(machine.toggle());
        assertEquals(State.RUNNING, machine.getState());
        assertEquals(15 * MIN, machine.getRemainingMillis());
    }

    @Test
    public void plan_withRestDisabled_runsTheBlocksBackToBack() {
        settings = new Settings(FOCUS, REST, false, 4, false, false);
        machine.startPlan(TWO_BLOCKS);

        elapse(20 * MIN);

        assertEquals(State.RUNNING, machine.getState());
        assertEquals(15 * MIN, machine.getRemainingMillis());
        assertEquals(Collections.singletonList("focus:" + 20 * MIN), calls);
    }

    @Test
    public void plan_endsAfterItsLastBlock_evenWithInfiniteCycles() {
        settings = new Settings(FOCUS, REST, true, 4, true, true);
        machine.startPlan(new long[]{10 * MIN});

        elapse(10 * MIN);

        assertEquals(State.STOPPED, machine.getState());
        assertEquals(Arrays.asList("focus:" + 10 * MIN, "allCycles:1"), calls);
        assertFalse(machine.hasPlan());
        assertEquals("idle shows the configured length again", FOCUS, machine.getRemainingMillis());
    }

    @Test
    public void plan_endsAfterItsLastBlock_evenWhenMoreCyclesAreConfigured() {
        // 1 cycle configured, 3 blocks: the plan, not the cycle count, decides the end.
        settings = new Settings(FOCUS, REST, true, 1, false, true);
        machine.startPlan(new long[]{5 * MIN, 5 * MIN, 5 * MIN});

        elapse(5 * MIN);
        elapse(REST);
        elapse(5 * MIN);
        elapse(REST);
        elapse(5 * MIN);

        assertEquals(Arrays.asList("focus:" + 5 * MIN, "focus:" + 5 * MIN, "focus:" + 5 * MIN,
                "allCycles:3"), calls);
        assertEquals(State.STOPPED, machine.getState());
    }

    @Test
    public void plan_survivesSnapshotAndRestore_midSecondBlock() {
        settings = new Settings(FOCUS, REST, true, 4, false, true);
        machine.startPlan(TWO_BLOCKS);
        elapse(20 * MIN);
        elapse(REST);
        elapse(5 * MIN);
        Snapshot saved = machine.snapshot();

        machine = newMachine();
        machine.restore(saved);

        assertTrue(machine.hasPlan());
        assertEquals(1, machine.getPlanBlockIndex());
        assertEquals(2, machine.getPlanBlockCount());
        assertEquals(10 * MIN, machine.getRemainingMillis());
        elapse(10 * MIN);
        assertEquals(Arrays.asList("focus:" + 20 * MIN, "focus:" + 15 * MIN, "allCycles:2"), calls);
    }

    @Test
    public void plan_cannotStartOverARunningSession() {
        machine.toggle();

        assertFalse(machine.startPlan(TWO_BLOCKS));
        assertFalse(machine.hasPlan());
        assertEquals(FOCUS, machine.getRemainingMillis());
    }

    @Test
    public void plan_cancelDropsItAndReportsTheFocusedPartOfTheBlock() {
        machine.startPlan(TWO_BLOCKS);
        elapse(3 * MIN + 30_000);

        assertEquals(3 * MIN + 30_000, machine.getCurrentFocusElapsedMillis());
        assertTrue(machine.cancel());
        assertFalse(machine.hasPlan());
        assertEquals(State.STOPPED, machine.getState());
        assertEquals(FOCUS, machine.getRemainingMillis());
        assertTrue(calls.isEmpty());
    }

    @Test
    public void plan_pendingBetweenBlocks_canBeCancelled() {
        machine.startPlan(TWO_BLOCKS);
        elapse(20 * MIN);
        elapse(REST); // auto-cycle off: STOPPED, next block pending

        assertEquals(0L, machine.getCurrentFocusElapsedMillis());
        assertTrue(machine.cancel());
        assertFalse(machine.hasPlan());
        assertFalse("a plain idle timer still has nothing to cancel", machine.cancel());
    }

    // ------------------------- starting a task over this machine (TaskFocusStarter, §3.5, D-P12)

    private static final int TASK_ID = 42;
    private static final int OTHER_TASK_ID = 7;

    private boolean accessibilityOn;
    private int blockUnavailableEvents;

    private final TaskFocusStarter.BlockAccess blockAccess = new TaskFocusStarter.BlockAccess() {
        @Override
        public boolean isAvailable() {
            return accessibilityOn;
        }

        @Override
        public void onUnavailable() {
            blockUnavailableEvents++;
        }
    };

    @After
    public void unbindTask() {
        FocusTarget.clear();
    }

    private TaskFocusStarter newStarter() {
        return new TaskFocusStarter(machine, () -> settings, blockAccess);
    }

    /** «Repasar el tema 4»: 60 min goal, 25 already focused. */
    private static TaskFocusRequest task(boolean blockOnFocus) {
        return new TaskFocusRequest(TASK_ID, "Repasar el tema 4", 60, 25, blockOnFocus, 3,
                "Estudios", "book", 2);
    }

    @Test
    public void taskStart_whenIdle_bindsTheTaskAndRunsItsCyclesFromWhatItHas() {
        TaskFocusStarter starter = newStarter();

        assertTrue(starter.startTaskFocus(task(false)));

        assertEquals(TASK_ID, FocusTarget.getEntryId());
        assertEquals(3, FocusTarget.getProjectId());
        assertFalse("started here: Home must not auto-start it again",
                FocusTarget.consumePendingAutoStart());
        assertEquals(State.RUNNING, machine.getState());
        assertTrue(machine.hasPlan());
        // 35 min left in cycles of 25: blocks of 25 and 10.
        assertEquals(2, machine.getPlanBlockCount());
        assertEquals(25 * MIN, machine.getRemainingMillis());
    }

    @Test
    public void taskStart_whileASessionRuns_changesNothingAndReturnsFalse() {
        FocusTarget.set(OTHER_TASK_ID, "Otra", 0);
        TaskFocusStarter starter = newStarter();
        accessibilityOn = true;
        machine.startFocus(FOCUS);
        elapse(MIN);

        assertFalse(starter.startTaskFocus(task(true)));

        assertEquals("the running session keeps its task", OTHER_TASK_ID, FocusTarget.getEntryId());
        assertFalse("block mode not armed by the refused task", starter.isBlockModeArmed());
        assertEquals(0, blockUnavailableEvents);
        assertFalse(machine.hasPlan());
        assertEquals(State.RUNNING, machine.getState());
        assertEquals(FOCUS - MIN, machine.getRemainingMillis());
    }

    @Test
    public void taskStart_whilePaused_alsoChangesNothing() {
        TaskFocusStarter starter = newStarter();
        machine.startFocus(FOCUS);
        machine.pause();

        assertFalse(starter.startTaskFocus(task(true)));

        assertEquals(0, FocusTarget.getEntryId());
        assertEquals(0, blockUnavailableEvents);
        assertEquals(State.PAUSED, machine.getState());
    }

    @Test
    public void taskStart_withReachedGoal_runsABoundSessionOfTheConfiguredLength() {
        TaskFocusStarter starter = newStarter();

        assertTrue(starter.startTaskFocus(new TaskFocusRequest(TASK_ID, "Hecha", 50, 50, false, 0,
                null, null, 0)));

        assertEquals(TASK_ID, FocusTarget.getEntryId());
        assertFalse(machine.hasPlan());
        assertEquals(FOCUS, machine.getRemainingMillis());
    }

    @Test
    public void blockOnFocus_withAccessibilityOn_armsTheBlockAtStart() {
        accessibilityOn = true;
        TaskFocusStarter starter = newStarter();

        starter.startTaskFocus(task(true));

        assertTrue(starter.isBlockModeArmed());
        assertEquals(0, blockUnavailableEvents);
    }

    @Test
    public void blockOnFocus_withAccessibilityOff_startsUnarmedAndSignalsOnce() {
        accessibilityOn = false;
        TaskFocusStarter starter = newStarter();

        assertTrue(starter.startTaskFocus(task(true)));

        assertEquals(State.RUNNING, machine.getState());
        assertFalse(starter.isBlockModeArmed());
        assertEquals(1, blockUnavailableEvents);
    }

    @Test
    public void withoutBlockOnFocus_neverArmsNorSignals() {
        TaskFocusStarter starter = newStarter();

        starter.startTaskFocus(task(false));

        assertFalse(starter.isBlockModeArmed());
        assertEquals(0, blockUnavailableEvents);
    }

    @Test
    public void homeFocusThis_withBlockOnFocusAndAccessibilityOff_alsoSignals() {
        // Home's path: FocusTarget already bound, minutes read from the db, then begin(...).
        TaskFocusStarter starter = newStarter();

        assertTrue(starter.begin(60, 25, true));

        assertFalse(starter.isBlockModeArmed());
        assertEquals(1, blockUnavailableEvents);
        assertTrue(machine.hasPlan());
    }

    @Test
    public void blockArmedByTheTask_goesBackOffWhenItEndsEarly() {
        accessibilityOn = true;
        TaskFocusStarter starter = newStarter();
        starter.startTaskFocus(task(true));

        machine.cancel();
        starter.onTaskSessionEnded();

        assertFalse(starter.isBlockModeArmed());
    }

    @Test
    public void blockArmedByTheTask_goesBackOffAfterItsLastBlock() {
        settings = new Settings(FOCUS, REST, true, 4, false, true);
        accessibilityOn = true;
        TaskFocusStarter[] starter = new TaskFocusStarter[1];
        // The view model's wiring: the all-cycles signal ends the task session.
        machine = new FocusTimerStateMachine(() -> now, () -> settings,
                new FocusTimerStateMachine.Listener() {
                    @Override
                    public void onFocusCompleted(long sessionMillis) { }

                    @Override
                    public void onAllCyclesCompleted(int cycles) {
                        starter[0].onTaskSessionEnded();
                    }

                    @Override
                    public void onFailed() { }
                });
        starter[0] = newStarter();
        starter[0].startTaskFocus(new TaskFocusRequest(TASK_ID, "Corta", 10, 0, true, 0, null,
                null, 0));
        assertTrue(starter[0].isBlockModeArmed());

        elapse(10 * MIN);

        assertEquals(State.STOPPED, machine.getState());
        assertFalse(starter[0].isBlockModeArmed());
    }

    @Test
    public void blockTheUserHadArmed_staysArmedAfterTheTask() {
        accessibilityOn = true;
        TaskFocusStarter starter = newStarter();
        starter.restoreBlockModeArmed(true);
        starter.startTaskFocus(task(true));

        machine.cancel();
        starter.onTaskSessionEnded();

        assertTrue(starter.isBlockModeArmed());
    }

    @Test
    public void userChoiceDuringTheTask_isKeptWhenItEnds() {
        accessibilityOn = true;
        TaskFocusStarter starter = newStarter();
        starter.startTaskFocus(task(true));

        starter.setBlockModeArmedByUser(true); // the user confirms the block on the Focus screen
        machine.cancel();
        starter.onTaskSessionEnded();

        assertTrue(starter.isBlockModeArmed());
    }

    @Test
    public void userDisarmDuringTheTask_isKeptWhenItEnds() {
        accessibilityOn = true;
        TaskFocusStarter starter = newStarter();
        starter.startTaskFocus(task(true));

        starter.setBlockModeArmedByUser(false);
        machine.cancel();
        starter.onTaskSessionEnded();

        assertFalse(starter.isBlockModeArmed());
    }
}
