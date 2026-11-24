package com.example.bbettercalendar.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;
import androidx.lifecycle.MutableLiveData;

import com.example.bbettercalendar.calendarEntries.AddEventActivity;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.notifications.event.EventReminderScheduler;
import com.example.bbettercalendar.social.stats.StatsPublisher;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Rediseño de Proyectos (§3.5, decisión d4): «Terminar antes» una sesión de una tarea guarda sus
 * minutos enteros como una sesión normal con la tarea, sin contar un fallo ni un pomodoro terminado,
 * y la tarea sólo se completa si con ellos llega a su meta. Sólo se simulan las DAO de Room, el
 * programador de alarmas y los executors (la frontera); la atribución es la real.
 */
public class FocusSessionRepositoryEndEarlyTest {

    private static final int TASK_ID = 42;
    private static final int GOAL = 60;

    private StatsDAO statsDao;
    private FocusEventDAO focusEventDao;
    private CalendarEntryDAO calendarEntryDao;
    private FocusAttributionRepository attribution;
    private FocusSessionRepository repository;
    private CalendarEntry task;

    /** Ejecuta en el acto: el test ve el resultado de la tarea del executor. */
    private static final class DirectExecutorService extends AbstractExecutorService {
        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }

    @Before
    public void setUp() {
        ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            @Override
            public void executeOnDiskIO(Runnable runnable) {
                runnable.run();
            }

            @Override
            public void postToMainThread(Runnable runnable) {
                runnable.run();
            }

            @Override
            public boolean isMainThread() {
                return true;
            }
        });
        statsDao = mock(StatsDAO.class);
        focusEventDao = mock(FocusEventDAO.class);
        calendarEntryDao = mock(CalendarEntryDAO.class);
        task = new CalendarEntry.EventBuilder()
                .setEventId(TASK_ID)
                .setEventType(AddEventActivity.TYPE_TASK)
                .setEventTitle("Repasar el tema 4")
                .setEventTargetMinutes(GOAL)
                .build();
        when(calendarEntryDao.getEventById(TASK_ID)).thenReturn(task);
        attribution = new FocusAttributionRepository(focusEventDao,
                calendarEntryDao, mock(EventReminderScheduler.class));
        ExecutorService direct = new DirectExecutorService();
        repository = new FocusSessionRepository(statsDao, focusEventDao, attribution, direct, direct,
                () -> mock(StatsPublisher.class), () -> false, message -> { });
    }

    @After
    public void tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    @Test
    public void endingEarly_recordsANormalFocusSessionForTheTask() {
        repository.recordFocusEndedEarly(TASK_ID, 7, 1);

        ArgumentCaptor<FocusEvent> ev = ArgumentCaptor.forClass(FocusEvent.class);
        verify(focusEventDao).insert(ev.capture());
        assertEquals(0, ev.getValue().type);
        assertEquals(TASK_ID, ev.getValue().entryId);
        assertEquals(7, ev.getValue().durationMin);
        assertEquals(7, ev.getValue().elapsedMin);
        assertEquals(1, ev.getValue().pauseCount);
        verify(statsDao).addTimeStudied(7 * 60_000);
    }

    @Test
    public void endingEarly_isNeitherAFailNorAFinishedPomodoro_andLeavesTheTaskOpen() {
        when(focusEventDao.sumAttributedMinutes(TASK_ID)).thenReturn(33);

        repository.recordFocusEndedEarly(TASK_ID, 8, 0);

        verify(statsDao, never()).addFails();
        verify(statsDao, never()).addTasksDone();
        verify(calendarEntryDao, never()).update(any(CalendarEntry.class));
        assertFalse(task.isDone());
    }

    @Test
    public void endingEarly_thatReachesTheGoal_completesTheTask() {
        when(focusEventDao.sumAttributedMinutes(TASK_ID)).thenReturn(GOAL);

        repository.recordFocusEndedEarly(TASK_ID, 12, 0);

        verify(calendarEntryDao).update(task);
        assertTrue(task.isDone());
        assertEquals("Repasar el tema 4", attribution.getAutoCompletedTaskTitle().getValue());
    }

    @Test
    public void lessThanOneWholeMinute_recordsNothing() {
        repository.recordFocusEndedEarly(TASK_ID, 0, 0);

        verify(focusEventDao, never()).insert(any(FocusEvent.class));
        verify(statsDao, never()).addTimeStudied(anyInt());
    }

    @Test
    public void theMinutesKeptReachAListAlreadyOnScreen_asAFreshRow() {
        MutableLiveData<List<CalendarEntry>> rows = new MutableLiveData<>(Collections.singletonList(task));
        MutableLiveData<List<AttributedMinutes>> sums = new MutableLiveData<>(
                Collections.singletonList(minutes(TASK_ID, 25)));
        when(focusEventDao.observeAttributedMinutesByEntry()).thenReturn(sums);
        List<List<CalendarEntry>> shown = new ArrayList<>();
        attribution.enrich(rows).observeForever(shown::add);
        CalendarEntry before = shown.get(shown.size() - 1).get(0);

        // «Terminar antes» tras 1 minuto: focus_event cambia, la fila de la tarea no.
        sums.setValue(Collections.singletonList(minutes(TASK_ID, 26)));

        CalendarEntry after = shown.get(shown.size() - 1).get(0);
        assertEquals(26, after.getAttributedMinutes());
        assertEquals(TASK_ID, after.getId());
        assertEquals("Repasar el tema 4", after.getTitle());
        assertEquals(GOAL, after.getTargetMinutes());
        assertEquals("the row already drawn keeps what it showed", 25, before.getAttributedMinutes());
    }

    private static AttributedMinutes minutes(int entryId, int minutes) {
        AttributedMinutes am = new AttributedMinutes();
        am.entryId = entryId;
        am.minutes = minutes;
        return am;
    }

    @Test
    public void anUnboundSession_recordsNothing() {
        repository.recordFocusEndedEarly(0, 9, 0);

        verify(focusEventDao, never()).insert(any(FocusEvent.class));
        verify(statsDao, never()).addTimeStudied(anyInt());
    }
}
