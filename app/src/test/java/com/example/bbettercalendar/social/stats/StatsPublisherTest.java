package com.example.bbettercalendar.social.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;
import androidx.lifecycle.MutableLiveData;

import com.example.bbettercalendar.social.account.SocialPrefs;
import com.example.bbettercalendar.social.common.SerialExecutor;
import com.example.bbettercalendar.social.profile.Profile;
import com.example.bbettercalendar.social.profile.ProfileState;
import com.example.bbettercalendar.stats.DayMinutes;
import com.example.bbettercalendar.stats.FocusEventDAO;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * F3-u4: {@link StatsPublisher} con Firebase simulado por Mockito a través de {@link StatsBackend}, el DAO y
 * {@link SocialPrefs} simulados (las preferencias guardan en un mapa: un publicador nuevo con el mismo mapa es
 * «otro arranque de la app») y ejecutores directos. {@code postValue}/{@code setValue} se entregan en el acto
 * con un {@link TaskExecutor} de prueba.
 */
@SuppressWarnings("unchecked")
public class StatsPublisherTest {

    private static final LocalDate WED = LocalDate.of(2026, 10, 28);
    private static final Profile ME = new Profile("me", "Me", "me", 0, null, null);
    private static final ProfileState READY = ProfileState.ready(ME);

    private MutableLiveData<ProfileState> profiles;
    private StatsBackend backend;
    private FocusEventDAO dao;
    private SocialPrefs prefs;
    private final Map<String, String> prefsStore = new HashMap<>();
    private final List<Throwable> logged = new ArrayList<>();
    private final AtomicReference<LocalDate> today = new AtomicReference<>(WED);
    private boolean writesSucceed = true;
    private boolean enabled = true;

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
        profiles = new MutableLiveData<>(ProfileState.loading());
        backend = mock(StatsBackend.class);
        when(backend.currentUid()).thenReturn("me");
        doAnswer(inv -> {
            StatsBackend.Callback cb = inv.getArgument(3);
            if (writesSucceed) {
                cb.onSuccess();
            } else {
                cb.onError(new IllegalStateException("offline rejected"));
            }
            return null;
        }).when(backend).setStats(anyString(), anyMap(), any(Executor.class), any(StatsBackend.Callback.class));

        dao = mock(FocusEventDAO.class);
        DayMinutes wed = new DayMinutes();
        wed.day = "2026-10-28";
        wed.minutes = 50;
        when(dao.getFocusMinutesPerDayBetween(anyLong(), anyLong())).thenReturn(Collections.singletonList(wed));
        when(dao.sumFocusMinutesSince(0L)).thenReturn(900);
        when(dao.getActiveDaysSince(0L)).thenReturn(Arrays.asList("2026-10-20", "2026-10-28"));

        prefs = mock(SocialPrefs.class);
        when(prefs.readLastStatsPublished(anyString())).thenAnswer(inv -> prefsStore.get((String) inv.getArgument(0)));
        doAnswer(inv -> {
            prefsStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(prefs).saveLastStatsPublished(anyString(), anyString());
    }

    @After
    public void tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    /** Un publicador nuevo = un proceso nuevo (comparte las preferencias). */
    private StatsPublisher newPublisher(Executor serial) {
        return new StatsPublisher(profiles, backend, dao, () -> 3, prefs, serial, today::get, () -> enabled,
                (message, error) -> logged.add(error));
    }

    private StatsPublisher started() {
        StatsPublisher p = newPublisher(new SerialExecutor(Runnable::run));
        p.start();
        return p;
    }

    private Map<String, Object> lastWrite(int expectedWrites) {
        ArgumentCaptor<Map<String, Object>> map = ArgumentCaptor.forClass(Map.class);
        verify(backend, times(expectedWrites)).setStats(eq("me"), map.capture(), any(Executor.class),
                any(StatsBackend.Callback.class));
        return map.getValue();
    }

    @Test
    public void signedOutNeverWrites() {
        when(backend.currentUid()).thenReturn(null);
        StatsPublisher p = started();
        profiles.setValue(ProfileState.signedOut());
        p.requestPublish();
        verify(backend, never()).setStats(anyString(), anyMap(), any(Executor.class), any(StatsBackend.Callback.class));
    }

    @Test
    public void aProfileThatIsNotReadyNeverWrites() {
        StatsPublisher p = started();
        p.requestPublish(); // LOADING
        profiles.setValue(ProfileState.none());
        p.requestPublish();
        profiles.setValue(ProfileState.error(new IllegalStateException("listener")));
        p.requestPublish();
        verify(backend, never()).setStats(anyString(), anyMap(), any(Executor.class), any(StatsBackend.Callback.class));
    }

    @Test
    public void readyWritesOnceWithTheTenContractKeysFromAllHistory() {
        started();
        profiles.setValue(READY);
        Map<String, Object> m = lastWrite(1);
        assertEquals(new HashSet<>(Arrays.asList("dayKey", "weekKey", "minutesToday", "minutesWeekCapped",
                "daysThisWeek", "weeklyGoal", "weekStreak", "bestWeekStreak", "totalMinutes", "updatedAt")), m.keySet());
        assertEquals("2026-10-28", m.get("dayKey"));
        assertEquals("2026-W44", m.get("weekKey"));
        assertEquals(50, m.get("minutesToday"));
        assertEquals(50, m.get("minutesWeekCapped"));
        assertEquals(1, m.get("daysThisWeek"));
        assertEquals(3, m.get("weeklyGoal"));
        assertEquals(900, m.get("totalMinutes")); // D-031: toda la historia, sin fecha de corte
        verify(dao).sumFocusMinutesSince(0L);
        verify(dao).getActiveDaysSince(0L);
        assertTrue(prefsStore.get("me").startsWith("2026-10-28|2026-W44|"));
    }

    @Test
    public void aSecondReadyOfTheSameUidWithTheSameKeysDoesNotWrite() {
        started();
        profiles.setValue(READY);
        profiles.setValue(ProfileState.ready(new Profile("me", "Me renamed", "me", 3, null, null)));
        lastWrite(1);

        // Otro arranque el mismo día y sin sesiones nuevas: nada que publicar.
        newPublisher(new SerialExecutor(Runnable::run)).start();
        lastWrite(1);
    }

    @Test
    public void aNewDayAtStartWrites() {
        started();
        profiles.setValue(READY);
        lastWrite(1);

        today.set(WED.plusDays(1));
        newPublisher(new SerialExecutor(Runnable::run)).start();
        Map<String, Object> m = lastWrite(2);
        assertEquals("2026-10-29", m.get("dayKey"));
        assertEquals(0, m.get("minutesToday")); // la fila del miércoles ya no es de hoy
    }

    @Test
    public void requestPublishWhileReadyWrites() {
        StatsPublisher p = started();
        profiles.setValue(READY);
        lastWrite(1);
        p.requestPublish();
        lastWrite(2);
    }

    @Test
    public void aBurstOfRequestsCoalescesIntoOneWrite() {
        List<Runnable> queue = new ArrayList<>();
        StatsPublisher p = newPublisher(new SerialExecutor(queue::add));
        p.start();
        profiles.setValue(READY);
        drain(queue);
        lastWrite(1);

        p.requestPublish();
        p.requestPublish();
        p.requestPublish();
        drain(queue);
        lastWrite(2);

        p.requestPublish(); // una petición después de la ráfaga vuelve a escribir
        drain(queue);
        lastWrite(3);
    }

    private static void drain(List<Runnable> queue) {
        while (!queue.isEmpty()) {
            queue.remove(0).run();
        }
    }

    @Test
    public void withoutTheFlagNothingIsObservedOrWritten() {
        enabled = false;
        StatsPublisher p = started();
        profiles.setValue(READY);
        p.requestPublish();
        verify(backend, never()).setStats(anyString(), anyMap(), any(Executor.class), any(StatsBackend.Callback.class));
        assertFalse(profiles.hasObservers());
    }

    @Test
    public void aProfileOfAnotherSessionIsNotPublished() {
        when(backend.currentUid()).thenReturn("someoneElse");
        StatsPublisher p = started();
        profiles.setValue(READY);
        p.requestPublish();
        verify(backend, never()).setStats(anyString(), anyMap(), any(Executor.class), any(StatsBackend.Callback.class));
    }

    @Test
    public void aFailedWriteIsOnlyLoggedAndRetriedAtTheNextStart() {
        writesSucceed = false;
        started();
        profiles.setValue(READY);
        lastWrite(1);
        assertEquals(1, logged.size());
        assertFalse(prefsStore.containsKey("me"));

        writesSucceed = true;
        newPublisher(new SerialExecutor(Runnable::run)).start();
        lastWrite(2);
        assertTrue(prefsStore.containsKey("me"));
    }

    @Test
    public void signingOutAndInAgainRecomputesButSkipsAnIdenticalDocument() {
        started();
        profiles.setValue(READY);
        lastWrite(1);
        profiles.setValue(ProfileState.signedOut());
        profiles.setValue(READY);
        lastWrite(1); // mismos valores ya publicados

        when(dao.sumFocusMinutesSince(0L)).thenReturn(925); // una sesión hecha sin sesión iniciada
        profiles.setValue(ProfileState.signedOut());
        profiles.setValue(READY);
        assertEquals(925, lastWrite(2).get("totalMinutes"));
    }
}
