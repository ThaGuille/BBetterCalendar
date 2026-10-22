package com.example.bbettercalendar.social.friends;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.social.common.ListenerException;
import com.example.bbettercalendar.social.common.MainLooperScheduler;
import com.example.bbettercalendar.social.common.ProcessForeground;
import com.example.bbettercalendar.social.common.Scheduler;
import com.example.bbettercalendar.social.common.SerialExecutor;
import com.example.bbettercalendar.social.common.WatchedListener;
import com.example.bbettercalendar.social.profile.HandleValidator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Amigos y solicitudes de la cuenta (F2-u5; plan social-f2-amigos.md; 03 §5 §6; D-015, D-027, D-028).
 *
 * <p><b>Observar</b> (sigue a la sesión, como {@code ProfileRepository}):
 * <ul>
 *   <li>{@link #observeFriends()}: {@code users/me/friends} y un listener de {@code profiles/{fid}} por
 *       amigo (como mucho {@link #MAX_FRIENDS}), con altas y bajas al cambiar el conjunto. Un
 *       {@code PERMISSION_DENIED} o un perfil que el servidor dice que no existe es un amigo que ya no lo es
 *       (amistad «fantasma» de un solo lado, carrera con {@code deleteAccount}): se oculta, no es un error,
 *       y se llama a la callable {@code removeFriend} una vez para limpiarlo. Orden alfabético.</li>
 *   <li>{@link #observeIncoming()} ({@code to == me}: {@code pending} y {@code failed}),
 *       {@link #observeOutgoing()} ({@code from == me}: solo {@code pending}) y
 *       {@link #pendingIncomingCount()} (la campana). Se ordenan en el cliente: sin índices compuestos.</li>
 * </ul>
 *
 * <p><b>Escribir:</b> {@link #send} lee {@code users/me} del servidor, calcula el contador con
 * {@link RequestQuota} y escribe la solicitud y el contador en un solo lote ({@code backend.batch()}, un
 * {@code WriteBatch}) con los mapas de {@link FriendRequestWrites}; {@link #send} y {@link #accept} miran
 * antes el tope propio de 50 amigos; {@link #decline} y {@link #cancel} borran (no hay {@code declined});
 * {@link #remove} llama a {@code removeFriend}.
 *
 * <p>Regla #3: un solo {@link SerialExecutor} por repositorio encima del {@code @IoExecutor} (F2-u1) para
 * todos los listeners ({@link WatchedListener}), las respuestas de Firebase y el estado interno, que solo se
 * toca en ese hilo; el estado se publica con {@code postValue}. Los {@link Callback} llegan en ese hilo de
 * E/S: quien los reciba debe usar {@code postValue}.
 *
 * <p>Firebase solo se toca a través de {@link FriendsBackend} y de {@code ListenerSource} (interfaces
 * propias; {@code FriendsRepositoryTest} las simula con Mockito).
 */
@Singleton
public class FriendsRepository {

    /** Tope de amigos por persona (D-015); la Function lo vuelve a mirar al aceptar. */
    public static final int MAX_FRIENDS = 50;

    public interface Callback {
        void onSuccess();

        void onError(@NonNull Throwable error);
    }

    public interface LookupCallback {
        void onResult(@NonNull LookupResult result);

        void onError(@NonNull Throwable error);
    }

    /** Reloj de pared (para {@link RequestQuota}); en tests, uno fijo. */
    interface Clock {
        long nowMillis();
    }

    /** Lo que devuelve {@link #lookupHandle}. */
    public static final class LookupResult {

        public enum Kind {
            /** El {@code @usuario} no existe (o no tiene un formato posible). */
            NOT_FOUND,
            /** Es el tuyo. */
            SELF,
            ALREADY_FRIENDS,
            /** Ya le enviaste una solicitud. */
            ALREADY_SENT,
            /** Te envió una solicitud: se acepta, no se cruza; {@link #request} es esa solicitud. */
            THEY_SENT_YOU,
            /** Se le puede enviar; {@link #card} es su tarjeta. */
            OK
        }

        @NonNull
        public final Kind kind;
        /** La tarjeta de {@code handles/{h}}; null solo con {@code NOT_FOUND}. */
        @Nullable
        public final FriendCard card;
        /** Con {@code THEY_SENT_YOU}, la solicitud recibida. */
        @Nullable
        public final FriendRequest request;

        LookupResult(@NonNull Kind kind, @Nullable FriendCard card, @Nullable FriendRequest request) {
            this.kind = kind;
            this.card = card;
            this.request = request;
        }
    }

    /** Un {@code @usuario} con este formato puede existir en {@code handles/} (reglas de F1-u1 y F2-u1). */
    private static final Pattern LOOKUP_FORMAT = Pattern.compile("^[a-z0-9_.]{3,20}$");
    private static final Pattern FIRESTORE_RESERVED_ID = Pattern.compile("^__.*__$");
    static final String PERMISSION_DENIED = "PERMISSION_DENIED";

    private final FriendsBackend backend;
    private final Executor serial;
    private final Scheduler scheduler;
    private final Clock clock;

    private final MutableLiveData<FriendsState<FriendCard>> friends;
    private final MutableLiveData<FriendsState<FriendRequest>> incoming;
    private final MutableLiveData<FriendsState<FriendRequest>> outgoing;
    private final MutableLiveData<Integer> pendingCount = new MutableLiveData<>(0);

    private final WatchedListener<String, FriendsBackend.DocList> friendsWatcher;
    private final WatchedListener<String, FriendsBackend.DocList> incomingWatcher;
    private final WatchedListener<String, FriendsBackend.DocList> outgoingWatcher;

    // ---- estado: solo en el serial ----------------------------------------------------------------------
    @Nullable
    private String sessionUid;
    /** Todos los uids de {@code users/me/friends} (para el tope); null hasta la primera respuesta. */
    @Nullable
    private List<String> friendIds;
    private boolean friendsPublishedReady;
    /** Un listener de perfil por amigo (como mucho {@link #MAX_FRIENDS}). */
    private final Map<String, FriendEntry> friendEntries = new LinkedHashMap<>();
    /** Amigos ya limpiados con {@code removeFriend} en esta sesión: una sola llamada por amigo. */
    private final Set<String> cleaned = new HashSet<>();
    @NonNull
    private List<FriendRequest> lastIncoming = Collections.emptyList();
    /** Enviadas en cualquier estado (también {@code failed}): reenviar a esa persona se denegaría. */
    @NonNull
    private List<FriendRequest> lastOutgoingAll = Collections.emptyList();

    @Inject
    public FriendsRepository(FirestoreFriendsBackend backend, @IoExecutor ExecutorService ioExecutor) {
        this(backend, new SerialExecutor(ioExecutor), new MainLooperScheduler(), System::currentTimeMillis);
        // Tras un error no reintentable, los listeners vuelven a engancharse al volver a primer plano.
        ProcessForeground.observe(this::onForeground);
    }

    /** Para tests: Firebase llega solo por {@link FriendsBackend} y sus {@code ListenerSource}. */
    FriendsRepository(@NonNull FriendsBackend backend, @NonNull Executor serial, @NonNull Scheduler scheduler,
                      @NonNull Clock clock) {
        this.backend = backend;
        this.serial = serial;
        this.scheduler = scheduler;
        this.clock = clock;
        boolean signedIn = backend.currentUid() != null;
        this.friends = new MutableLiveData<>(signedIn ? FriendsState.loading() : FriendsState.signedOut());
        this.incoming = new MutableLiveData<>(signedIn ? FriendsState.loading() : FriendsState.signedOut());
        this.outgoing = new MutableLiveData<>(signedIn ? FriendsState.loading() : FriendsState.signedOut());
        this.friendsWatcher = new WatchedListener<>(serial, scheduler, backend.friendsOf(), new FriendsListSink());
        this.incomingWatcher = new WatchedListener<>(serial, scheduler, backend.requestsTo(),
                new RequestsSink(true));
        this.outgoingWatcher = new WatchedListener<>(serial, scheduler, backend.requestsFrom(),
                new RequestsSink(false));
        // Se dispara al registrarse y en cada cambio de sesión; todo el estado se toca en el serial.
        backend.addAuthListener(uid -> serial.execute(() -> onSession(uid)));
    }

    // ---- observar --------------------------------------------------------------------------------------

    /** Amigos en vivo, en orden alfabético; sigue a la sesión. */
    @NonNull
    public LiveData<FriendsState<FriendCard>> observeFriends() {
        return friends;
    }

    /** Solicitudes recibidas ({@code pending} y {@code failed}), la más nueva primero. */
    @NonNull
    public LiveData<FriendsState<FriendRequest>> observeIncoming() {
        return incoming;
    }

    /** Solicitudes enviadas en {@code pending}, la más nueva primero. */
    @NonNull
    public LiveData<FriendsState<FriendRequest>> observeOutgoing() {
        return outgoing;
    }

    /** Recibidas en {@code pending} (el punto de la campana); 0 sin sesión. */
    @NonNull
    public LiveData<Integer> pendingIncomingCount() {
        return pendingCount;
    }

    /** En el serial. */
    private void onSession(@Nullable String uid) {
        if (Objects.equals(uid, sessionUid)) {
            return; // misma sesión ya enganchada
        }
        sessionUid = uid;
        clearFriendEntries();
        cleaned.clear();
        friendIds = null;
        friendsPublishedReady = false;
        lastIncoming = Collections.emptyList();
        lastOutgoingAll = Collections.emptyList();
        if (uid == null) {
            friendsWatcher.detach();
            incomingWatcher.detach();
            outgoingWatcher.detach();
        } else {
            friendsWatcher.attach(uid);
            incomingWatcher.attach(uid);
            outgoingWatcher.attach(uid);
        }
    }

    private void onForeground() {
        serial.execute(() -> {
            friendsWatcher.onForeground();
            incomingWatcher.onForeground();
            outgoingWatcher.onForeground();
            for (FriendEntry e : friendEntries.values()) {
                e.watcher.onForeground();
            }
        });
    }

    /** Un amigo de la lista y el listener de su perfil. */
    private static final class FriendEntry {
        @NonNull
        final String uid;
        WatchedListener<String, FriendsBackend.Doc> watcher;
        /** Ya respondió (perfil, oculto o error): hasta que respondan todos, la lista sigue en LOADING. */
        boolean answered;
        /** null si está oculto (ya no es amigo, o error sin tarjeta previa). */
        @Nullable
        FriendCard card;

        FriendEntry(@NonNull String uid) {
            this.uid = uid;
        }
    }

    /** {@code users/me/friends}: concilia los listeners de perfil con el conjunto nuevo. En el serial. */
    private final class FriendsListSink implements WatchedListener.Sink<FriendsBackend.DocList> {

        @Override
        public void onLoading() {
            friends.postValue(FriendsState.loading());
        }

        @Override
        public void onDetached() {
            clearFriendEntries();
            friendIds = null;
            friendsPublishedReady = false;
            friends.postValue(FriendsState.signedOut());
        }

        @Override
        public void onError(@NonNull ListenerException error) {
            // Los perfiles ya enganchados siguen; el listener de la lista se reengancha solo (F2-u1).
            friends.postValue(FriendsState.error(error.original()));
        }

        @Override
        public void onValue(@NonNull FriendsBackend.DocList list) {
            if (sessionUid == null) {
                return;
            }
            if (list.fromCache && list.docs.isEmpty() && friendIds == null) {
                return; // la caché vacía no sabe nada; esperamos al servidor
            }
            List<String> ids = FriendsMapping.friendIds(list.docs);
            friendIds = ids;
            List<String> watched = ids.size() > MAX_FRIENDS ? ids.subList(0, MAX_FRIENDS) : ids;
            Iterator<Map.Entry<String, FriendEntry>> it = friendEntries.entrySet().iterator();
            while (it.hasNext()) {
                FriendEntry e = it.next().getValue();
                if (!watched.contains(e.uid)) {
                    it.remove(); // primero fuera del mapa: su onDetached ya no se reconoce
                    e.watcher.detach();
                }
            }
            for (String fid : watched) {
                if (!friendEntries.containsKey(fid)) {
                    FriendEntry e = new FriendEntry(fid);
                    e.watcher = new WatchedListener<>(serial, scheduler, backend.profile(), new FriendProfileSink(e));
                    friendEntries.put(fid, e);
                    e.watcher.attach(fid);
                }
            }
            publishFriends();
        }
    }

    /** {@code profiles/{fid}} de un amigo. En el serial; ignora lo de una entrada que ya no está. */
    private final class FriendProfileSink implements WatchedListener.Sink<FriendsBackend.Doc> {

        private final FriendEntry entry;

        FriendProfileSink(@NonNull FriendEntry entry) {
            this.entry = entry;
        }

        private boolean current() {
            return friendEntries.get(entry.uid) == entry;
        }

        @Override
        public void onLoading() {
            // Sigue sin responder: la lista espera a todos antes del primer READY.
        }

        @Override
        public void onDetached() {
            // Solo pasa al salir de la lista o de la sesión; nada que publicar.
        }

        @Override
        public void onValue(@NonNull FriendsBackend.Doc doc) {
            if (!current()) {
                return;
            }
            if (doc.exists()) {
                entry.card = FriendsMapping.profileCard(doc); // malformado → oculto
                entry.answered = true;
            } else if (doc.fromCache) {
                return; // la caché no sabe; esperamos al servidor
            } else {
                hideAndClean(entry);
            }
            publishFriends();
        }

        @Override
        public void onError(@NonNull ListenerException error) {
            if (!current()) {
                return;
            }
            if (PERMISSION_DENIED.equals(error.code)) {
                hideAndClean(entry);
            } else {
                entry.answered = true; // se reintenta solo; mientras, la última tarjeta (o nada)
            }
            publishFriends();
        }
    }

    /** Un amigo cuyo perfil ya no se puede leer: se oculta y se limpia una vez con {@code removeFriend}. */
    private void hideAndClean(@NonNull FriendEntry entry) {
        entry.card = null;
        entry.answered = true;
        if (sessionUid != null && cleaned.add(entry.uid)) {
            backend.callRemoveFriend(entry.uid, serial, new FriendsBackend.Result<Void>() {
                @Override
                public void onSuccess(@Nullable Void value) {
                    // El listener de users/me/friends quita la entrada.
                }

                @Override
                public void onError(@NonNull Throwable error) {
                    // Sigue oculto; se reintenta en la próxima sesión.
                }
            });
        }
    }

    private void publishFriends() {
        if (friendIds == null) {
            return;
        }
        List<FriendCard> cards = new ArrayList<>();
        boolean allAnswered = true;
        for (FriendEntry e : friendEntries.values()) {
            allAnswered &= e.answered;
            if (e.card != null) {
                cards.add(e.card);
            }
        }
        if (!allAnswered && !friendsPublishedReady) {
            return; // el primer READY espera a que respondan todos los perfiles (sin parpadeo)
        }
        friendsPublishedReady = true;
        friends.postValue(FriendsState.ready(FriendsMapping.sortFriends(cards)));
    }

    private void clearFriendEntries() {
        List<FriendEntry> old = new ArrayList<>(friendEntries.values());
        friendEntries.clear();
        for (FriendEntry e : old) {
            e.watcher.detach();
        }
    }

    /** Recibidas ({@code to == me}) o enviadas ({@code from == me}). En el serial. */
    private final class RequestsSink implements WatchedListener.Sink<FriendsBackend.DocList> {

        private final boolean isIncoming;
        private boolean known;

        RequestsSink(boolean isIncoming) {
            this.isIncoming = isIncoming;
        }

        private MutableLiveData<FriendsState<FriendRequest>> target() {
            return isIncoming ? incoming : outgoing;
        }

        @Override
        public void onLoading() {
            known = false;
            target().postValue(FriendsState.loading());
        }

        @Override
        public void onDetached() {
            known = false;
            target().postValue(FriendsState.signedOut());
            if (isIncoming) {
                pendingCount.postValue(0);
            }
        }

        @Override
        public void onError(@NonNull ListenerException error) {
            target().postValue(FriendsState.error(error.original()));
        }

        @Override
        public void onValue(@NonNull FriendsBackend.DocList list) {
            String me = sessionUid;
            if (me == null || (list.fromCache && list.docs.isEmpty() && !known)) {
                return;
            }
            known = true;
            if (isIncoming) {
                lastIncoming = FriendsMapping.incoming(list.docs, me);
                incoming.postValue(FriendsState.ready(lastIncoming));
                pendingCount.postValue(FriendsMapping.pendingCount(lastIncoming));
            } else {
                List<FriendRequest> all = new ArrayList<>();
                for (FriendsBackend.Doc doc : list.docs) {
                    FriendRequest r = FriendsMapping.request(doc);
                    if (r != null && me.equals(r.from.uid)) {
                        all.add(r);
                    }
                }
                lastOutgoingAll = all;
                outgoing.postValue(FriendsState.ready(FriendsMapping.outgoing(list.docs, me)));
            }
        }
    }

    // ---- buscar por @usuario -------------------------------------------------------------------------

    /**
     * Busca {@code raw} (normalizado con {@link HandleValidator#normalizeHandle}) en {@code handles/} del
     * servidor y dice qué relación hay. Las solicitudes se miran en las dos consultas en vivo (el cliente no
     * hace {@code get} de una solicitud que podría no existir: las reglas lo denegarían).
     */
    public void lookupHandle(@Nullable String raw, @NonNull LookupCallback callback) {
        serial.execute(() -> {
            String me = backend.currentUid();
            if (me == null) {
                callback.onError(new FriendsException(FriendsException.Code.NOT_SIGNED_IN));
                return;
            }
            String h = HandleValidator.normalizeHandle(raw);
            if (!LOOKUP_FORMAT.matcher(h).matches() || FIRESTORE_RESERVED_ID.matcher(h).matches()) {
                callback.onResult(new LookupResult(LookupResult.Kind.NOT_FOUND, null, null));
                return;
            }
            backend.getFromServer(FriendRequestWrites.handlePath(h), serial, new FriendsBackend.Result<FriendsBackend.Doc>() {
                @Override
                public void onSuccess(@Nullable FriendsBackend.Doc doc) {
                    FriendCard card = doc == null ? null : FriendsMapping.handleCard(doc);
                    if (card == null) {
                        callback.onResult(new LookupResult(LookupResult.Kind.NOT_FOUND, null, null));
                    } else if (card.uid.equals(me)) {
                        callback.onResult(new LookupResult(LookupResult.Kind.SELF, card, null));
                    } else {
                        lookupRelation(me, card, callback);
                    }
                }

                @Override
                public void onError(@NonNull Throwable error) {
                    callback.onError(error);
                }
            });
        });
    }

    private void lookupRelation(@NonNull String me, @NonNull FriendCard card, @NonNull LookupCallback callback) {
        backend.getFromServer(FriendRequestWrites.friendPath(me, card.uid), serial,
                new FriendsBackend.Result<FriendsBackend.Doc>() {
                    @Override
                    public void onSuccess(@Nullable FriendsBackend.Doc doc) {
                        if (doc != null && doc.exists()) {
                            callback.onResult(new LookupResult(LookupResult.Kind.ALREADY_FRIENDS, card, null));
                            return;
                        }
                        for (FriendRequest r : lastIncoming) {
                            if (r.from.uid.equals(card.uid)) {
                                callback.onResult(new LookupResult(LookupResult.Kind.THEY_SENT_YOU, card, r));
                                return;
                            }
                        }
                        for (FriendRequest r : lastOutgoingAll) {
                            if (r.to.uid.equals(card.uid)) {
                                callback.onResult(new LookupResult(LookupResult.Kind.ALREADY_SENT, card, null));
                                return;
                            }
                        }
                        callback.onResult(new LookupResult(LookupResult.Kind.OK, card, null));
                    }

                    @Override
                    public void onError(@NonNull Throwable error) {
                        callback.onError(error);
                    }
                });
    }

    // ---- escribir --------------------------------------------------------------------------------------

    /**
     * Envía una solicitud a {@code to} (la tarjeta de {@link #lookupHandle}): tope propio de 50, perfil
     * propio y {@code users/me} del servidor, {@link RequestQuota} ({@code DAILY_LIMIT}) y un lote con la
     * solicitud y el contador. Si alguien renombró entre medias, las reglas deniegan el lote.
     */
    public void send(@NonNull FriendCard to, @NonNull Callback callback) {
        serial.execute(() -> {
            String me = backend.currentUid();
            if (me == null) {
                callback.onError(new FriendsException(FriendsException.Code.NOT_SIGNED_IN));
                return;
            }
            if (atCap()) {
                callback.onError(new FriendsException(FriendsException.Code.FRIEND_CAP));
                return;
            }
            backend.getFromServer(FriendRequestWrites.profilePath(me), serial, new Step<FriendsBackend.Doc>(callback) {
                @Override
                public void onSuccess(@Nullable FriendsBackend.Doc profile) {
                    FriendCard mine = profile == null ? null : FriendsMapping.profileCard(profile);
                    if (mine == null) {
                        callback.onError(new FriendsException(FriendsException.Code.NO_PROFILE));
                        return;
                    }
                    backend.getFromServer(FriendRequestWrites.userPath(me), serial, new Step<FriendsBackend.Doc>(callback) {
                        @Override
                        public void onSuccess(@Nullable FriendsBackend.Doc user) {
                            if (user == null || user.data == null) {
                                callback.onError(new FriendsException(FriendsException.Code.NO_PROFILE));
                                return;
                            }
                            RequestQuota.Next next = RequestQuota.next(asLong(user.data.get(FriendRequestWrites.REQ_DAY)),
                                    asLong(user.data.get(FriendRequestWrites.REQ_COUNT)), clock.nowMillis());
                            if (!next.allowed) {
                                callback.onError(new FriendsException(FriendsException.Code.DAILY_LIMIT));
                                return;
                            }
                            backend.batch()
                                    .set(FriendRequestWrites.requestPath(FriendRequestWrites.requestId(me, to.uid)),
                                            FriendRequestWrites.request(mine, to))
                                    .update(FriendRequestWrites.userPath(me), FriendRequestWrites.counter(next, to.uid))
                                    .commit(serial, done(callback));
                        }
                    });
                }
            });
        });
    }

    /** Acepta una solicitud recibida ({@code pending → accepted}); antes, el tope propio de 50. */
    public void accept(@NonNull FriendRequest request, @NonNull Callback callback) {
        serial.execute(() -> {
            if (backend.currentUid() == null) {
                callback.onError(new FriendsException(FriendsException.Code.NOT_SIGNED_IN));
                return;
            }
            if (atCap()) {
                callback.onError(new FriendsException(FriendsException.Code.FRIEND_CAP));
                return;
            }
            backend.batch()
                    .update(FriendRequestWrites.requestPath(request.id), FriendRequestWrites.accept())
                    .commit(serial, done(callback));
        });
    }

    /** Rechaza una recibida (o descarta una {@code failed}): la borra. El remitente no recibe aviso. */
    public void decline(@NonNull FriendRequest request, @NonNull Callback callback) {
        deleteRequest(request, callback);
    }

    /** Cancela una enviada: la borra. */
    public void cancel(@NonNull FriendRequest request, @NonNull Callback callback) {
        deleteRequest(request, callback);
    }

    /** Elimina a un amigo con la callable {@code removeFriend} (los dos lados y el espejo). */
    public void remove(@NonNull String friendUid, @NonNull Callback callback) {
        serial.execute(() -> {
            if (backend.currentUid() == null) {
                callback.onError(new FriendsException(FriendsException.Code.NOT_SIGNED_IN));
                return;
            }
            backend.callRemoveFriend(friendUid, serial, done(callback));
        });
    }

    private void deleteRequest(@NonNull FriendRequest request, @NonNull Callback callback) {
        serial.execute(() -> {
            if (backend.currentUid() == null) {
                callback.onError(new FriendsException(FriendsException.Code.NOT_SIGNED_IN));
                return;
            }
            backend.batch()
                    .delete(FriendRequestWrites.requestPath(request.id))
                    .commit(serial, done(callback));
        });
    }

    // ---- utilidades ------------------------------------------------------------------------------------

    /** En el serial: el tope propio, según la última lista de amigos (la Function lo vuelve a mirar). */
    private boolean atCap() {
        return friendIds != null && friendIds.size() >= MAX_FRIENDS;
    }

    /** Un paso intermedio: los errores van directos al {@link Callback}. */
    private abstract static class Step<T> implements FriendsBackend.Result<T> {
        private final Callback callback;

        Step(@NonNull Callback callback) {
            this.callback = callback;
        }

        @Override
        public void onError(@NonNull Throwable error) {
            callback.onError(error);
        }
    }

    @NonNull
    private static FriendsBackend.Result<Void> done(@NonNull Callback callback) {
        return new FriendsBackend.Result<Void>() {
            @Override
            public void onSuccess(@Nullable Void value) {
                callback.onSuccess();
            }

            @Override
            public void onError(@NonNull Throwable error) {
                callback.onError(error);
            }
        };
    }

    @Nullable
    private static Long asLong(@Nullable Object value) {
        return value instanceof Number ? ((Number) value).longValue() : null;
    }
}
