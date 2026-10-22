package com.example.bbettercalendar.social.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.invocation.Invocation;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;

/**
 * F2-u1: {@link WatchedListener} con la fuente y el destino simulados con Mockito (interfaces propias,
 * sin clases de Firebase), un {@link SerialExecutor} sobre una cola manual (para decidir cuándo corre
 * cada evento) y el reloj falso de {@link FakeScheduler}.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class WatchedListenerTest {

    /** Ejecutor de debajo del serial: guarda las tareas hasta {@link #drain()}. */
    private static final class ManualExecutor implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable r) {
            tasks.add(r);
        }

        void drain() {
            Runnable r;
            while ((r = tasks.poll()) != null) {
                r.run();
            }
        }
    }

    private final ManualExecutor io = new ManualExecutor();
    private final SerialExecutor serial = new SerialExecutor(io);
    private final FakeScheduler clock = new FakeScheduler();
    private ListenerSource<String, String> source;
    private ListenerSource.Registration registration;
    private WatchedListener.Sink<String> sink;
    private WatchedListener<String, String> watcher;

    @Before
    public void setUp() {
        source = mock(ListenerSource.class);
        registration = mock(ListenerSource.Registration.class);
        sink = mock(WatchedListener.Sink.class);
        when(source.listen(anyString(), any(Executor.class), any(ListenerSource.Events.class)))
                .thenReturn(registration);
        watcher = new WatchedListener<>(serial, clock, source, sink);
    }

    /** Los callbacks de cada {@code listen} en orden: el n-ésimo enganche. */
    private ListenerSource.Events<String> events(int n) {
        ArgumentCaptor<ListenerSource.Events> captor = ArgumentCaptor.forClass(ListenerSource.Events.class);
        verify(source, atLeast(n + 1)).listen(anyString(), any(Executor.class), captor.capture());
        return captor.getAllValues().get(n);
    }

    private int listenCount() {
        int n = 0;
        for (Invocation i : mockingDetails(source).getInvocations()) {
            if (i.getMethod().getName().equals("listen")) {
                n++;
            }
        }
        return n;
    }

    /** Como Firestore: el evento se entrega por el ejecutor que recibió {@code listen} (el serial). */
    private void fire(ListenerSource.Events<String> e, String value, ListenerException error) {
        serial.execute(() -> e.onEvent(value, false, error));
    }

    /** Un valor de la caché local (fromCache): lo primero que entrega Firestore al reenganchar. */
    private void fireCached(ListenerSource.Events<String> e, String value) {
        serial.execute(() -> e.onEvent(value, true, null));
    }

    private static ListenerException err(String code) {
        return new ListenerException(code, null);
    }

    // ---- generación ----------------------------------------------------------------------------------

    @Test
    public void staleGenerationEventsAndStatesAreDropped() {
        watcher.attach("alice");
        io.drain();
        ListenerSource.Events<String> alice = events(0);
        // Un evento de alice ya encolado cuando se cierra la sesión: no puede publicar READY tras SIGNED_OUT.
        fire(alice, "alice-1", null);
        watcher.detach();
        io.drain();
        verify(sink, never()).onValue(any());
        InOrder order = inOrder(sink);
        order.verify(sink).onLoading();
        order.verify(sink).onDetached();
        verify(registration).remove();

        // Un evento tardío del listener viejo, también fuera.
        fire(alice, "alice-2", null);
        io.drain();
        verify(sink, never()).onValue(any());

        // Dos sesiones seguidas sin que corra nada: el LOADING de la primera es viejo y se descarta.
        watcher.attach("x");
        watcher.attach("bob");
        ListenerSource.Events<String> x = events(1);
        ListenerSource.Events<String> bob = events(2);
        fire(x, "x-1", null);
        fire(alice, "alice-3", null);
        fire(bob, "bob-1", null);
        io.drain();
        verify(sink, times(2)).onLoading(); // el de alice y el de bob; el de x, no
        verify(sink).onValue("bob-1");
        verify(sink, never()).onValue("x-1");
        verify(sink, never()).onValue("alice-3");

        // Un error viejo tampoco publica ERROR ni programa reintentos.
        fire(x, null, err("UNAVAILABLE"));
        io.drain();
        verify(sink, never()).onError(any());
        assertTrue(clock.pending().isEmpty());
    }

    @Test
    public void sameKeyWhileActiveIsANoOp() {
        watcher.attach("alice");
        watcher.attach("alice");
        watcher.onForeground(); // no hay error: no hace nada
        io.drain();
        verify(source, times(1)).listen(eq("alice"), any(Executor.class), any(ListenerSource.Events.class));
        verify(sink, times(1)).onLoading();
    }

    // ---- error reintentable --------------------------------------------------------------------------

    @Test
    public void retryableErrorPublishesErrorAndReattachesWithBackoff() {
        watcher.attach("alice");
        io.drain();
        fire(events(0), null, err("UNAVAILABLE"));
        io.drain();
        verify(sink).onError(any(ListenerException.class));
        assertEquals(1, clock.pending().size());
        assertEquals(1_000L, clock.pending().get(0).delay);

        clock.advance(999);
        io.drain();
        assertEquals(1, listenCount());
        clock.advance(1);
        io.drain();
        assertEquals(2, listenCount());

        // Segundo error seguido: 2 s.
        fire(events(1), null, err("INTERNAL"));
        io.drain();
        assertEquals(2_000L, clock.pending().get(0).delay);
        clock.advance(2_000);
        io.drain();
        assertEquals(3, listenCount());

        // Un evento bueno reinicia la espera; el listener del primer intento sigue siendo viejo.
        fire(events(0), "stale", null);
        fire(events(2), "ok", null);
        io.drain();
        verify(sink).onValue("ok");
        verify(sink, never()).onValue("stale");
        fire(events(2), null, err("UNKNOWN"));
        io.drain();
        assertEquals(1_000L, clock.pending().get(0).delay);

        // Seis intentos como mucho (1, 2, 4, 8, 16, 32 s); después espera a primer plano.
        clock.advance(1_000);
        io.drain();
        for (int i = 1; i < Backoff.MAX_ATTEMPTS; i++) {
            fire(events(listenCount() - 1), null, err("RESOURCE_EXHAUSTED"));
            io.drain();
            assertEquals(Backoff.delayFor(i), clock.pending().get(0).delay);
            clock.advance(Backoff.delayFor(i));
            io.drain();
        }
        int afterRetries = listenCount();
        assertEquals(3 + Backoff.MAX_ATTEMPTS, afterRetries);
        fire(events(afterRetries - 1), null, err("UNAVAILABLE"));
        io.drain();
        assertTrue(clock.pending().isEmpty());
        clock.advance(10 * 60_000L);
        io.drain();
        assertEquals(afterRetries, listenCount());
        watcher.onForeground();
        io.drain();
        assertEquals(afterRetries + 1, listenCount());
        // Los reintentos no vuelven a publicar LOADING (se queda el ERROR hasta el primer evento bueno).
        verify(sink, times(1)).onLoading();
    }

    /**
     * Regresión (verify u1, Medium): al reenganchar, Firestore entrega primero el documento de la caché y
     * luego el error del servidor. El valor de la caché se publica, pero no reinicia la espera: 1, 2, 4 s.
     */
    @Test
    public void cachedValueBeforeRetryableErrorDoesNotResetBackoff() {
        watcher.attach("alice");
        io.drain();
        fireCached(events(0), "alice-cache");
        fire(events(0), null, err("UNAVAILABLE"));
        io.drain();
        assertEquals(1_000L, clock.pending().get(0).delay);
        for (int i = 1; i <= 2; i++) {
            clock.advance(Backoff.delayFor(i - 1));
            io.drain();
            ListenerSource.Events<String> e = events(listenCount() - 1);
            fireCached(e, "alice-cache");
            fire(e, null, err("UNAVAILABLE"));
            io.drain();
            assertEquals(Backoff.delayFor(i), clock.pending().get(0).delay);
        }
        // El valor de la caché sí se publica cada vez (se ve el perfil sin conexión).
        verify(sink, times(3)).onValue("alice-cache");
    }

    /**
     * Misma familia: con un valor de la caché antes de cada error, el tope de 6 intentos se alcanza y se
     * espera a primer plano; y un valor del servidor (no de la caché) sí reinicia la espera.
     */
    @Test
    public void cachedValuesStillReachTheAttemptCapAndServerValueResets() {
        watcher.attach("alice");
        io.drain();
        for (int i = 0; i < Backoff.MAX_ATTEMPTS; i++) {
            ListenerSource.Events<String> e = events(listenCount() - 1);
            fireCached(e, "alice-cache");
            fire(e, null, err("RESOURCE_EXHAUSTED"));
            io.drain();
            assertEquals(Backoff.delayFor(i), clock.pending().get(0).delay);
            clock.advance(Backoff.delayFor(i));
            io.drain();
        }
        int afterRetries = listenCount();
        assertEquals(1 + Backoff.MAX_ATTEMPTS, afterRetries);
        ListenerSource.Events<String> last = events(afterRetries - 1);
        fireCached(last, "alice-cache");
        fire(last, null, err("UNAVAILABLE"));
        io.drain();
        assertTrue(clock.pending().isEmpty());
        clock.advance(10 * 60_000L);
        io.drain();
        assertEquals(afterRetries, listenCount());

        // Primer plano: reengancha con la espera a cero; la caché no la reinicia (1 s, 2 s), el servidor sí.
        watcher.onForeground();
        io.drain();
        for (int i = 0; i < 2; i++) {
            ListenerSource.Events<String> e = events(listenCount() - 1);
            fireCached(e, "alice-cache");
            fire(e, null, err("UNAVAILABLE"));
            io.drain();
            assertEquals(Backoff.delayFor(i), clock.pending().get(0).delay);
            clock.advance(Backoff.delayFor(i));
            io.drain();
        }
        ListenerSource.Events<String> fresh = events(listenCount() - 1);
        fire(fresh, "alice-server", null);
        fire(fresh, null, err("UNAVAILABLE"));
        io.drain();
        verify(sink).onValue("alice-server");
        assertEquals(1_000L, clock.pending().get(0).delay);
    }

    @Test
    public void detachCancelsAPendingRetry() {
        watcher.attach("alice");
        fire(events(0), null, err("UNAVAILABLE"));
        io.drain();
        assertEquals(1, clock.pending().size());
        watcher.detach();
        clock.advance(60_000);
        io.drain();
        assertEquals(1, listenCount());
        verify(sink).onDetached();
    }

    // ---- error no reintentable -----------------------------------------------------------------------

    @Test
    public void nonRetryableErrorWaitsForForegroundOrSessionChange() {
        watcher.attach("alice");
        fire(events(0), null, err("PERMISSION_DENIED"));
        io.drain();
        verify(sink).onError(any(ListenerException.class));
        assertTrue(clock.pending().isEmpty());
        clock.advance(10 * 60_000L);
        io.drain();
        assertEquals(1, listenCount());

        // ProcessLifecycleOwner ON_START: vuelve a enganchar la misma clave.
        watcher.onForeground();
        io.drain();
        verify(source, times(2)).listen(eq("alice"), any(Executor.class), any(ListenerSource.Events.class));
        fire(events(1), "alice-ok", null);
        io.drain();
        verify(sink).onValue("alice-ok");

        // FAILED_PRECONDITION: espera; un cambio de sesión (otra clave) engancha de nuevo.
        fire(events(1), null, err("FAILED_PRECONDITION"));
        io.drain();
        assertTrue(clock.pending().isEmpty());
        watcher.attach("bob");
        io.drain();
        verify(source).listen(eq("bob"), any(Executor.class), any(ListenerSource.Events.class));
        verify(sink, times(2)).onLoading();
        verify(sink, times(2)).onError(any(ListenerException.class));
    }
}
