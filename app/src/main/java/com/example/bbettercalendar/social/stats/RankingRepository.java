package com.example.bbettercalendar.social.stats;

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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
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
 * Ranking de la semana y stats de cada amigo (F3-u5; 02 §6 §9, 03 §5 §6 §12, D-030). Escucha {@code stats/{me}}
 * y un {@code stats/{fid}} por amigo de {@link FriendsRepository#observeFriends()} (como mucho los 50 que esa
 * lista vigila) y recalcula con {@link RankingCalculator} y {@code WeekKey.Keys.of(LocalDate.now())} en cada
 * cambio.
 *
 * <ul>
 *   <li><b>Solo mientras se observa</b> (03 §12): los listeners se enganchan cuando {@link #observeRanking()} o
 *       algún {@link #observeFriendStats(String)} gana su primer observador activo, y se sueltan todos cuando el
 *       último deja de observar, al cerrar sesión y, uno a uno, para quien sale de la lista.</li>
 *   <li><b>Estados:</b> {@code LOADING} hasta que llegan mi documento y la lista de amigos y responde el
 *       documento de cada amigo (el primer {@code READY} no parpadea; después, un amigo nuevo cuenta 0 hasta
 *       que responde). {@code ERROR} solo si falla mi listener o la lista de amigos.</li>
 *   <li><b>Un amigo sin stats cuenta 0</b> y sigue apareciendo: sin documento, malformado
 *       ({@link StatsMapping#fromMap} da null) o {@code PERMISSION_DENIED}. Nunca vuelve {@code ERROR} el
 *       ranking entero.</li>
 *   <li><b>{@code PERMISSION_DENIED} de un amigo se reengancha</b> con {@link Backoff} (1, 2, 4… s, como mucho
 *       6 intentos): la amistad se escribe en los dos lados con una Function y el primer listener puede llegar
 *       antes que el espejo. {@link WatchedListener} no reintenta ese código solo, así que lo hace este
 *       repositorio. El reintento se anula si el amigo sale de la lista, al dejar de observar y al cerrar
 *       sesión; agotado, lo reanuda la vuelta a primer plano o {@link #refresh()}.</li>
 * </ul>
 *
 * <p>Regla #3: un solo {@link SerialExecutor} encima del {@code @IoExecutor} para todos los listeners
 * ({@link WatchedListener}), la lista de amigos y el estado interno, que solo se toca en ese hilo; se publica con
 * {@code postValue}. Firebase solo se toca a través de {@link StatsReadBackend}.
 */
@Singleton
public class RankingRepository {

    static final String PERMISSION_DENIED = "PERMISSION_DENIED";

    /** La fecha civil de hoy en la zona del dispositivo; en tests, una fija. */
    interface Today {
        @NonNull
        LocalDate date();
    }

    private final StatsReadBackend backend;
    private final LiveData<FriendsState<FriendCard>> friendsSource;
    private final Executor serial;
    private final Scheduler scheduler;
    private final Today today;

    private final Tracked<RankingState> ranking;
    /** Una por uid pedido; se crean en cualquier hilo y se publican desde el serial. Guardado por sí mismo. */
    private final Map<String, Tracked<RankingState.FriendStatsState>> friendStats = new HashMap<>();
    private final Observer<FriendsState<FriendCard>> friendsObserver;
    private final WatchedListener<String, StatsReadBackend.Doc> myWatcher;

    /** Observadores activos de todas las LiveData de este repositorio. Solo en el hilo principal. */
    private int activeObservers;

    // ---- estado: solo en el serial ----------------------------------------------------------------------
    private boolean active;
    @Nullable
    private String sessionUid;
    /** El último estado de la lista de amigos mientras se observa; null = aún no se sabe. */
    @Nullable
    private FriendsState<FriendCard> friendsState;
    @Nullable
    private FriendStats myStats;
    private boolean myAnswered;
    @Nullable
    private ListenerException myError;
    private boolean publishedReady;
    /** Un listener de stats por amigo de la lista. */
    private final Map<String, FriendEntry> entries = new LinkedHashMap<>();

    @Inject
    public RankingRepository(FirestoreStatsReadBackend backend, FriendsRepository friends,
                             @IoExecutor ExecutorService ioExecutor) {
        this(backend, friends.observeFriends(), new SerialExecutor(ioExecutor), new MainLooperScheduler(),
                LocalDate::now);
        // Tras un error que espera (o reintentos agotados), se vuelve a enganchar al volver a primer plano.
        ProcessForeground.observe(this::refresh);
    }

    /** Para tests: Firebase llega solo por {@link StatsReadBackend}; la lista de amigos, por una LiveData. */
    RankingRepository(@NonNull StatsReadBackend backend, @NonNull LiveData<FriendsState<FriendCard>> friendsSource,
                      @NonNull Executor serial, @NonNull Scheduler scheduler, @NonNull Today today) {
        this.backend = backend;
        this.friendsSource = friendsSource;
        this.serial = serial;
        this.scheduler = scheduler;
        this.today = today;
        // La sesión inicial ya cuenta: el primer aviso de Auth con el mismo uid no lo toma por un cambio.
        this.sessionUid = backend.currentUid();
        this.ranking = new Tracked<>(sessionUid != null ? RankingState.loading() : RankingState.signedOut());
        this.friendsObserver = state -> serial.execute(() -> onFriends(state));
        this.myWatcher = new WatchedListener<>(serial, scheduler, backend.stats(), new MyStatsSink());
        backend.addAuthListener(uid -> serial.execute(() -> onSession(uid)));
    }

    // ---- observar --------------------------------------------------------------------------------------

    /** El ranking de la semana (yo y mis amigos); sigue a la sesión y escucha solo mientras se observa. */
    @NonNull
    public LiveData<RankingState> observeRanking() {
        return ranking;
    }

    /**
     * Las stats de un amigo para su perfil, con la semántica del ranking aplicada a mis claves de hoy, o
     * «sin stats todavía». La misma LiveData para el mismo uid; mientras se observa, engancha los listeners
     * como {@link #observeRanking()}.
     */
    @NonNull
    public LiveData<RankingState.FriendStatsState> observeFriendStats(@NonNull String uid) {
        Tracked<RankingState.FriendStatsState> live;
        synchronized (friendStats) {
            live = friendStats.get(uid);
            if (live != null) {
                return live;
            }
            live = new Tracked<>(RankingState.FriendStatsState.loading(uid));
            friendStats.put(uid, live);
        }
        final Tracked<RankingState.FriendStatsState> created = live;
        serial.execute(() -> created.postValue(friendStatsState(uid, WeekKey.Keys.of(today.date()))));
        return live;
    }

    /**
     * Vuelve a enganchar lo que espera tras un error (mi listener, la lista agotada de reintentos de un amigo
     * denegado) y recalcula con la fecha de hoy. Lo llama la vuelta a primer plano; la UI puede llamarlo desde
     * «Reintentar».
     */
    public void refresh() {
        serial.execute(() -> {
            if (!active) {
                return;
            }
            myWatcher.onForeground();
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

    /** Una LiveData que cuenta sus observadores activos para enganchar y soltar los listeners. */
    private final class Tracked<T> extends MutableLiveData<T> {

        Tracked(T initial) {
            super(initial);
        }

        @Override
        protected void onActive() {
            retain();
        }

        @Override
        protected void onInactive() {
            release();
        }
    }

    private void retain() {
        if (activeObservers++ == 0) {
            // Primero se activa: el valor actual de la lista, que llega al observar, se encola detrás.
            serial.execute(this::activate);
            friendsSource.observeForever(friendsObserver);
        }
    }

    private void release() {
        if (activeObservers > 0 && --activeObservers == 0) {
            friendsSource.removeObserver(friendsObserver);
            serial.execute(this::deactivate);
        }
    }

    // ---- sesión, lista y activación (serial) ------------------------------------------------------------

    private void activate() {
        active = true;
        resetMine();
        friendsState = null;
        publishedReady = false;
        if (sessionUid != null) {
            myWatcher.attach(sessionUid);
        }
        publish();
    }

    private void deactivate() {
        active = false;
        myWatcher.detach();
        clearEntries();
        resetMine();
        friendsState = null;
        publishedReady = false;
    }

    private void onSession(@Nullable String uid) {
        if (Objects.equals(uid, sessionUid)) {
            return; // misma sesión ya enganchada
        }
        sessionUid = uid;
        clearEntries();
        resetMine();
        // La lista que había era de la sesión anterior: se espera a la siguiente de FriendsRepository.
        friendsState = null;
        publishedReady = false;
        if (uid == null || !active) {
            myWatcher.detach();
        } else {
            myWatcher.attach(uid);
        }
        publish();
    }

    private void onFriends(@Nullable FriendsState<FriendCard> state) {
        if (!active || state == null) {
            return;
        }
        friendsState = state;
        if (state.status == FriendsState.Status.READY && sessionUid != null) {
            reconcile(state.items);
        } else if (state.status == FriendsState.Status.SIGNED_OUT) {
            clearEntries();
        }
        // LOADING y ERROR: los listeners de los amigos que ya había siguen.
        publish();
    }

    /** Engancha a quien entra en la lista y suelta a quien sale. */
    private void reconcile(@NonNull List<FriendCard> cards) {
        Set<String> uids = new HashSet<>();
        for (FriendCard c : cards) {
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
        for (FriendCard c : cards) {
            if (!entries.containsKey(c.uid)) {
                FriendEntry e = new FriendEntry(c.uid);
                e.watcher = new WatchedListener<>(serial, scheduler, backend.stats(), new FriendStatsSink(e));
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

    private void resetMine() {
        myStats = null;
        myAnswered = false;
        myError = null;
    }

    // ---- listeners (serial) ------------------------------------------------------------------------------

    /** {@code stats/{me}}. */
    private final class MyStatsSink implements WatchedListener.Sink<StatsReadBackend.Doc> {

        @Override
        public void onLoading() {
            // Sigue sin responder.
        }

        @Override
        public void onDetached() {
            // Solo al dejar de observar o al cambiar de sesión; quien lo hace publica.
        }

        @Override
        public void onValue(@NonNull StatsReadBackend.Doc doc) {
            if (!active || sessionUid == null) {
                return;
            }
            if (!doc.exists() && doc.fromCache) {
                return; // la caché no sabe; esperamos al servidor
            }
            myStats = StatsMapping.fromMap(doc.data); // malformado → sin stats (cuento 0)
            myAnswered = true;
            myError = null;
            publish();
        }

        @Override
        public void onError(@NonNull ListenerException error) {
            if (!active || sessionUid == null) {
                return;
            }
            myError = error; // los reintentables se reenganchan solos; los demás, con refresh()
            publish();
        }
    }

    /** Un amigo de la lista, su listener y el reenganche tras {@code PERMISSION_DENIED}. */
    private final class FriendEntry {
        @NonNull
        final String uid;
        WatchedListener<String, StatsReadBackend.Doc> watcher;
        /** Ya respondió (documento, «no existe» del servidor o error). */
        boolean answered;
        @Nullable
        FriendStats stats;
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

    /** {@code stats/{fid}} de un amigo. Ignora lo de una entrada que ya no está. */
    private final class FriendStatsSink implements WatchedListener.Sink<StatsReadBackend.Doc> {

        private final FriendEntry entry;

        FriendStatsSink(@NonNull FriendEntry entry) {
            this.entry = entry;
        }

        @Override
        public void onLoading() {
            // Al reenganchar tras un error se queda lo último (o 0).
        }

        @Override
        public void onDetached() {
            // Solo al salir de la lista, de la sesión o al dejar de observar.
        }

        @Override
        public void onValue(@NonNull StatsReadBackend.Doc doc) {
            if (!entry.current()) {
                return;
            }
            if (!doc.exists() && doc.fromCache) {
                return; // la caché no sabe; esperamos al servidor
            }
            entry.stats = StatsMapping.fromMap(doc.data); // sin documento o malformado → sin stats (0)
            entry.answered = true;
            if (!doc.fromCache) {
                entry.deniedBackoff.reset();
            }
            publish();
        }

        @Override
        public void onError(@NonNull ListenerException error) {
            if (!entry.current()) {
                return;
            }
            entry.answered = true;
            if (PERMISSION_DENIED.equals(error.code)) {
                entry.stats = null; // aún no se puede leer: cuenta 0 y se reintenta
                entry.scheduleDeniedRetry();
            }
            // Otros códigos: WatchedListener reintenta los reintentables; mientras, lo último que hubo.
            publish();
        }
    }

    // ---- publicar (serial) -------------------------------------------------------------------------------

    private void publish() {
        WeekKey.Keys keys = WeekKey.Keys.of(today.date());
        ranking.postValue(rankingState(keys));
        List<Map.Entry<String, Tracked<RankingState.FriendStatsState>>> all;
        synchronized (friendStats) {
            all = new ArrayList<>(friendStats.entrySet());
        }
        for (Map.Entry<String, Tracked<RankingState.FriendStatsState>> e : all) {
            e.getValue().postValue(friendStatsState(e.getKey(), keys));
        }
    }

    @NonNull
    private RankingState rankingState(@NonNull WeekKey.Keys keys) {
        String me = sessionUid;
        if (me == null) {
            return RankingState.signedOut();
        }
        if (!active) {
            return RankingState.loading();
        }
        if (myError != null) {
            return RankingState.error(myError.original());
        }
        FriendsState<FriendCard> fs = friendsState;
        if (fs != null && fs.status == FriendsState.Status.ERROR && fs.error != null) {
            return RankingState.error(fs.error);
        }
        if (fs == null || fs.status != FriendsState.Status.READY || !myAnswered) {
            return RankingState.loading();
        }
        List<RankingCalculator.FriendInput> inputs = new ArrayList<>();
        boolean allAnswered = true;
        for (FriendCard c : fs.items) {
            FriendEntry e = entries.get(c.uid);
            allAnswered &= e != null && e.answered;
            inputs.add(new RankingCalculator.FriendInput(c.uid, c.handle, e == null ? null : e.stats));
        }
        if (!allAnswered && !publishedReady) {
            return RankingState.loading(); // el primer READY espera a todos los amigos (sin parpadeo)
        }
        publishedReady = true;
        return RankingState.ready(RankingCalculator.compute(me, myStats, inputs, keys), fs.items);
    }

    @NonNull
    private RankingState.FriendStatsState friendStatsState(@NonNull String uid, @NonNull WeekKey.Keys keys) {
        if (sessionUid == null) {
            return RankingState.FriendStatsState.signedOut(uid);
        }
        FriendEntry e = active ? entries.get(uid) : null;
        FriendsState<FriendCard> fs = active ? friendsState : null;
        if (fs != null && fs.status == FriendsState.Status.READY) {
            FriendCard card = null;
            for (FriendCard c : fs.items) {
                if (c.uid.equals(uid)) {
                    card = c;
                }
            }
            if (card == null) {
                return RankingState.FriendStatsState.notFriend(uid);
            }
            return fromEntry(uid, e, card, keys);
        }
        if (fs != null && fs.status == FriendsState.Status.ERROR && fs.error != null) {
            // La lista falló: si ya se sabía algo de ese amigo, se sigue enseñando.
            return e != null && e.answered ? fromEntry(uid, e, null, keys)
                    : RankingState.FriendStatsState.error(uid, fs.error);
        }
        return RankingState.FriendStatsState.loading(uid);
    }

    @NonNull
    private static RankingState.FriendStatsState fromEntry(@NonNull String uid, @Nullable FriendEntry e,
                                                           @Nullable FriendCard card, @NonNull WeekKey.Keys keys) {
        if (e == null || !e.answered) {
            return RankingState.FriendStatsState.loading(uid);
        }
        FriendStats s = RankingCalculator.applySemantics(e.stats, keys);
        return s == null ? RankingState.FriendStatsState.noStats(uid, card)
                : RankingState.FriendStatsState.ready(uid, s, card);
    }
}
