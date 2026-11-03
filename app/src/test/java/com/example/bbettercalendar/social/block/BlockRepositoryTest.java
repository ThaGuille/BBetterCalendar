package com.example.bbettercalendar.social.block;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import androidx.lifecycle.Observer;

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
 * L-u9: {@link BlockRepository} con Firebase simulado por Mockito a través de {@link BlockBackend}: sin sesión no
 * engancha nada, publica la lista más nueva primero, descarta lo malformado, mapea bloquear y desbloquear y suelta
 * el listener al cerrar sesión. El serial corre sobre un ejecutor directo y {@code postValue} llega en el acto.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class BlockRepositoryTest {

    private BlockBackend backend;
    private ListenerSource<String, BlockBackend.DocList> source;
    private ListenerSource.Registration registration;
    private final List<String> listens = new ArrayList<>();
    private ListenerSource.Events events;
    private BlockBackend.AuthListener auth;
    private String uid;

    private final List<BlockState> states = new ArrayList<>();
    private final Observer<BlockState> observer = states::add;
    private BlockRepository repo;

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
        backend = mock(BlockBackend.class);
        source = mock(ListenerSource.class);
        registration = mock(ListenerSource.Registration.class);
        when(source.listen(anyString(), any(Executor.class), any(ListenerSource.Events.class))).thenAnswer(inv -> {
            listens.add(inv.getArgument(0));
            events = inv.getArgument(2);
            return registration;
        });
        when(backend.blocked()).thenReturn(source);
        when(backend.currentUid()).thenAnswer(inv -> uid);
        repo = new BlockRepository(backend, new SerialExecutor(Runnable::run), mock(Scheduler.class));
        ArgumentCaptor<BlockBackend.AuthListener> captor = ArgumentCaptor.forClass(BlockBackend.AuthListener.class);
        verify(backend).addAuthListener(captor.capture());
        auth = captor.getValue();
    }

    @After
    public void tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    private static BlockBackend.Doc doc(String id, long millis) {
        Map<String, Object> m = new HashMap<>();
        m.put("handle", "h" + id);
        m.put("displayName", "N" + id);
        m.put("avatarColor", 1L);
        m.put("since", new Timestamp(new Date(millis)));
        return new BlockBackend.Doc(id, m);
    }

    private void emit(BlockBackend.Doc... docs) {
        events.onEvent(new BlockBackend.DocList(new ArrayList<>(Arrays.asList(docs)), false), false, null);
    }

    private BlockState last() {
        return repo.observeBlocked().getValue();
    }

    @Test
    public void withoutASessionTheStateIsSignedOutAndNoListenerIsOpened() {
        uid = null;
        build();
        repo.observeBlocked().observeForever(observer);
        assertEquals(BlockState.Status.SIGNED_OUT, last().status);
        verify(source, never()).listen(anyString(), any(Executor.class), any(ListenerSource.Events.class));
    }

    @Test
    public void itListensOnlyWhileObservedAndPublishesNewestFirst() {
        assertEquals(0, listens.size());
        repo.observeBlocked().observeForever(observer);
        assertEquals(Collections.singletonList("me"), listens);
        assertEquals(BlockState.Status.LOADING, last().status);
        emit(doc("a", 1_000L), doc("b", 3_000L), doc("c", 2_000L));
        assertEquals(BlockState.Status.READY, last().status);
        assertEquals("b", last().blocked.get(0).uid);
        assertEquals("c", last().blocked.get(1).uid);
        assertEquals("a", last().blocked.get(2).uid);

        repo.observeBlocked().removeObserver(observer);
        verify(registration, times(1)).remove();
    }

    @Test
    public void aMalformedDocumentIsSkipped() {
        repo.observeBlocked().observeForever(observer);
        Map<String, Object> broken = new HashMap<>();
        broken.put("handle", 5L);
        emit(doc("ok", 1_000L), new BlockBackend.Doc("broken", broken), new BlockBackend.Doc("gone", null));
        assertEquals(BlockState.Status.READY, last().status);
        assertEquals(1, last().blocked.size());
        assertEquals("ok", last().blocked.get(0).uid);
    }

    @Test
    public void blockMapsTheCallableAnswer() {
        List<BlockResult> out = new ArrayList<>();
        doAnswer(inv -> {
            ((BlockBackend.CallResult) inv.getArgument(2)).onSuccess(Collections.<String, Object>singletonMap("status", "blocked"));
            return null;
        }).when(backend).blockUser(eq("u1"), any(Executor.class), any(BlockBackend.CallResult.class));
        repo.block("u1", out::add);
        assertEquals(Collections.singletonList(BlockResult.BLOCKED), out);

        doAnswer(inv -> {
            ((BlockBackend.CallResult) inv.getArgument(2)).onSuccess(Collections.<String, Object>singletonMap("status", "nope"));
            return null;
        }).when(backend).blockUser(eq("u2"), any(Executor.class), any(BlockBackend.CallResult.class));
        repo.block("u2", out::add);
        doAnswer(inv -> {
            ((BlockBackend.CallResult) inv.getArgument(2)).onError(new RuntimeException("boom"));
            return null;
        }).when(backend).blockUser(eq("u3"), any(Executor.class), any(BlockBackend.CallResult.class));
        repo.block("u3", out::add);
        assertEquals(Arrays.asList(BlockResult.BLOCKED, BlockResult.ERROR, BlockResult.ERROR), out);
    }

    @Test
    public void blockWithoutASessionIsAnErrorAndCallsNothing() {
        uid = null;
        build();
        List<BlockResult> out = new ArrayList<>();
        repo.block("u1", out::add);
        assertEquals(Collections.singletonList(BlockResult.ERROR), out);
        verify(backend, never()).blockUser(anyString(), any(Executor.class), any(BlockBackend.CallResult.class));
    }

    @Test
    public void unblockDeletesTheDocumentOfTheSignedInAccount() {
        doAnswer(inv -> {
            ((BlockBackend.Result) inv.getArgument(3)).onSuccess();
            return null;
        }).when(backend).unblock(eq("me"), eq("u1"), any(Executor.class), any(BlockBackend.Result.class));
        doAnswer(inv -> {
            ((BlockBackend.Result) inv.getArgument(3)).onError(new RuntimeException("denied"));
            return null;
        }).when(backend).unblock(eq("me"), eq("u2"), any(Executor.class), any(BlockBackend.Result.class));
        List<Boolean> out = new ArrayList<>();
        repo.unblock("u1", out::add);
        repo.unblock("u2", out::add);
        assertEquals(Arrays.asList(true, false), out);
    }

    @Test
    public void signingOutReleasesTheListenerAndDropsLateEvents() {
        repo.observeBlocked().observeForever(observer);
        emit(doc("a", 1_000L));
        uid = null;
        auth.onAuth(null);
        verify(registration, times(1)).remove();
        assertEquals(BlockState.Status.SIGNED_OUT, last().status);
        assertTrue(last().blocked.isEmpty());

        emit(doc("late", 9L));
        assertEquals(BlockState.Status.SIGNED_OUT, last().status);

        List<Boolean> out = new ArrayList<>();
        repo.unblock("a", out::add);
        assertEquals(Collections.singletonList(false), out);
        assertFalse(out.get(0));
    }
}
