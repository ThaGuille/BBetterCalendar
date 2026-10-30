package com.example.bbettercalendar.social;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.social.common.Backoff;
import com.example.bbettercalendar.social.common.ListenerException;
import com.example.bbettercalendar.social.common.MainLooperScheduler;
import com.example.bbettercalendar.social.common.ProcessForeground;
import com.example.bbettercalendar.social.common.Scheduler;
import com.example.bbettercalendar.social.common.SerialExecutor;
import com.example.bbettercalendar.social.common.WatchedListener;
import com.example.bbettercalendar.social.friends.FriendCard;
import com.example.bbettercalendar.social.friends.FriendsRepository;
import com.example.bbettercalendar.social.friends.FriendsState;
import com.example.bbettercalendar.social.presence.Presence;
import com.example.bbettercalendar.social.presence.PresenceMapping;
import com.example.bbettercalendar.social.presence.PresenceReadBackend;
import com.example.bbettercalendar.social.presence.RtdbPresenceReadBackend;
import com.example.bbettercalendar.social.stats.Ranking;
import com.example.bbettercalendar.social.stats.RankingRepository;
import com.example.bbettercalendar.social.stats.RankingState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * La instantánea social real (F4-u4; 02 §7, 03 §8 §10 §12, D-033): junta la lista de amigos
 * ({@link FriendsRepository#observeFriends()}), un listener de {@code status/{fid}} por amigo
 * ({@link PresenceReadBackend}) y la corona de {@link RankingRepository#observeRanking()}.
 *
 * <ul>
 *   <li><b>Sin sesión</b> publica {@link SocialSnapshot#EMPTY} (las pantallas enseñan su «sin cuenta»).</li>
 *   <li><b>Amigos en cuanto se sabe la lista</b>: cada uno sale ausente hasta que responde su {@code status/}; la
 *       corona y mi puesto llegan cuando el ranking está {@code READY}.</li>
 *   <li><b>Regla de 4 h en vivo</b>: tras cada publicación se programa una reevaluación para el primer amigo
 *       presente que cruce la línea (medida desde su {@code since}), así pasa a ausente sin evento nuevo.</li>
 *   <li><b>Solo mientras se observa</b> (03 §12): los listeners (presencia, lista y ranking) se enganchan con el
 *       primer observador activo y se sueltan con el último; también al cerrar sesión y, uno a uno, para quien sale
 *       de la lista. El último estado de cada amigo se guarda entre observaciones de la misma sesión para no
 *       parpadear «ausente» al pasar de una pantalla a otra (la regla de 4 h se le sigue aplicando).</li>
 *   <li><b>{@code PERMISSION_DENIED} de un amigo</b> (el espejo {@code friends/} de la RTDB aún no está) lo deja
 *       ausente y se reengancha con {@link Backoff}, como en {@link RankingRepository}; agotado, lo reanuda la vuelta
 *       a primer plano ({@link #refresh()}).</li>
 * </ul>
 *
 * <p>Regla #3: un solo {@link SerialExecutor} encima del {@code @IoExecutor} para los listeners, la lista, el
 * ranking y el estado interno, que solo se toca en ese hilo; se publica con {@code postValue}. Las horas de los
 * amigos se pasan al reloj del móvil con {@code .info/serverTimeOffset}.
 */
@Singleton
public class FirebaseSocialRepository implements SocialRepository {

    static final String PERMISSION_DENIED = "PERMISSION_DENIED";

    /** El reloj del móvil; en tests, uno falso. */
    interface DeviceClock {
        long nowMs();
    }

    private final PresenceReadBackend backend;
    private final LiveData<FriendsState<FriendCard>> friendsSource;
    private final LiveData<RankingState> rankingSource;
    private final Executor serial;
    private final Scheduler scheduler;
    private final DeviceClock clock;

    private final Snapshot snapshot = new Snapshot();
    private final Observer<FriendsState<FriendCard>> friendsObserver;
    private final Observer<RankingState> rankingObserver;

    // ---- estado: solo en el serial ----------------------------------------------------------------------
    private boolean active;
    @Nullable
    private String sessionUid;
    /** La última lista READY de esta sesión; null = aún no se sabe. */
    @Nullable
    private List<FriendCard> cards;
    @Nullable
    private String crownUid;
    @Nullable
    private SocialRank userRank;
    /** Un amigo de la lista con su listener y su último {@code status/}. */
    private final Map<String, FriendEntry> entries = new LinkedHashMap<>();
    @Nullable
    private Scheduler.Cancellable pendingReevaluation;

    @Inject
    public FirebaseSocialRepository(RtdbPresenceReadBackend backend, FriendsRepository friends,
                                    RankingRepository ranking, @IoExecutor ExecutorService ioExecutor) {
        this(backend, friends.observeFriends(), ranking.observeRanking(), new SerialExecutor(ioExecutor),
                new MainLooperScheduler(), System::currentTimeMillis);
        // Tras reintentos agotados de un amigo denegado, se vuelve a enganchar al volver a primer plano.
        ProcessForeground.observe(this::refresh);
    }

    /** Para tests: Firebase llega solo por {@link PresenceReadBackend}; la lista y el ranking, por LiveData. */
    FirebaseSocialRepository(@NonNull PresenceReadBackend backend,
                             @NonNull LiveData<FriendsState<FriendCard>> friendsSource,
                             @NonNull LiveData<RankingState> rankingSource, @NonNull Executor serial,
                             @NonNull Scheduler scheduler, @NonNull DeviceClock clock) {
        this.backend = backend;
        this.friendsSource = friendsSource;
        this.rankingSource = rankingSource;
        this.serial = serial;
        this.scheduler = scheduler;
        this.clock = clock;
        // La sesión inicial ya cuenta: el primer aviso de Auth con el mismo uid no lo toma por un cambio.
        this.sessionUid = backend.currentUid();
        this.friendsObserver = state -> serial.execute(() -> onFriends(state));
        this.rankingObserver = state -> serial.execute(() -> onRanking(state));
        backend.addAuthListener(uid -> serial.execute(() -> onSession(uid)));
    }

    @NonNull
    @Override
    public LiveData<SocialSnapshot> getSnapshot() {
        return snapshot;
    }

    /**
     * Vuelve a enganchar a los amigos que esperan tras un error (reintentos agotados de un denegado) y reevalúa la
     * regla de 4 h. Lo llama la vuelta a primer plano.
     */
    public void refresh() {
        serial.execute(() -> {
            if (!active) {
                return;
            }
            for (FriendEntry e : entries.values()) {
                e.deniedBackoff.reset();
                if (e.pendingRetry == null) {
                    e.watcher.onForeground();
                }
            }
            publish();
        });
    }

    // ---- observadores activos (hilo principal) ----------------------------------------------------------

    /** La instantánea: engancha los listeners con su primer observador activo y los suelta con el último. */
    private final class Snapshot extends MutableLiveData<SocialSnapshot> {

        Snapshot() {
            super(SocialSnapshot.EMPTY);
        }

        @Override
        protected void onActive() {
            // Primero se activa: los valores actuales de lista y ranking, que llegan al observar, van detrás.
            serial.execute(FirebaseSocialRepository.this::activate);
            friendsSource.observeForever(friendsObserver);
            rankingSource.observeForever(rankingObserver);
        }

        @Override
        protected void onInactive() {
            friendsSource.removeObserver(friendsObserver);
            rankingSource.removeObserver(rankingObserver);
            serial.execute(FirebaseSocialRepository.this::deactivate);
        }
    }

    // ---- sesión, lista, ranking y activación (serial) ---------------------------------------------------

    private void activate() {
        active = true;
        if (sessionUid != null) {
            for (FriendEntry e : entries.values()) {
                e.watcher.attach(e.uid);
            }
        }
        publish();
    }

    private void deactivate() {
        active = false;
        for (FriendEntry e : entries.values()) {
            e.stop(); // suelta el listener; su último estado se queda para la próxima vez
        }
        cancelReevaluation();
    }

    private void onSession(@Nullable String uid) {
        if (Objects.equals(uid, sessionUid)) {
            return; // misma sesión
        }
        sessionUid = uid;
        clearEntries();
        // Lo que había era de la sesión anterior: se espera a la lista y al ranking de la nueva.
        cards = null;
        crownUid = null;
        userRank = null;
        publish();
    }

    private void onFriends(@Nullable FriendsState<FriendCard> state) {
        if (!active || state == null) {
            return;
        }
        if (state.status == FriendsState.Status.READY && sessionUid != null) {
            cards = new ArrayList<>(state.items);
            reconcile(state.items);
        } else if (state.status == FriendsState.Status.SIGNED_OUT) {
            cards = null;
            clearEntries();
        }
        // LOADING y ERROR: se sigue enseñando la última lista conocida.
        publish();
    }

    private void onRanking(@Nullable RankingState state) {
        if (!active || state == null) {
            return;
        }
        if (state.status == RankingState.Status.READY && state.ranking != null) {
            crownUid = state.ranking.crownUid;
            userRank = rankOf(state.ranking, state.hasFriends());
        } else if (state.status == RankingState.Status.SIGNED_OUT) {
            crownUid = null;
            userRank = null;
        }
        // LOADING y ERROR: la corona que ya había se queda.
        publish();
    }

    @Nullable
    private static SocialRank rankOf(@NonNull Ranking ranking, boolean hasFriends) {
        int position = ranking.myMinutesPosition;
        if (!hasFriends || position < 1 || ranking.groupSize < position) {
            return null;
        }
        return new SocialRank(position, ranking.groupSize);
    }

    /** Engancha a quien entra en la lista y suelta a quien sale. */
    private void reconcile(@NonNull List<FriendCard> list) {
        Set<String> uids = new HashSet<>();
        for (FriendCard c : list) {
            uids.add(c.uid);
        }
        Iterator<Map.Entry<String, FriendEntry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            FriendEntry e = it.next().getValue();
            if (!uids.contains(e.uid)) {
                it.remove(); // primero fuera del mapa: lo que aún llegue de él ya no se reconoce
                e.stop();
            }
        }
        for (FriendCard c : list) {
            if (!entries.containsKey(c.uid)) {
                FriendEntry e = new FriendEntry(c.uid);
                e.watcher = new WatchedListener<>(serial, scheduler, backend.status(), new StatusSink(e));
                entries.put(c.uid, e);
                e.watcher.attach(c.uid);
            }
        }
    }

    private void clearEntries() {
        List<FriendEntry> old = new ArrayList<>(entries.values());
        entries.clear();
        for (FriendEntry e : old) {
            e.stop();
        }
    }

    // ---- listeners (serial) ------------------------------------------------------------------------------

    /** Un amigo de la lista, su listener de {@code status/}, su último estado y el reenganche tras denegación. */
    private final class FriendEntry {
        @NonNull
        final String uid;
        WatchedListener<String, PresenceReadBackend.Status> watcher;
        /** Último {@code status/} crudo; null = sin estado, ilegible o denegado (ausente). */
        @Nullable
        Map<String, Object> raw;
        final Backoff deniedBackoff = new Backoff();
        @Nullable
        Scheduler.Cancellable pendingRetry;

        FriendEntry(@NonNull String uid) {
            this.uid = uid;
        }

        boolean current() {
            return active && entries.get(uid) == this;
        }

        /** Suelta el listener y anula el reenganche pendiente. */
        void stop() {
            if (pendingRetry != null) {
                pendingRetry.cancel();
                pendingRetry = null;
            }
            watcher.detach();
        }

        /** Tras {@code PERMISSION_DENIED}: vuelve a enganchar con espera creciente mientras siga en la lista. */
        void scheduleDeniedRetry() {
            if (pendingRetry != null) {
                return;
            }
            long delay = deniedBackoff.nextDelayMs();
            if (delay < 0) {
                return; // agotado: lo reanuda refresh() (primer plano)
            }
            pendingRetry = scheduler.schedule(delay, () -> serial.execute(() -> {
                if (!current()) {
                    return;
                }
                pendingRetry = null;
                watcher.attach(uid); // en espera tras el error: attach vuelve a registrar
            }));
        }
    }

    /** {@code status/{fid}} de un amigo. Ignora lo de una entrada que ya no está o mientras no se observa. */
    private final class StatusSink implements WatchedListener.Sink<PresenceReadBackend.Status> {

        private final FriendEntry entry;

        StatusSink(@NonNull FriendEntry entry) {
            this.entry = entry;
        }

        @Override
        public void onLoading() {
            // Se queda lo último que se supo (o ausente).
        }

        @Override
        public void onDetached() {
            // Solo al salir de la lista, de la sesión o al dejar de observar.
        }

        @Override
        public void onValue(@NonNull PresenceReadBackend.Status status) {
            if (!entry.current()) {
                return;
            }
            entry.raw = status.data;
            entry.deniedBackoff.reset();
            publish();
        }

        @Override
        public void onError(@NonNull ListenerException error) {
            if (!entry.current()) {
                return;
            }
            if (PERMISSION_DENIED.equals(error.code)) {
                entry.raw = null; // aún no se puede leer: ausente, y se reintenta
                entry.scheduleDeniedRetry();
                publish();
            }
            // Otros códigos: WatchedListener reintenta solo; mientras, lo último que hubo.
        }
    }

    // ---- publicar (serial) -------------------------------------------------------------------------------

    private void publish() {
        List<FriendCard> list = cards;
        if (sessionUid == null || list == null) {
            cancelReevaluation();
            snapshot.postValue(SocialSnapshot.EMPTY);
            return;
        }
        long offset = backend.serverTimeOffsetMs();
        long serverNow = clock.nowMs() + offset;
        long firstAway = Long.MAX_VALUE;
        List<Friend> friends = new ArrayList<>(list.size());
        for (FriendCard c : list) {
            FriendEntry e = entries.get(c.uid);
            Presence p = PresenceMapping.fromMap(e == null ? null : e.raw, serverNow);
            firstAway = Math.min(firstAway, p.awayAfter());
            friends.add(new Friend(c.uid, c.handle, c.displayName, c.avatarColor, p, offset, 0L));
        }
        snapshot.postValue(new SocialSnapshot(friends, userRank, crownUid));
        scheduleReevaluation(firstAway, serverNow);
    }

    /** La regla de 4 h sin evento nuevo: vuelve a publicar justo después de que el primero cruce la línea. */
    private void scheduleReevaluation(long firstAwayServerMs, long serverNow) {
        cancelReevaluation();
        if (!active || firstAwayServerMs == Long.MAX_VALUE) {
            return;
        }
        long delay = Math.max(1L, firstAwayServerMs - serverNow + 1L);
        pendingReevaluation = scheduler.schedule(delay, () -> serial.execute(() -> {
            if (active) {
                publish();
            }
        }));
    }

    private void cancelReevaluation() {
        if (pendingReevaluation != null) {
            pendingReevaluation.cancel();
            pendingReevaluation = null;
        }
    }
}
