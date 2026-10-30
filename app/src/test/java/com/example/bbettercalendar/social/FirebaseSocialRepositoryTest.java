package com.example.bbettercalendar.social;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.annotation.NonNull;
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
import com.example.bbettercalendar.social.presence.PresenceReadBackend;
import com.example.bbettercalendar.social.stats.Ranking;
import com.example.bbettercalendar.social.stats.RankingState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * F4-u4: {@link FirebaseSocialRepository} con la RTDB simulada ({@link PresenceReadBackend} con Mockito y una
 * fuente de {@code status/} falsa), la lista de amigos y el ranking como LiveData. Se mira lo que vería quien usa
 * la app: quién sale estudiando, en qué orden, con qué tiempo y dónde va la corona; y cuándo se escucha.
 *
 * <p>El serial corre sobre un ejecutor directo, {@code postValue} se entrega en el acto y el reloj del móvil es el
 * del {@link Scheduler} falso, que solo avanza a mano. El servidor va {@link #OFFSET} por delante del móvil.
 */
public class FirebaseSocialRepositoryTest {

    private static final long MIN = 60_000L;
    private static final long HOUR = 60 * MIN;
    /** Reloj del móvil al empezar cada caso. */
    private static final long DEVICE_T0 = 1_800_000_000_000L;
    /** El servidor va 3 s por delante. */
    private static final long OFFSET = 3_000L;

    private static final FriendCard BOB = new FriendCard("uidB", "bob", "Bob", 1);
    private static final FriendCard CAROL = new FriendCard("uidC", "carol", "Carol", 2);
    private static final FriendCard DANI = new FriendCard("uidD", "dani", "Dani", 3);

    private PresenceReadBackend backend;
    private FakeStatusSource source;
    private FakeScheduler scheduler;
    private MutableLiveData<FriendsState<FriendCard>> friends;
    private MutableLiveData<RankingState> ranking;
    private PresenceReadBackend.AuthListener auth;
    private FirebaseSocialRepository repo;
    private final List<SocialSnapshot> seen = new ArrayList<>();
    private final Observer<SocialSnapshot> observer = seen::add;

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
        source = new FakeStatusSource();
        scheduler = new FakeScheduler();
        friends = new MutableLiveData<>(FriendsState.<FriendCard>loading());
        ranking = new MutableLiveData<>(RankingState.loading());
    }

    @After
    public void tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    // ---- ayudantes -------------------------------------------------------------------------------------

    private void create(String uid) {
        backend = mock(PresenceReadBackend.class);
        when(backend.currentUid()).thenReturn(uid);
        when(backend.serverTimeOffsetMs()).thenReturn(OFFSET);
        when(backend.status()).thenReturn(source);
        repo = new FirebaseSocialRepository(backend, friends, ranking, new SerialExecutor(Runnable::run), scheduler,
                () -> DEVICE_T0 + scheduler.now);
        ArgumentCaptor<PresenceReadBackend.AuthListener> captor =
                ArgumentCaptor.forClass(PresenceReadBackend.AuthListener.class);
        verify(backend).addAuthListener(captor.capture());
        auth = captor.getValue();
        auth.onAuth(uid); // Firebase avisa al registrarse
    }

    private void observe() {
        repo.getSnapshot().observeForever(observer);
    }

    private void stopObserving() {
        repo.getSnapshot().removeObserver(observer);
    }

    private SocialSnapshot snap() {
        return repo.getSnapshot().getValue();
    }

    private void friendsReady(FriendCard... cards) {
        friends.setValue(FriendsState.ready(Arrays.asList(cards)));
    }

    /** Hora del servidor ahora mismo. */
    private long serverNow() {
        return DEVICE_T0 + scheduler.now + OFFSET;
    }

    private static Map<String, Object> focus(long since, long sessionStart) {
        Map<String, Object> m = new HashMap<>();
        m.put("state", "focus");
        m.put("since", since);
        m.put("sessionStart", sessionStart);
        return m;
    }

    private static Map<String, Object> paused(long since, long sessionStart, long pausedAt) {
        Map<String, Object> m = focus(since, sessionStart);
        m.put("state", "paused");
        m.put("pausedAt", pausedAt);
        return m;
    }

    private static Map<String, Object> idle(long since) {
        Map<String, Object> m = new HashMap<>();
        m.put("state", "idle");
        m.put("since", since);
        return m;
    }

    private static RankingState rankingReady(String crownUid, int myPosition, FriendCard... cards) {
        Ranking r = new Ranking(Collections.<Ranking.Row>emptyList(), Collections.<Ranking.Row>emptyList(),
                crownUid, myPosition, cards.length + 1);
        return RankingState.ready(r, Arrays.asList(cards));
    }

    private Friend friend(String uid) {
        for (Friend f : snap().getFriends()) {
            if (f.getUid().equals(uid)) {
                return f;
            }
        }
        throw new AssertionError("sin " + uid);
    }

    private static List<String> names(List<Friend> list) {
        List<String> out = new ArrayList<>();
        for (Friend f : list) {
            out.add(f.getName());
        }
        return out;
    }

    // ---- casos -----------------------------------------------------------------------------------------

    @Test
    public void withoutSession_emitsEmptyAndListensNothing() {
        create(null);
        observe();
        friends.setValue(FriendsState.<FriendCard>signedOut());
        ranking.setValue(RankingState.signedOut());

        assertSame(SocialSnapshot.EMPTY, snap());
        assertTrue(source.listens.isEmpty());
    }

    @Test
    public void friendsShowAsSoonAsTheListIsKnown_awayUntilTheirStatusArrives() {
        create("me");
        observe();
        assertSame(SocialSnapshot.EMPTY, snap()); // la lista aún carga

        friendsReady(BOB, CAROL);
        assertEquals(2, snap().getFriendCount());
        assertEquals(0, snap().getStudyingCount());
        assertEquals(Arrays.asList("uidB", "uidC"), source.listens); // un listener por amigo
        Friend bob = friend("uidB");
        assertEquals("bob", bob.getHandle());
        assertEquals(1, bob.getAvatarColor());
        assertFalse(bob.isStudying());
    }

    @Test
    public void freshStatus_showsStudyingFirstWithLiveTimeOnTheDeviceClock() {
        create("me");
        observe();
        friendsReady(BOB, CAROL, DANI);
        source.emit("uidB", focus(serverNow(), serverNow() - 12 * MIN));
        source.emit("uidD", focus(serverNow(), serverNow() - 40 * MIN));
        source.emit("uidC", idle(serverNow()));

        SocialSnapshot s = snap();
        assertEquals(Arrays.asList("Dani", "Bob", "Carol"), names(s.getFriends())); // la sesión más larga primero
        assertEquals(2, s.getStudyingCount());
        long deviceNow = DEVICE_T0 + scheduler.now;
        assertEquals(12 * MIN, friend("uidB").getElapsedMs(deviceNow));
        assertEquals(13 * MIN, friend("uidB").getElapsedMs(deviceNow + MIN)); // corre sin eventos nuevos
        assertFalse(friend("uidC").isStudying());
    }

    @Test
    public void pausedFriend_isStudyingWithItsTimeFrozen() {
        create("me");
        observe();
        friendsReady(BOB);
        long now = serverNow();
        source.emit("uidB", paused(now, now - 30 * MIN, now - 5 * MIN));

        Friend bob = friend("uidB");
        assertTrue(bob.isStudying());
        assertTrue(bob.isPaused());
        assertEquals(25 * MIN, bob.getElapsedMs(DEVICE_T0 + scheduler.now + HOUR));
        assertEquals(1, snap().getStudyingCount());
    }

    @Test
    public void crownAndRankArriveWhenTheRankingIsReady_andTheCrownCanBeOnAnAwayFriend() {
        create("me");
        observe();
        friendsReady(BOB, CAROL);
        source.emit("uidB", focus(serverNow(), serverNow() - MIN));
        assertNull(snap().getTopFriend()); // ranking aún en LOADING
        assertNull(snap().getUserRank());

        ranking.setValue(rankingReady("uidC", 2, BOB, CAROL));

        assertEquals("uidC", snap().getCrownUid());
        assertEquals("Carol", snap().getTopFriend().getName());
        assertFalse(snap().getTopFriend().isStudying()); // Carol está ausente y lleva la corona igual
        assertNotNull(snap().getUserRank());
        assertEquals(2, snap().getUserRank().getPosition());
        assertEquals(3, snap().getUserRank().getGroupSize());

        ranking.setValue(RankingState.loading()); // recalculando: la corona se queda
        assertEquals("uidC", snap().getCrownUid());
    }

    @Test
    public void statusCrossingThe4hLineWhileObserved_turnsAwayWithoutANewEvent() {
        create("me");
        observe();
        friendsReady(BOB, CAROL);
        long now = serverNow();
        source.emit("uidB", focus(now - 4 * HOUR + 10 * MIN, now - 5 * HOUR)); // le quedan 10 min de frescura
        source.emit("uidC", focus(now - HOUR, now - HOUR));
        assertEquals(2, snap().getStudyingCount());

        scheduler.advance(10 * MIN);
        assertTrue(friend("uidB").isStudying()); // justo en la línea sigue fresco

        scheduler.advance(1);
        assertFalse(friend("uidB").isStudying());
        assertTrue(friend("uidC").isStudying());
        assertEquals(1, source.count("uidB")); // sin volver a escuchar: solo se reevalúa

        // El siguiente en cruzar es Carol, 3 h después.
        scheduler.advance(3 * HOUR - 10 * MIN);
        assertFalse(friend("uidC").isStudying());
        assertTrue(scheduler.pending().isEmpty());
    }

    @Test
    public void listensOnlyWhileObserved_andKeepsTheLastStateBetweenObservations() {
        create("me");
        friendsReady(BOB);
        assertTrue(source.listens.isEmpty());
        assertFalse(friends.hasObservers());

        observe();
        source.emit("uidB", focus(serverNow(), serverNow() - 20 * MIN));
        assertTrue(friend("uidB").isStudying());
        assertTrue(ranking.hasObservers());

        stopObserving();
        assertTrue(source.lastReg("uidB").removed);
        assertFalse(friends.hasObservers());
        assertFalse(ranking.hasObservers());
        assertTrue(scheduler.pending().isEmpty()); // tampoco queda la reevaluación de 4 h

        observe();
        assertEquals(2, source.count("uidB"));
        assertTrue(friend("uidB").isStudying()); // sin parpadear «ausente» mientras llega el valor nuevo
        source.emit("uidB", idle(serverNow()));
        assertFalse(friend("uidB").isStudying());
    }

    @Test
    public void signOut_releasesEverythingAndEmitsEmpty_andSignInWaitsForTheNewList() {
        create("me");
        observe();
        friendsReady(BOB, CAROL);
        ranking.setValue(rankingReady("uidB", 1, BOB, CAROL));
        source.emit("uidB", focus(serverNow(), serverNow() - MIN));
        source.fail("uidC", "PERMISSION_DENIED");
        assertEquals(2, scheduler.pending().size()); // el reintento de Carol y la reevaluación de 4 h de Bob

        auth.onAuth(null);
        friends.setValue(FriendsState.<FriendCard>signedOut());
        ranking.setValue(RankingState.signedOut());

        assertSame(SocialSnapshot.EMPTY, snap());
        assertTrue(source.lastReg("uidB").removed);
        assertTrue(source.lastReg("uidC").removed);
        assertTrue(scheduler.pending().isEmpty());
        scheduler.advance(60_000L);
        assertEquals(1, source.count("uidC")); // el reintento anulado no vuelve a escuchar

        auth.onAuth("other");
        assertSame(SocialSnapshot.EMPTY, snap());
        friendsReady(DANI);
        assertEquals(Arrays.asList("Dani"), names(snap().getFriends()));
        assertNull(snap().getCrownUid()); // la corona era de la sesión anterior
        assertEquals(1, source.count("uidB"));
    }

    @Test
    public void removedFriend_releasesItsListenerAndItsLateEventsAreIgnored() {
        create("me");
        observe();
        friendsReady(BOB, CAROL);
        source.emit("uidB", focus(serverNow(), serverNow() - MIN));

        friendsReady(CAROL);

        assertTrue(source.lastReg("uidB").removed);
        assertFalse(source.lastReg("uidC").removed);
        assertEquals(Arrays.asList("Carol"), names(snap().getFriends()));
        source.emit("uidB", focus(serverNow(), serverNow() - 2 * MIN)); // de un listener ya quitado
        assertEquals(1, snap().getFriendCount());
        assertEquals(0, snap().getStudyingCount());
    }

    @Test
    public void permissionDenied_isAwayAndReattachedWithBackoffUntilTheMirrorExists() {
        create("me");
        observe();
        friendsReady(BOB);
        source.fail("uidB", "PERMISSION_DENIED");
        assertEquals(1, snap().getFriendCount());
        assertFalse(friend("uidB").isStudying());
        assertEquals(1, scheduler.pending().size());
        assertEquals(1_000L, scheduler.pending().get(0).delay);

        scheduler.advance(1_000L);
        assertEquals(2, source.count("uidB"));
        source.fail("uidB", "PERMISSION_DENIED"); // el espejo friends/ de la RTDB aún no está
        assertEquals(2_000L, scheduler.pending().get(0).delay);

        scheduler.advance(2_000L);
        assertEquals(3, source.count("uidB"));
        source.emit("uidB", focus(serverNow(), serverNow() - 7 * MIN)); // la amistad ya es efectiva
        assertTrue(friend("uidB").isStudying());
    }

    @Test
    public void otherErrors_keepTheLastKnownState() {
        create("me");
        observe();
        friendsReady(BOB);
        source.emit("uidB", focus(serverNow(), serverNow() - 3 * MIN));

        source.fail("uidB", "UNAVAILABLE"); // sin red: WatchedListener reintenta solo

        assertTrue(friend("uidB").isStudying());
        scheduler.advance(1_000L);
        assertEquals(2, source.count("uidB"));
    }

    // ---- fakes -----------------------------------------------------------------------------------------

    private static final class FakeReg implements ListenerSource.Registration {
        boolean removed;

        @Override
        public void remove() {
            removed = true;
        }
    }

    /** {@code status/} falso: guarda quién escucha y deja emitir valores y errores. */
    private static final class FakeStatusSource implements ListenerSource<String, PresenceReadBackend.Status> {
        final List<String> listens = new ArrayList<>();
        final Map<String, Events<PresenceReadBackend.Status>> events = new HashMap<>();
        final Map<String, FakeReg> regs = new HashMap<>();

        @NonNull
        @Override
        public Registration listen(@NonNull String key, @NonNull Executor executor,
                                   @NonNull Events<PresenceReadBackend.Status> ev) {
            listens.add(key);
            events.put(key, ev);
            FakeReg reg = new FakeReg();
            regs.put(key, reg);
            return reg;
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
            events.get(key).onEvent(new PresenceReadBackend.Status(key, data), false, null);
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

        long now;
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
