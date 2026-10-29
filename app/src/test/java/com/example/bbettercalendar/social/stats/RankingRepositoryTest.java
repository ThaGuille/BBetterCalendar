package com.example.bbettercalendar.social.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.example.bbettercalendar.social.common.ListenerException;
import com.example.bbettercalendar.social.common.ListenerSource;
import com.example.bbettercalendar.social.common.Scheduler;
import com.example.bbettercalendar.social.common.SerialExecutor;
import com.example.bbettercalendar.social.friends.FriendCard;
import com.example.bbettercalendar.social.friends.FriendsState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * F3-u5: {@link RankingRepository} con Firebase simulado por fakes de las interfaces propias
 * ({@link StatsReadBackend} y su {@link ListenerSource}; sin Robolectric): un listener de {@code stats/} propio y
 * uno por amigo, solo mientras se observa; {@code LOADING} hasta mi documento y la lista; un amigo sin documento,
 * de otra semana o denegado cuenta 0 sin volver {@code ERROR} el ranking; el reenganche con {@code Backoff} tras
 * {@code PERMISSION_DENIED}; y {@link RankingRepository#observeFriendStats}.
 *
 * <p>El serial corre sobre un ejecutor directo (en orden, sin reentrar), {@code postValue} se entrega en el acto
 * con un {@link TaskExecutor} de prueba y el {@link Scheduler} es un reloj falso que solo corre al avanzarlo.
 */
public class RankingRepositoryTest {

    /** Jueves 2026-10-29: semana 2026-W44; la anterior es 2026-W43. */
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);
    private static final String DAY = "2026-10-29";
    private static final String WEEK = "2026-W44";
    private static final String PREV_WEEK = "2026-W43";

    private static final FriendCard BOB = new FriendCard("uidB", "bob", "Bob", 1);
    private static final FriendCard CAROL = new FriendCard("uidC", "carol", "Carol", 2);

    private FakeBackend backend;
    private FakeScheduler scheduler;
    private MutableLiveData<FriendsState<FriendCard>> friends;
    private RankingRepository repo;
    private final List<RankingState> seen = new ArrayList<>();
    private final Observer<RankingState> observer = seen::add;

    @Before
    public void setUp() {
        ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            @Override
            public void executeOnDiskIO(@NonNull Runnable runnable) {
                runnable.run();
            }

            @Override
            public void postToMainThread(@NonNull Runnable runnable) {
                runnable.run();
            }

            @Override
            public boolean isMainThread() {
                return true;
            }
        });
        backend = new FakeBackend();
        scheduler = new FakeScheduler();
        friends = new MutableLiveData<>(FriendsState.<FriendCard>loading());
    }

    @After
    public void tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    // ---- ayudantes -------------------------------------------------------------------------------------

    private void create(@Nullable String uid) {
        backend.uid = uid;
        repo = new RankingRepository(backend, friends, new SerialExecutor(Runnable::run), scheduler, () -> TODAY);
        backend.auth.onAuth(uid); // Firebase avisa al registrarse
    }

    private void observe() {
        repo.observeRanking().observeForever(observer);
    }

    private RankingState state() {
        return repo.observeRanking().getValue();
    }

    private void friendsReady(FriendCard... cards) {
        friends.setValue(FriendsState.ready(Arrays.asList(cards)));
    }

    private static Map<String, Object> stats(String day, String week, int today, int weekMin, int days, int goal,
                                             int streak) {
        Map<String, Object> m = new HashMap<>();
        m.put(StatsMapping.DAY_KEY, day);
        m.put(StatsMapping.WEEK_KEY, week);
        m.put(StatsMapping.MINUTES_TODAY, (long) today);
        m.put(StatsMapping.MINUTES_WEEK_CAPPED, (long) weekMin);
        m.put(StatsMapping.DAYS_THIS_WEEK, (long) days);
        m.put(StatsMapping.WEEKLY_GOAL, (long) goal);
        m.put(StatsMapping.WEEK_STREAK, (long) streak);
        m.put(StatsMapping.BEST_WEEK_STREAK, (long) (streak + 2));
        m.put(StatsMapping.TOTAL_MINUTES, 5000L);
        m.put(StatsMapping.UPDATED_AT, 1_000L);
        return m;
    }

    private static Map<String, Object> thisWeek(int weekMin, int days) {
        return stats(DAY, WEEK, 30, weekMin, days, 3, 1);
    }

    private static int valueOf(List<Ranking.Row> rows, String uid) {
        for (Ranking.Row r : rows) {
            if (r.uid.equals(uid)) {
                return r.value;
            }
        }
        throw new AssertionError("sin fila para " + uid);
    }

    private static int positionOf(List<Ranking.Row> rows, String uid) {
        for (Ranking.Row r : rows) {
            if (r.uid.equals(uid)) {
                return r.position;
            }
        }
        throw new AssertionError("sin fila para " + uid);
    }

    // ---- casos -----------------------------------------------------------------------------------------

    @Test
    public void signedOut_isSignedOutAndListensNothing() {
        create(null);
        observe();
        friends.setValue(FriendsState.<FriendCard>signedOut());

        assertEquals(RankingState.Status.SIGNED_OUT, state().status);
        assertTrue(backend.listens.isEmpty());
    }

    @Test
    public void loadingUntilMyDocAndTheFriendsListArrive() {
        create("me");
        observe();
        assertEquals(RankingState.Status.LOADING, state().status);
        assertEquals(Arrays.asList("me"), backend.listens);

        backend.emit("me", thisWeek(100, 2)); // mi documento, pero la lista sigue en LOADING
        assertEquals(RankingState.Status.LOADING, state().status);

        backend.emitMissing("me", true); // un «no existe» de la caché no cambia nada
        friendsReady(BOB);
        assertEquals(RankingState.Status.LOADING, state().status); // Bob aún no ha respondido

        backend.emitMissing("uidB", true); // la caché de Bob no sabe: se espera al servidor
        assertEquals(RankingState.Status.LOADING, state().status);

        backend.emit("uidB", thisWeek(50, 1));
        RankingState s = state();
        assertEquals(RankingState.Status.READY, s.status);
        assertNotNull(s.ranking);
        assertEquals(100, valueOf(s.ranking.minutes, "me"));
        assertEquals(2, s.ranking.groupSize);
        assertSame(BOB, s.friend("uidB"));
    }

    @Test
    public void readyWithTwoFriends_ordersAndSharesPositions() {
        create("me");
        observe();
        friendsReady(BOB, CAROL);
        backend.emit("me", thisWeek(120, 2));
        backend.emit("uidB", thisWeek(300, 3));
        assertEquals(RankingState.Status.LOADING, state().status); // falta Carol
        backend.emit("uidC", thisWeek(120, 1));

        RankingState s = state();
        assertEquals(RankingState.Status.READY, s.status);
        Ranking r = s.ranking;
        assertEquals(3, r.groupSize);
        assertEquals("uidB", r.minutes.get(0).uid);
        assertEquals(1, positionOf(r.minutes, "uidB"));
        assertEquals(2, positionOf(r.minutes, "me"));
        assertEquals(2, positionOf(r.minutes, "uidC")); // empate: mismo puesto
        assertEquals(2, r.myMinutesPosition);
        assertEquals(1, positionOf(r.consistency, "uidB"));
        assertEquals(2, s.friends.size());
        assertTrue(s.hasFriends());
        assertEquals("uidB", r.crownUid); // empate a 30 hoy: gana @bob por orden de @usuario
    }

    @Test
    public void friendAddedAndRemoved_attachesAndDetachesItsListener() {
        create("me");
        observe();
        friendsReady(BOB);
        backend.emit("me", thisWeek(10, 1));
        backend.emit("uidB", thisWeek(20, 1));
        assertEquals(2, state().ranking.groupSize);

        friendsReady(BOB, CAROL);
        assertEquals(1, backend.count("uidC"));
        assertEquals(RankingState.Status.READY, state().status); // ya publicado: Carol cuenta 0 mientras
        assertEquals(0, valueOf(state().ranking.minutes, "uidC"));
        backend.emit("uidC", thisWeek(40, 1));
        assertEquals(40, valueOf(state().ranking.minutes, "uidC"));

        friendsReady(CAROL);
        assertTrue(backend.lastReg("uidB").removed);
        assertFalse(backend.lastReg("uidC").removed);
        assertEquals(2, state().ranking.groupSize);
        backend.emit("uidB", thisWeek(999, 7)); // de un listener ya quitado: se ignora
        assertEquals(2, state().ranking.minutes.size());
        assertEquals(1, backend.count("uidB"));
    }

    @Test
    public void friendPermissionDenied_countsZeroAndNeverErrors() {
        create("me");
        observe();
        friendsReady(BOB, CAROL);
        backend.emit("me", thisWeek(10, 1));
        backend.emit("uidC", thisWeek(60, 2));
        backend.fail("uidB", "PERMISSION_DENIED");

        RankingState s = state();
        assertEquals(RankingState.Status.READY, s.status);
        assertEquals(0, valueOf(s.ranking.minutes, "uidB"));
        assertEquals(3, s.ranking.groupSize);
        assertEquals(RankingState.FriendStatsState.Status.NO_STATS,
                repo.observeFriendStats("uidB").getValue().status);
    }

    @Test
    public void friendDocFromAnotherWeek_countsZeroForTheWeek() {
        create("me");
        observe();
        friendsReady(BOB);
        backend.emit("me", thisWeek(10, 1));
        backend.emit("uidB", stats("2026-10-22", PREV_WEEK, 90, 500, 4, 3, 2));

        Ranking r = state().ranking;
        assertEquals(0, valueOf(r.minutes, "uidB"));
        assertEquals(0, valueOf(r.consistency, "uidB"));
        assertEquals(1, r.myMinutesPosition);
        assertNull(r.crownUid); // sus 90 minutos no son de hoy
    }

    @Test
    public void observeFriendStats_withAndWithoutDoc() {
        create("me");
        observe();
        friendsReady(BOB, CAROL);
        backend.emit("me", thisWeek(10, 1));
        // Bob publicó ayer (misma semana): sus minutos de hoy pasan a 0, los de la semana cuentan.
        backend.emit("uidB", stats("2026-10-28", WEEK, 45, 200, 2, 4, 3));
        backend.emitMissing("uidC", false); // el servidor dice que Carol no tiene documento

        RankingState.FriendStatsState bob = repo.observeFriendStats("uidB").getValue();
        assertEquals(RankingState.FriendStatsState.Status.READY, bob.status);
        assertEquals(0, bob.stats.minutesToday);
        assertEquals(200, bob.stats.minutesWeekCapped);
        assertEquals(2, bob.stats.daysThisWeek);
        assertEquals(4, bob.stats.weeklyGoal);
        assertEquals(3, bob.stats.weekStreak);
        assertEquals(5, bob.stats.bestWeekStreak);
        assertEquals(5000, bob.stats.totalMinutes);
        assertSame(BOB, bob.card);

        RankingState.FriendStatsState carol = repo.observeFriendStats("uidC").getValue();
        assertEquals(RankingState.FriendStatsState.Status.NO_STATS, carol.status);
        assertNull(carol.stats);
        assertSame(CAROL, carol.card);

        assertEquals(RankingState.FriendStatsState.Status.NOT_FRIEND,
                repo.observeFriendStats("uidX").getValue().status);
        assertSame(repo.observeFriendStats("uidB"), repo.observeFriendStats("uidB"));

        // Bob sale de la lista (p. ej. tras eliminarlo): su perfil lo sabe.
        friendsReady(CAROL);
        assertEquals(RankingState.FriendStatsState.Status.NOT_FRIEND,
                repo.observeFriendStats("uidB").getValue().status);
    }

    @Test
    public void observeFriendStats_aloneAttachesTheListeners() {
        create("me");
        List<RankingState.FriendStatsState> got = new ArrayList<>();
        Observer<RankingState.FriendStatsState> o = got::add;
        repo.observeFriendStats("uidB").observeForever(o);
        assertEquals(RankingState.FriendStatsState.Status.LOADING, got.get(got.size() - 1).status);
        friendsReady(BOB);
        backend.emit("uidB", thisWeek(70, 2));
        assertEquals(RankingState.FriendStatsState.Status.READY, got.get(got.size() - 1).status);
        assertEquals(70, got.get(got.size() - 1).stats.minutesWeekCapped);

        repo.observeFriendStats("uidB").removeObserver(o);
        assertTrue(backend.lastReg("uidB").removed);
        assertTrue(backend.lastReg("me").removed);
    }

    @Test
    public void unobserved_detachesEverythingAndCancelsRetries() {
        create("me");
        observe();
        friendsReady(BOB, CAROL);
        backend.emit("me", thisWeek(10, 1));
        backend.emit("uidC", thisWeek(10, 1));
        backend.fail("uidB", "PERMISSION_DENIED");
        assertEquals(1, scheduler.pending().size());
        assertTrue(friends.hasObservers());

        repo.observeRanking().removeObserver(observer);

        assertTrue(backend.lastReg("me").removed);
        assertTrue(backend.lastReg("uidC").removed);
        assertFalse(friends.hasObservers());
        assertTrue(scheduler.pending().isEmpty());
        scheduler.advance(60_000L);
        assertEquals(1, backend.count("uidB")); // el reintento anulado no vuelve a escuchar

        // Al volver a observar, todo se engancha de nuevo y empieza en LOADING.
        observe();
        assertEquals(RankingState.Status.LOADING, state().status);
        assertEquals(2, backend.count("me"));
        assertEquals(2, backend.count("uidB"));
        assertEquals(2, backend.count("uidC"));
    }

    @Test
    public void permissionDenied_isReattachedWithBackoffAndThenCounts() {
        create("me");
        observe();
        friendsReady(BOB);
        backend.emit("me", thisWeek(10, 1));
        backend.fail("uidB", "PERMISSION_DENIED");
        assertEquals(0, valueOf(state().ranking.minutes, "uidB"));
        assertEquals(1, scheduler.pending().size());
        assertEquals(1_000L, scheduler.pending().get(0).delay);

        scheduler.advance(1_000L);
        assertEquals(2, backend.count("uidB"));
        backend.fail("uidB", "PERMISSION_DENIED"); // el espejo de la amistad aún no está
        assertEquals(2_000L, scheduler.pending().get(0).delay);

        scheduler.advance(2_000L);
        assertEquals(3, backend.count("uidB"));
        backend.emit("uidB", thisWeek(80, 2)); // la amistad ya es efectiva
        assertEquals(RankingState.Status.READY, state().status);
        assertEquals(80, valueOf(state().ranking.minutes, "uidB"));
        assertEquals(1, positionOf(state().ranking.minutes, "uidB"));
        assertTrue(scheduler.pending().isEmpty());
    }

    @Test
    public void permissionDeniedRetry_stopsWhenTheFriendLeavesOrOnSignOut() {
        create("me");
        observe();
        friendsReady(BOB, CAROL);
        backend.emit("me", thisWeek(10, 1));
        backend.fail("uidB", "PERMISSION_DENIED");
        backend.fail("uidC", "PERMISSION_DENIED");
        assertEquals(2, scheduler.pending().size());

        friendsReady(CAROL); // Bob sale de la lista
        assertEquals(1, scheduler.pending().size());
        scheduler.advance(1_000L);
        assertEquals(1, backend.count("uidB"));
        assertEquals(2, backend.count("uidC"));

        backend.fail("uidC", "PERMISSION_DENIED");
        backend.auth.onAuth(null); // cerrar sesión
        assertTrue(scheduler.pending().isEmpty());
        assertTrue(backend.lastReg("me").removed);
        assertEquals(RankingState.Status.SIGNED_OUT, state().status);
        assertEquals(RankingState.FriendStatsState.Status.SIGNED_OUT,
                repo.observeFriendStats("uidC").getValue().status);
    }

    @Test
    public void signOut_detachesAllAndSignInAgainWaitsForTheNewList() {
        create("me");
        observe();
        friendsReady(BOB);
        backend.emit("me", thisWeek(10, 1));
        backend.emit("uidB", thisWeek(10, 1));
        assertEquals(RankingState.Status.READY, state().status);

        backend.auth.onAuth(null);
        assertEquals(RankingState.Status.SIGNED_OUT, state().status);
        assertTrue(backend.lastReg("me").removed);
        assertTrue(backend.lastReg("uidB").removed);

        backend.auth.onAuth("other");
        assertEquals(RankingState.Status.LOADING, state().status);
        assertEquals(1, backend.count("other"));
        assertEquals(1, backend.count("uidB")); // la lista vieja no vuelve a engancharse
        backend.emit("other", thisWeek(5, 1));
        assertEquals(RankingState.Status.LOADING, state().status);
        friendsReady();
        assertEquals(RankingState.Status.READY, state().status);
        assertEquals(1, state().ranking.groupSize);
        assertEquals(Ranking.NO_RANK, state().ranking.myMinutesPosition);
        assertFalse(state().hasFriends());
    }

    @Test
    public void myListenerOrFriendsListError_isError() {
        create("me");
        observe();
        friendsReady();
        backend.emit("me", thisWeek(10, 1));
        assertEquals(RankingState.Status.READY, state().status);

        backend.fail("me", "UNAVAILABLE");
        assertEquals(RankingState.Status.ERROR, state().status);
        assertEquals(1, scheduler.pending().size()); // WatchedListener reintenta solo
        scheduler.advance(1_000L);
        backend.emit("me", thisWeek(10, 1));
        assertEquals(RankingState.Status.READY, state().status);

        RuntimeException boom = new RuntimeException("lista");
        friends.setValue(FriendsState.<FriendCard>error(boom));
        assertEquals(RankingState.Status.ERROR, state().status);
        assertSame(boom, state().error);
    }

    @Test
    public void malformedDoc_countsAsNoStats() {
        create("me");
        observe();
        friendsReady(BOB);
        backend.emit("me", thisWeek(10, 1));
        Map<String, Object> bad = thisWeek(10, 1);
        bad.put("fails", 3L); // no rompe nada: StatsMapping solo mira sus claves
        bad.put(StatsMapping.MINUTES_TODAY, 5000L); // fuera de rango → malformado
        backend.emit("uidB", bad);

        assertEquals(RankingState.Status.READY, state().status);
        assertEquals(0, valueOf(state().ranking.minutes, "uidB"));
        assertEquals(RankingState.FriendStatsState.Status.NO_STATS,
                repo.observeFriendStats("uidB").getValue().status);
    }

    // ---- fakes -----------------------------------------------------------------------------------------

    private static final class FakeReg implements ListenerSource.Registration {
        boolean removed;

        @Override
        public void remove() {
            removed = true;
        }
    }

    private static final class FakeBackend implements StatsReadBackend {
        String uid;
        AuthListener auth;
        /** Claves escuchadas, en orden (una por {@code listen}). */
        final List<String> listens = new ArrayList<>();
        final Map<String, ListenerSource.Events<Doc>> events = new HashMap<>();
        final Map<String, FakeReg> regs = new HashMap<>();

        private final ListenerSource<String, Doc> source = new ListenerSource<String, Doc>() {
            @NonNull
            @Override
            public Registration listen(@NonNull String key, @NonNull Executor executor,
                                       @NonNull Events<Doc> ev) {
                listens.add(key);
                events.put(key, ev);
                FakeReg reg = new FakeReg();
                regs.put(key, reg);
                return reg;
            }
        };

        @Nullable
        @Override
        public String currentUid() {
            return uid;
        }

        @Override
        public void addAuthListener(@NonNull AuthListener listener) {
            auth = listener;
        }

        @NonNull
        @Override
        public ListenerSource<String, Doc> stats() {
            return source;
        }

        int count(String key) {
            int n = 0;
            for (String k : listens) {
                if (k.equals(key)) {
                    n++;
                }
            }
            return n;
        }

        FakeReg lastReg(String key) {
            return regs.get(key);
        }

        void emit(String key, Map<String, Object> data) {
            events.get(key).onEvent(new Doc(key, data, false), false, null);
        }

        void emitMissing(String key, boolean fromCache) {
            events.get(key).onEvent(new Doc(key, null, fromCache), fromCache, null);
        }

        void fail(String key, String code) {
            events.get(key).onEvent(null, false, new ListenerException(code, null));
        }
    }

    /** {@link Scheduler} con reloj falso: nada corre hasta {@link #advance(long)}. */
    private static final class FakeScheduler implements Scheduler {

        static final class Entry {
            final long at;
            final long delay;
            final Runnable task;
            boolean done;

            Entry(long at, long delay, Runnable task) {
                this.at = at;
                this.delay = delay;
                this.task = task;
            }
        }

        private long now;
        private final List<Entry> entries = new ArrayList<>();

        @NonNull
        @Override
        public Cancellable schedule(long delayMs, @NonNull Runnable task) {
            Entry e = new Entry(now + delayMs, delayMs, task);
            entries.add(e);
            return () -> e.done = true;
        }

        List<Entry> pending() {
            List<Entry> out = new ArrayList<>();
            for (Entry e : entries) {
                if (!e.done) {
                    out.add(e);
                }
            }
            return out;
        }

        void advance(long ms) {
            long target = now + ms;
            while (true) {
                Entry next = null;
                for (Entry e : entries) {
                    if (!e.done && e.at <= target && (next == null || e.at < next.at)) {
                        next = e;
                    }
                }
                if (next == null) {
                    break;
                }
                now = next.at;
                next.done = true;
                next.task.run();
            }
            now = target;
        }
    }
}
