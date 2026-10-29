package com.example.bbettercalendar.stats;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;

import com.example.bbettercalendar.social.stats.StatsPublisher;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * F3-u4: el gancho de {@link FocusSessionRepository} hacia {@link StatsPublisher} (03 §10). Una sesión completada
 * y un fallo piden cada uno una publicación, después del insert en {@code focus_event}; sin
 * {@code FEATURE_SOCIAL} el publicador ni se crea ({@code dagger.Lazy} sin {@code get()}).
 */
public class FocusSessionRepositoryPublishTest {

    private StatsDAO statsDao;
    private FocusEventDAO focusEventDao;
    private StatsPublisher publisher;
    private final AtomicInteger lazyGets = new AtomicInteger();

    /** Ejecuta en el acto: los tests ven el orden real de la tarea del executor. */
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
        publisher = mock(StatsPublisher.class);
    }

    @After
    public void tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    private FocusSessionRepository repo(boolean socialEnabled) {
        ExecutorService direct = new DirectExecutorService();
        return new FocusSessionRepository(statsDao, focusEventDao, mock(FocusAttributionRepository.class),
                direct, direct, () -> {
                    lazyGets.incrementAndGet();
                    return publisher;
                }, () -> socialEnabled, message -> { });
    }

    @Test
    public void aCompletedSessionRequestsOnePublishAfterTheInsert() {
        repo(true).recordFocusCompleted(25 * 60_000, 0);
        InOrder order = inOrder(focusEventDao, publisher);
        ArgumentCaptor<FocusEvent> ev = ArgumentCaptor.forClass(FocusEvent.class);
        order.verify(focusEventDao).insert(ev.capture());
        order.verify(publisher).requestPublish();
        verify(publisher, times(1)).requestPublish();
        assertEquals(FocusEvent.TYPE_FOCUS, ev.getValue().type);
        assertEquals(25, ev.getValue().durationMin);
    }

    @Test
    public void aFailRequestsOnePublishAfterTheInsert() {
        repo(true).recordFail(0);
        InOrder order = inOrder(focusEventDao, publisher);
        ArgumentCaptor<FocusEvent> ev = ArgumentCaptor.forClass(FocusEvent.class);
        order.verify(focusEventDao).insert(ev.capture());
        order.verify(publisher).requestPublish();
        verify(publisher, times(1)).requestPublish();
        assertEquals(FocusEvent.TYPE_FAIL, ev.getValue().type);
        assertEquals(0, ev.getValue().durationMin);
    }

    @Test
    public void withoutTheSocialFlagThePublisherIsNeverCreated() {
        FocusSessionRepository r = repo(false);
        r.recordFocusCompleted(25 * 60_000, 0);
        r.recordFail(0);
        verify(focusEventDao, times(2)).insert(any(FocusEvent.class));
        verify(publisher, never()).requestPublish();
        assertEquals(0, lazyGets.get());
    }
}
