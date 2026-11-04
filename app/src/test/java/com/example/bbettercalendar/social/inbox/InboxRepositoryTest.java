package com.example.bbettercalendar.social.inbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;
import androidx.lifecycle.Observer;

import com.example.bbettercalendar.social.common.ListenerException;
import com.example.bbettercalendar.social.common.ListenerSource;
import com.example.bbettercalendar.social.common.Scheduler;
import com.example.bbettercalendar.social.common.SerialExecutor;
import com.google.firebase.Timestamp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * F5-u5: {@link InboxRepository} con Firebase simulado por Mockito a través de {@link InboxBackend} y su
 * {@link ListenerSource}: escucha solo mientras se observa, sin sesión no engancha nada, se suelta al cerrar
 * sesión, descarta lo ilegible, cuenta lo no leído y escribe solo {@code read = true}. El serial corre sobre un
 * ejecutor directo y {@code postValue} se entrega en el acto.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class InboxRepositoryTest {

    private InboxBackend backend;
    private ListenerSource<String, InboxBackend.DocList> source;
    private ListenerSource.Registration registration;
    private final List<String> listens = new ArrayList<>();
    private ListenerSource.Events events;
    private InboxBackend.AuthListener auth;
    private String uid;

    private final List<InboxState> states = new ArrayList<>();
    private final List<Integer> counts = new ArrayList<>();
    private final Observer<InboxState> stateObserver = states::add;
    private final Observer<Integer> countObserver = counts::add;
    private InboxRepository repo;

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
        uid = "me";
        build();
    }

    private void build() {
        backend = mock(InboxBackend.class);
        source = mock(ListenerSource.class);
        registration = mock(ListenerSource.Registration.class);
        when(source.listen(anyString(), any(Executor.class), any(ListenerSource.Events.class))).thenAnswer(inv -> {
            listens.add(inv.getArgument(0));
            events = inv.getArgument(2);
            return registration;
        });
        when(backend.inbox()).thenReturn(source);
        when(backend.currentUid()).thenAnswer(inv -> uid);
        repo = new InboxRepository(backend, new SerialExecutor(Runnable::run), mock(Scheduler.class));
        ArgumentCaptor<InboxBackend.AuthListener> captor = ArgumentCaptor.forClass(InboxBackend.AuthListener.class);
        verify(backend).addAuthListener(captor.capture());
        auth = captor.getValue();
    }

    @After
    public void tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    private void observe() {
        repo.observeInbox().observeForever(stateObserver);
        repo.unreadCount().observeForever(countObserver);
    }

    private void stopObserving() {
        repo.observeInbox().removeObserver(stateObserver);
        repo.unreadCount().removeObserver(countObserver);
    }

    private static Map<String, Object> fields(String type, long millis, boolean read) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        m.put("from", "uidAna");
        m.put("fromHandle", "ana");
        m.put("fromName", "Ana");
        m.put("fromColor", 2L);
        m.put("createdAt", new Timestamp(new Date(millis)));
        m.put("read", read);
        return m;
    }

    private void emit(InboxBackend.Doc... docs) {
        events.onEvent(new InboxBackend.DocList(new ArrayList<>(Arrays.asList(docs)), false), false, null);
    }

    private static InboxBackend.Doc doc(String id, String type, long millis, boolean read) {
        return new InboxBackend.Doc(id, fields(type, millis, read));
    }

    private InboxState last() {
        return repo.observeInbox().getValue();
    }

    @Test
    public void withoutASessionTheStateIsSignedOutAndNoListenerIsOpened() {
        uid = null;
        build();
        observe();
        assertEquals(InboxState.Status.SIGNED_OUT, last().status);
        assertEquals(Integer.valueOf(0), repo.unreadCount().getValue());
        verify(source, never()).listen(anyString(), any(Executor.class), any(ListenerSource.Events.class));
    }

    @Test
    public void itOpensTheListenerOnlyWhileObservedAndReleasesItWhenTheLastObserverLeaves() {
        assertEquals(0, listens.size());
        repo.observeInbox().observeForever(stateObserver);
        assertEquals(Collections.singletonList("me"), listens);
        repo.unreadCount().observeForever(countObserver);
        assertEquals("a second observer shares the one listener", 1, listens.size());

        repo.observeInbox().removeObserver(stateObserver);
        verify(registration, never()).remove();
        repo.unreadCount().removeObserver(countObserver);
        verify(registration, times(1)).remove();
    }

    @Test
    public void itPublishesTheInboxNewestFirstAndCountsTheUnreadOnes() {
        observe();
        assertEquals(InboxState.Status.LOADING, last().status);
        emit(doc("a", "cheer", 1_000L, true), doc("b", "nudge", 3_000L, false), doc("c", "request", 2_000L, false));
        assertEquals(InboxState.Status.READY, last().status);
        assertEquals("b", last().items.get(0).id);
        assertEquals("c", last().items.get(1).id);
        assertEquals("a", last().items.get(2).id);
        assertEquals(Integer.valueOf(2), repo.unreadCount().getValue());
    }

    @Test
    public void anUnreadableOrMalformedDocumentIsSkippedAndNeverBreaksTheList() {
        observe();
        Map<String, Object> broken = fields("cheer", 5_000L, false);
        broken.put("fromColor", "not a number");
        emit(doc("ok", "cheer", 1_000L, false), new InboxBackend.Doc("broken", broken),
                new InboxBackend.Doc("gone", null), doc("odd", "poke", 9_000L, false));
        assertEquals(InboxState.Status.READY, last().status);
        assertEquals(1, last().items.size());
        assertEquals("ok", last().items.get(0).id);
        assertEquals(Integer.valueOf(1), repo.unreadCount().getValue());
    }

    @Test
    public void signingOutReleasesTheListenerAndClearsTheDot() {
        observe();
        emit(doc("a", "cheer", 1_000L, false));
        assertEquals(Integer.valueOf(1), repo.unreadCount().getValue());

        uid = null;
        auth.onAuth(null);
        verify(registration, times(1)).remove();
        assertEquals(InboxState.Status.SIGNED_OUT, last().status);
        assertEquals(Integer.valueOf(0), repo.unreadCount().getValue());

        events.onEvent(new InboxBackend.DocList(Collections.singletonList(doc("late", "cheer", 9L, false)), false),
                false, null);
        assertEquals("a late event of the old session is dropped", InboxState.Status.SIGNED_OUT, last().status);
    }

    @Test
    public void signingInWhileObservedStartsListeningToTheNewAccount() {
        uid = null;
        build();
        observe();
        assertEquals(0, listens.size());
        uid = "me2";
        auth.onAuth("me2");
        assertEquals(Collections.singletonList("me2"), listens);
        assertEquals(InboxState.Status.LOADING, last().status);
    }

    @Test
    public void markAllReadWritesOnlyTheUnreadIdsOfTheSignedInAccount() {
        observe();
        emit(doc("a", "cheer", 1_000L, true), doc("b", "nudge", 3_000L, false), doc("c", "request", 2_000L, false));
        repo.markAllRead();
        ArgumentCaptor<String> ids = ArgumentCaptor.forClass(String.class);
        verify(backend, times(2)).markRead(eq("me"), ids.capture(), any(Executor.class),
                any(InboxBackend.Result.class));
        assertEquals(new java.util.HashSet<>(Arrays.asList("b", "c")), new java.util.HashSet<>(ids.getAllValues()));
    }

    @Test
    public void markAllReadWritesEachIdEvenIfOneFails() {
        observe();
        emit(doc("a", "cheer", 1_000L, false), doc("b", "nudge", 3_000L, false), doc("c", "request", 2_000L, false));
        org.mockito.Mockito.doAnswer(inv -> {
            ((InboxBackend.Result) inv.getArgument(3)).onError(new RuntimeException("PERMISSION_DENIED"));
            return null;
        }).when(backend).markRead(eq("me"), eq("b"), any(Executor.class), any(InboxBackend.Result.class));
        repo.markAllRead();
        for (String id : Arrays.asList("a", "b", "c")) {
            verify(backend).markRead(eq("me"), eq(id), any(Executor.class), any(InboxBackend.Result.class));
        }
    }

    @Test
    public void markAllReadDoesNothingWhenEverythingIsRead() {
        observe();
        emit(doc("a", "cheer", 1_000L, true));
        repo.markAllRead();
        verify(backend, never()).markRead(anyString(), anyString(), any(Executor.class), any(InboxBackend.Result.class));
    }

    @Test
    public void markReadWritesThatIdEvenBeforeTheListFirstArrivesButNotForAKnownReadOne() {
        observe();
        repo.markRead("cheer_uidAna_2026-11-02");
        verify(backend).markRead(eq("me"), eq("cheer_uidAna_2026-11-02"),
                any(Executor.class), any(InboxBackend.Result.class));

        emit(doc("done", "cheer", 1_000L, true));
        repo.markRead("done");
        verify(backend, never()).markRead(anyString(), eq("done"),
                any(Executor.class), any(InboxBackend.Result.class));
    }

    @Test
    public void markReadWithoutASessionWritesNothing() {
        uid = null;
        build();
        repo.markRead("x");
        repo.markAllRead();
        verify(backend, never()).markRead(anyString(), anyString(), any(Executor.class), any(InboxBackend.Result.class));
    }

    @Test
    public void aListenerErrorPublishesErrorButKeepsTheDotAsItWas() {
        observe();
        emit(doc("a", "cheer", 1_000L, false));
        events.onEvent(null, false, new ListenerException("PERMISSION_DENIED", null));
        assertEquals(InboxState.Status.ERROR, last().status);
        assertNotNull(last().error);
        assertEquals(Integer.valueOf(1), repo.unreadCount().getValue());
    }

    @Test
    public void anEmptyCacheAnswerBeforeTheServerIsNotPublishedAsAnEmptyInbox() {
        observe();
        events.onEvent(new InboxBackend.DocList(new ArrayList<>(), true), true, null);
        assertEquals(InboxState.Status.LOADING, last().status);
        events.onEvent(new InboxBackend.DocList(new ArrayList<>(), false), false, null);
        assertEquals(InboxState.Status.READY, last().status);
        assertNull(last().error);
    }
}
