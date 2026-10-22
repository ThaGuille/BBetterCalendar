package com.example.bbettercalendar.social.friends;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

import com.example.bbettercalendar.social.common.ListenerException;
import com.example.bbettercalendar.social.common.ListenerSource;
import com.example.bbettercalendar.social.common.Scheduler;
import com.example.bbettercalendar.social.common.SerialExecutor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * F2-u5: {@link FriendsRepository} con Firebase simulado por Mockito a través de las interfaces propias
 * ({@link FriendsBackend} y los {@link ListenerSource} de u1; decisión del usuario, sin Robolectric):
 * un listener de perfil por amigo que se engancha y se quita al cambiar el conjunto, un
 * {@code PERMISSION_DENIED} o un perfil ausente que oculta al amigo y lo limpia con {@code removeFriend},
 * los topes (50 amigos, 20 al día) antes de escribir, el lote de enviar y {@code lookupHandle}.
 *
 * <p>El serial corre sobre un ejecutor directo (en orden, sin reentrar) y {@code postValue} se entrega en
 * el acto con un {@link TaskExecutor} de prueba.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class FriendsRepositoryTest {

    private static final long NOW = 20_000L * RequestQuota.DAY_MS + 3_600_000L;
    private static final FriendCard BOB = new FriendCard("uidB", "bob", "Bob", 5);

    private FriendsBackend backend;
    private ListenerSource<String, FriendsBackend.DocList> friendsSrc;
    private ListenerSource<String, FriendsBackend.DocList> toSrc;
    private ListenerSource<String, FriendsBackend.DocList> fromSrc;
    private ListenerSource<String, FriendsBackend.Doc> profileSrc;
    private final Map<String, ListenerSource.Events> listEvents = new HashMap<>();
    private final Map<String, ListenerSource.Events> profileEvents = new HashMap<>();
    private final Map<String, ListenerSource.Registration> profileRegs = new HashMap<>();
    private final List<String> profileListens = new ArrayList<>();
    private final Map<String, Map<String, Object>> serverDocs = new HashMap<>();
    private FriendsBackend.Batch batch;
    private FriendsBackend.AuthListener auth;
    private FriendsRepository repo;

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
        backend = mock(FriendsBackend.class);
        friendsSrc = listSource("friends");
        toSrc = listSource("to");
        fromSrc = listSource("from");
        profileSrc = mock(ListenerSource.class);
        when(profileSrc.listen(anyString(), any(Executor.class), any(ListenerSource.Events.class))).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            ListenerSource.Registration reg = mock(ListenerSource.Registration.class);
            profileRegs.put(key, reg);
            profileEvents.put(key, inv.getArgument(2));
            profileListens.add(key);
            return reg;
        });
        when(backend.friendsOf()).thenReturn(friendsSrc);
        when(backend.requestsTo()).thenReturn(toSrc);
        when(backend.requestsFrom()).thenReturn(fromSrc);
        when(backend.profile()).thenReturn(profileSrc);
        when(backend.currentUid()).thenReturn("me");
        doAnswer(inv -> {
            String path = inv.getArgument(0);
            FriendsBackend.Result<FriendsBackend.Doc> r = inv.getArgument(2);
            String id = path.substring(path.lastIndexOf('/') + 1);
            r.onSuccess(new FriendsBackend.Doc(id, serverDocs.get(path), false));
            return null;
        }).when(backend).getFromServer(anyString(), any(Executor.class), any(FriendsBackend.Result.class));
        batch = mock(FriendsBackend.Batch.class);
        when(backend.batch()).thenReturn(batch);
        when(batch.set(anyString(), anyMap())).thenReturn(batch);
        when(batch.update(anyString(), anyMap())).thenReturn(batch);
        when(batch.delete(anyString())).thenReturn(batch);
        doAnswer(inv -> {
            ((FriendsBackend.Result) inv.getArgument(1)).onSuccess(null);
            return null;
        }).when(batch).commit(any(Executor.class), any(FriendsBackend.Result.class));

        repo = new FriendsRepository(backend, new SerialExecutor(Runnable::run), mock(Scheduler.class), () -> NOW);
        ArgumentCaptor<FriendsBackend.AuthListener> captor = ArgumentCaptor.forClass(FriendsBackend.AuthListener.class);
        verify(backend).addAuthListener(captor.capture());
        auth = captor.getValue();
        auth.onAuth("me");
    }

    @After
    public void tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    private ListenerSource<String, FriendsBackend.DocList> listSource(String name) {
        ListenerSource<String, FriendsBackend.DocList> src = mock(ListenerSource.class);
        when(src.listen(anyString(), any(Executor.class), any(ListenerSource.Events.class))).thenAnswer(inv -> {
            listEvents.put(name, inv.getArgument(2));
            return mock(ListenerSource.Registration.class);
        });
        return src;
    }

    // ---- ayudantes -------------------------------------------------------------------------------------

    private void emitFriends(String... uids) {
        List<FriendsBackend.Doc> docs = new ArrayList<>();
        for (String uid : uids) {
            docs.add(new FriendsBackend.Doc(uid, Collections.singletonMap("since", (Object) 1L), false));
        }
        listEvents.get("friends").onEvent(new FriendsBackend.DocList(docs, false), false, null);
    }

    private static Map<String, Object> profile(String name, String handle, long color) {
        Map<String, Object> m = new HashMap<>();
        m.put("displayName", name);
        m.put("handle", handle);
        m.put("avatarColor", color);
        return m;
    }

    private void emitProfile(String uid, String name) {
        profileEvents.get(uid).onEvent(new FriendsBackend.Doc(uid, profile(name, name.toLowerCase(), 1L), false),
                false, null);
    }

    private void emitRequests(String which, FriendsBackend.Doc... docs) {
        List<FriendsBackend.Doc> list = new ArrayList<>();
        Collections.addAll(list, docs);
        listEvents.get(which).onEvent(new FriendsBackend.DocList(list, false), false, null);
    }

    private static FriendsBackend.Doc requestDoc(String from, String to) {
        Map<String, Object> m = new HashMap<>();
        m.put("from", from);
        m.put("to", to);
        m.put("status", "pending");
        m.put("fromHandle", "h" + from);
        m.put("fromName", "N" + from);
        m.put("fromColor", 1L);
        m.put("toHandle", "h" + to);
        m.put("toName", "N" + to);
        m.put("toColor", 2L);
        return new FriendsBackend.Doc(from + "_" + to, m, false);
    }

    private List<String> friendNames() {
        List<String> names = new ArrayList<>();
        for (FriendCard c : repo.observeFriends().getValue().items) {
            names.add(c.displayName);
        }
        return names;
    }

    private static final class RecordingCallback implements FriendsRepository.Callback {
        int successes;
        Throwable error;

        @Override
        public void onSuccess() {
            successes++;
        }

        @Override
        public void onError(Throwable e) {
            error = e;
        }
    }

    private static final class RecordingLookup implements FriendsRepository.LookupCallback {
        FriendsRepository.LookupResult result;
        Throwable error;

        @Override
        public void onResult(FriendsRepository.LookupResult r) {
            result = r;
        }

        @Override
        public void onError(Throwable e) {
            error = e;
        }
    }

    // ---- observar --------------------------------------------------------------------------------------

    @Test
    public void session_attachesTheThreeListenersWithMyUid() {
        verify(friendsSrc).listen(eq("me"), any(Executor.class), any(ListenerSource.Events.class));
        verify(toSrc).listen(eq("me"), any(Executor.class), any(ListenerSource.Events.class));
        verify(fromSrc).listen(eq("me"), any(Executor.class), any(ListenerSource.Events.class));
        assertEquals(FriendsState.Status.LOADING, repo.observeFriends().getValue().status);
    }

    @Test
    public void profileListeners_areAddedAndRemovedAsTheFriendSetChanges() {
        emitFriends("c", "b");
        assertEquals(2, profileListens.size());
        assertTrue(profileListens.contains("b") && profileListens.contains("c"));
        // Hasta que respondan todos los perfiles, la lista sigue cargando.
        emitProfile("c", "Carol");
        assertEquals(FriendsState.Status.LOADING, repo.observeFriends().getValue().status);
        emitProfile("b", "Bob");
        assertEquals(FriendsState.Status.READY, repo.observeFriends().getValue().status);
        assertEquals(java.util.Arrays.asList("Bob", "Carol"), friendNames());

        emitFriends("c", "d"); // b sale, d entra
        verify(profileRegs.get("b")).remove();
        verify(profileRegs.get("c"), never()).remove();
        assertEquals(3, profileListens.size());
        assertEquals("d", profileListens.get(2));
        assertEquals(java.util.Arrays.asList("Carol"), friendNames());
        emitProfile("d", "Dave");
        assertEquals(java.util.Arrays.asList("Carol", "Dave"), friendNames());

        // Cerrar sesión quita todos los listeners de perfil.
        auth.onAuth(null);
        verify(profileRegs.get("c")).remove();
        verify(profileRegs.get("d")).remove();
        assertEquals(FriendsState.Status.SIGNED_OUT, repo.observeFriends().getValue().status);
    }

    @Test
    public void permissionDeniedOrMissingProfile_hidesTheFriendAndCallsRemoveFriendOnce() {
        emitFriends("b", "c", "d");
        emitProfile("b", "Bob");
        profileEvents.get("c").onEvent(null, false, new ListenerException("PERMISSION_DENIED", null));
        // Un «no existe» de la caché no prueba nada: ni se oculta ni se limpia.
        profileEvents.get("d").onEvent(new FriendsBackend.Doc("d", null, true), true, null);
        verify(backend, never()).callRemoveFriend(eq("d"), any(Executor.class), any(FriendsBackend.Result.class));
        assertEquals(FriendsState.Status.LOADING, repo.observeFriends().getValue().status);
        // El servidor confirma que no existe: oculto y limpiado; no es un error.
        profileEvents.get("d").onEvent(new FriendsBackend.Doc("d", null, false), false, null);
        assertEquals(FriendsState.Status.READY, repo.observeFriends().getValue().status);
        assertNull(repo.observeFriends().getValue().error);
        assertEquals(java.util.Arrays.asList("Bob"), friendNames());
        verify(backend, times(1)).callRemoveFriend(eq("c"), any(Executor.class), any(FriendsBackend.Result.class));
        verify(backend, times(1)).callRemoveFriend(eq("d"), any(Executor.class), any(FriendsBackend.Result.class));
        verify(backend, never()).callRemoveFriend(eq("b"), any(Executor.class), any(FriendsBackend.Result.class));
    }

    @Test
    public void incoming_publishesSortedListAndPendingCount() {
        emitRequests("to", requestDoc("uidB", "me"), requestDoc("uidC", "me"));
        assertEquals(FriendsState.Status.READY, repo.observeIncoming().getValue().status);
        assertEquals(2, repo.observeIncoming().getValue().items.size());
        assertEquals(Integer.valueOf(2), repo.pendingIncomingCount().getValue());
        emitRequests("from", requestDoc("me", "uidD"));
        assertEquals("me_uidD", repo.observeOutgoing().getValue().items.get(0).id);
        auth.onAuth(null);
        assertEquals(Integer.valueOf(0), repo.pendingIncomingCount().getValue());
        assertEquals(FriendsState.Status.SIGNED_OUT, repo.observeIncoming().getValue().status);
    }

    // ---- topes y escribir ------------------------------------------------------------------------------

    @Test
    public void sendAndAccept_checkTheOwnFiftyFriendCapFirst() {
        String[] fifty = new String[FriendsRepository.MAX_FRIENDS];
        for (int i = 0; i < fifty.length; i++) {
            fifty[i] = String.format("f%02d", i);
        }
        emitFriends(fifty);
        assertEquals(FriendsRepository.MAX_FRIENDS, profileListens.size());

        RecordingCallback send = new RecordingCallback();
        repo.send(BOB, send);
        assertEquals(FriendsException.Code.FRIEND_CAP, ((FriendsException) send.error).code);

        RecordingCallback accept = new RecordingCallback();
        repo.accept(FriendsMapping.request(requestDoc("uidB", "me")), accept);
        assertEquals(FriendsException.Code.FRIEND_CAP, ((FriendsException) accept.error).code);

        verify(backend, never()).batch();
        verify(backend, never()).getFromServer(anyString(), any(Executor.class), any(FriendsBackend.Result.class));
    }

    @Test
    public void send_writesRequestAndCounterInOneBatch() {
        emitFriends();
        serverDocs.put("profiles/me", profile("Alice", "alice", 3L));
        Map<String, Object> user = new HashMap<>();
        user.put("ageBand", "18+");
        user.put("reqDay", 20_000L);
        user.put("reqCount", 4L);
        serverDocs.put("users/me", user);

        RecordingCallback cb = new RecordingCallback();
        repo.send(BOB, cb);
        assertNull(cb.error);
        assertEquals(1, cb.successes);

        ArgumentCaptor<Map> request = ArgumentCaptor.forClass(Map.class);
        verify(batch).set(eq("friendRequests/me_uidB"), request.capture());
        assertEquals("me", request.getValue().get("from"));
        assertEquals("uidB", request.getValue().get("to"));
        assertEquals("alice", request.getValue().get("fromHandle"));
        assertEquals("bob", request.getValue().get("toHandle"));
        Map<String, Object> counter = new HashMap<>();
        counter.put("reqDay", 20_000L);
        counter.put("reqCount", 5L);
        counter.put("reqLastTo", "uidB");
        verify(batch).update("users/me", counter);
        verify(batch, times(1)).commit(any(Executor.class), any(FriendsBackend.Result.class));
        verify(backend, times(1)).batch();
    }

    @Test
    public void send_atTheDailyLimit_isRefusedWithoutWriting() {
        emitFriends();
        serverDocs.put("profiles/me", profile("Alice", "alice", 3L));
        Map<String, Object> user = new HashMap<>();
        user.put("reqDay", 20_000L);
        user.put("reqCount", 20L);
        serverDocs.put("users/me", user);

        RecordingCallback cb = new RecordingCallback();
        repo.send(BOB, cb);
        assertEquals(FriendsException.Code.DAILY_LIMIT, ((FriendsException) cb.error).code);
        verify(backend, never()).batch();
    }

    @Test
    public void declineCancelDelete_andRemoveCallsTheCallable() {
        FriendRequest in = FriendsMapping.request(requestDoc("uidB", "me"));
        RecordingCallback cb = new RecordingCallback();
        repo.decline(in, cb);
        repo.cancel(FriendsMapping.request(requestDoc("me", "uidC")), cb);
        verify(batch).delete("friendRequests/uidB_me");
        verify(batch).delete("friendRequests/me_uidC");
        repo.remove("uidB", cb);
        verify(backend).callRemoveFriend(eq("uidB"), any(Executor.class), any(FriendsBackend.Result.class));
    }

    // ---- lookupHandle ----------------------------------------------------------------------------------

    @Test
    public void lookupHandle_reportsEveryRelation() {
        Map<String, Object> bobCard = new HashMap<>();
        bobCard.put("uid", "uidB");
        bobCard.put("displayName", "Bob");
        bobCard.put("avatarColor", 5L);
        serverDocs.put("handles/bob", bobCard);
        Map<String, Object> mine = new HashMap<>(bobCard);
        mine.put("uid", "me");
        serverDocs.put("handles/alice", mine);

        RecordingLookup r = new RecordingLookup();
        repo.lookupHandle("  @Bob ", r);
        assertEquals(FriendsRepository.LookupResult.Kind.OK, r.result.kind);
        assertEquals(BOB, r.result.card);

        repo.lookupHandle("alice", r);
        assertEquals(FriendsRepository.LookupResult.Kind.SELF, r.result.kind);
        repo.lookupHandle("nadie", r);
        assertEquals(FriendsRepository.LookupResult.Kind.NOT_FOUND, r.result.kind);
        repo.lookupHandle("__ab__", r); // id reservado: ni se lee
        assertEquals(FriendsRepository.LookupResult.Kind.NOT_FOUND, r.result.kind);
        verify(backend, never()).getFromServer(eq("handles/__ab__"), any(Executor.class),
                any(FriendsBackend.Result.class));

        emitRequests("from", requestDoc("me", "uidB"));
        repo.lookupHandle("bob", r);
        assertEquals(FriendsRepository.LookupResult.Kind.ALREADY_SENT, r.result.kind);

        emitRequests("to", requestDoc("uidB", "me"));
        repo.lookupHandle("bob", r);
        assertEquals(FriendsRepository.LookupResult.Kind.THEY_SENT_YOU, r.result.kind);
        assertEquals("uidB", r.result.request.from.uid);

        serverDocs.put("users/me/friends/uidB", Collections.singletonMap("since", (Object) 1L));
        repo.lookupHandle("bob", r);
        assertEquals(FriendsRepository.LookupResult.Kind.ALREADY_FRIENDS, r.result.kind);
    }
}
