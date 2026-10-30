package com.example.bbettercalendar.social.presence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.bbettercalendar.social.account.SocialPrefs;
import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.State;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * F4-u3: {@link PresencePublisher} con la Realtime Database simulada por Mockito a través de {@link PresenceBackend},
 * {@link SocialPrefs} simulado (interruptor y año de nacimiento), ejecutor directo y reloj/programador falsos.
 * Las aserciones miran lo que vería un amigo en {@code status/{uid}} (el último mapa escrito por uid), salvo el
 * orden {@code onDisconnect} → escritura y el de cerrar sesión, que son contrato.
 */
@SuppressWarnings("unchecked")
public class PresencePublisherTest {

    private static final Object TS = "SERVER_TIMESTAMP";
    private static final long MIN = 60_000L;
    private static final long HOUR = 60 * MIN;
    /** 2026-10-30 10:00:00 UTC. */
    private static final long T0 = 1_793_354_400_000L;
    private static final int ADULT_YEAR = 1990;
    private static final int MINOR_YEAR = 2010;

    /** Una escritura de {@code status/{uid}} tal como la vería el servidor. */
    private static final class Write {
        final String uid;
        final Map<String, Object> value;

        Write(String uid, Map<String, Object> value) {
            this.uid = uid;
            this.value = value;
        }
    }

    private PresenceBackend backend;
    private SocialPrefs prefs;
    private FakeClockScheduler clock;
    private PresenceBackend.Listener server;
    private PresencePublisher publisher;
    /** El interruptor del publicador (la API de Mi perfil, u8). */
    private PresenceSharing sharing;
    private boolean enabled = true;
    private boolean autoAck = true;

    private final List<Write> writes = new ArrayList<>();
    private final List<PresenceBackend.Callback> pendingAcks = new ArrayList<>();
    private final Map<String, Boolean> storedSharing = new HashMap<>();
    private Integer birthYear = ADULT_YEAR;
    /** Lectura del año retenida (para el caso «aún no leído»); null = se contesta al momento. */
    private List<Consumer<Integer>> heldBirthYearReads;

    // Modelo del socket del SDK de RTDB: sin ninguna operación pendiente lo corta por inactividad a los 60 s
    // (PersistentConnectionImpl.doIdleCheck) y solo lo reanuda otra operación; los .info/* son locales y no cuentan.
    /** Operaciones de servidor pedidas al backend (escribir, registrar o cancelar el onDisconnect). */
    private int ops;
    /** El SDK cortó el socket por inactividad: {@code .info/connected} no vuelve a true hasta otra operación. */
    private boolean idleDropped;

    @Before
    public void setUp() {
        backend = mock(PresenceBackend.class);
        prefs = mock(SocialPrefs.class);
        clock = new FakeClockScheduler(T0);

        when(backend.serverTimestamp()).thenReturn(TS);
        doAnswer(inv -> {
            server = inv.getArgument(1);
            return null;
        }).when(backend).observe(any(Executor.class), any(PresenceBackend.Listener.class));
        doAnswer(inv -> {
            writes.add(new Write(inv.getArgument(0), new HashMap<>((Map<String, Object>) inv.getArgument(1))));
            ack(inv.getArgument(3));
            return null;
        }).when(backend).setStatus(anyString(), anyMap(), any(Executor.class), any(PresenceBackend.Callback.class));
        doAnswer(inv -> {
            ack(inv.getArgument(3));
            return null;
        }).when(backend).registerOnDisconnect(anyString(), anyMap(), any(Executor.class),
                any(PresenceBackend.Callback.class));
        doAnswer(inv -> {
            ack(inv.getArgument(2));
            return null;
        }).when(backend).cancelOnDisconnect(anyString(), any(Executor.class), any(PresenceBackend.Callback.class));

        doAnswer(inv -> {
            Consumer<Boolean> cb = inv.getArgument(1);
            cb.accept(storedSharing.get((String) inv.getArgument(0)));
            return null;
        }).when(prefs).readPresenceSharing(anyString(), any(Consumer.class));
        doAnswer(inv -> {
            Consumer<Integer> cb = inv.getArgument(0);
            if (heldBirthYearReads != null) {
                heldBirthYearReads.add(cb);
            } else {
                cb.accept(birthYear);
            }
            return null;
        }).when(prefs).readBirthYear(any(Consumer.class));

        sharing = new PresenceSharing(prefs, () -> 2026);
        publisher = new PresencePublisher(backend, sharing, Runnable::run, clock, clock::now, () -> enabled,
                (message, error) -> { });
    }

    private void ack(PresenceBackend.Callback cb) {
        ops++;
        if (idleDropped) {
            idleDropped = false; // la operación reanuda el socket: conecta en cuanto el ejecutor queda libre
            clock.schedule(0L, () -> server.onConnectedChanged(true));
        }
        if (autoAck) {
            cb.onSuccess();
        } else {
            pendingAcks.add(cb);
        }
    }

    // ---- ayudas ------------------------------------------------------------------------------------------

    /** La app arranca (el VM restaura STOPPED), con sesión de {@code uid} y conexión. */
    private void signedInAndConnected(String uid) {
        publisher.onTimerState(State.STOPPED, 0L);
        server.onUidChanged(uid);
        server.onConnectedChanged(true);
    }

    private List<Write> writesFor(String uid) {
        List<Write> out = new ArrayList<>();
        for (Write w : writes) {
            if (w.uid.equals(uid)) {
                out.add(w);
            }
        }
        return out;
    }

    /** Lo que ve un amigo ahora en {@code status/{uid}}. */
    private Map<String, Object> seen(String uid) {
        List<Write> list = writesFor(uid);
        assertFalse("nothing written for " + uid, list.isEmpty());
        return list.get(list.size() - 1).value;
    }

    private static Map<String, Object> idle() {
        Map<String, Object> m = new HashMap<>();
        m.put("state", "idle");
        m.put("since", TS);
        return m;
    }

    private static Map<String, Object> running(String state, long sessionStart) {
        Map<String, Object> m = new HashMap<>();
        m.put("state", state);
        m.put("since", TS);
        m.put("sessionStart", sessionStart);
        return m;
    }

    private static Map<String, Object> paused(long sessionStart, long pausedAt) {
        Map<String, Object> m = running("paused", sessionStart);
        m.put("pausedAt", pausedAt);
        return m;
    }

    // ---- flag, sesión e interruptor ---------------------------------------------------------------------

    @Test
    public void withTheFlagOffNothingTouchesFirebase() {
        enabled = false;
        AtomicInteger signedOut = new AtomicInteger();
        publisher.onTimerState(State.RUNNING, 0L);
        publisher.onTimerCleared();
        publisher.goOfflineForSignOut(signedOut::incrementAndGet);
        verifyNoInteractions(backend);
        assertEquals("sign-out still proceeds", 1, signedOut.get());
    }

    @Test
    public void signedOutNothingIsWritten() {
        publisher.onTimerState(State.STOPPED, 0L);
        server.onUidChanged(null);
        server.onConnectedChanged(true);
        publisher.onTimerState(State.RUNNING, 0L);
        verify(backend, never()).setStatus(anyString(), anyMap(), any(), any());
        verify(backend, never()).registerOnDisconnect(anyString(), anyMap(), any(), any());
    }

    @Test
    public void withTheSwitchOffOnlyIdleIsEverWritten() {
        storedSharing.put("alice", false);
        signedInAndConnected("alice");
        publisher.onTimerState(State.RUNNING, 0L);
        clock.advance(25 * MIN);
        publisher.onTimerState(State.RUNNING_REST, 0L);
        publisher.onTimerState(State.PAUSED_REST, 0L);
        List<Write> list = writesFor("alice");
        assertFalse(list.isEmpty());
        for (Write w : list) {
            assertEquals(idle(), w.value);
        }
    }

    @Test
    public void adultsShareByDefault() {
        birthYear = ADULT_YEAR;
        signedInAndConnected("alice");
        publisher.onTimerState(State.RUNNING, 0L);
        assertEquals(running("focus", T0), seen("alice"));
    }

    @Test
    public void minorsAndUnknownAgesDoNotShareByDefault() {
        birthYear = MINOR_YEAR;
        signedInAndConnected("teen");
        publisher.onTimerState(State.RUNNING, 0L);
        assertEquals(idle(), seen("teen"));

        birthYear = null;
        server.onUidChanged("unknown");
        publisher.onTimerState(State.RUNNING_REST, 0L);
        assertEquals(idle(), seen("unknown"));
    }

    @Test
    public void untilTheBirthYearIsReadTheFirstWriteAfterSignInIsIdle() {
        heldBirthYearReads = new ArrayList<>();
        publisher.onTimerState(State.RUNNING, 0L); // ya estudiaba sin sesión
        server.onUidChanged("alice");
        server.onConnectedChanged(true);
        assertEquals(idle(), seen("alice"));
        assertEquals(1, writesFor("alice").size());

        heldBirthYearReads.get(0).accept(ADULT_YEAR); // llega el año: 18+
        assertEquals(running("focus", T0), seen("alice"));
    }

    @Test
    public void switchingOffWhileFocusingWritesIdleAtOnceAndOnRepublishesTheSameRun() {
        signedInAndConnected("alice");
        publisher.onTimerState(State.RUNNING, 0L);
        clock.advance(10 * MIN);
        sharing.setSharing("alice", false); // Mi perfil (u8)
        assertEquals(idle(), seen("alice"));
        assertFalse(sharing.isSharing("alice"));

        clock.advance(5 * MIN);
        sharing.setSharing("alice", true);
        assertEquals("the same run comes back", running("focus", T0), seen("alice"));
        verify(prefs).savePresenceSharing("alice", false);
        verify(prefs).savePresenceSharing("alice", true);
    }

    // ---- conexión y onDisconnect ------------------------------------------------------------------------

    @Test
    public void onConnectOnDisconnectIdleIsRegisteredBeforeTheStateWrite() {
        publisher.onTimerState(State.RUNNING, 0L);
        server.onUidChanged("alice");
        server.onConnectedChanged(true);
        InOrder order = inOrder(backend);
        order.verify(backend).registerOnDisconnect(eq("alice"), eq(idle()), any(), any());
        order.verify(backend).setStatus(eq("alice"), eq(running("focus", T0)), any(), any());
    }

    @Test
    public void aReconnectionRegistersOnDisconnectAgainAndRewritesTheState() {
        signedInAndConnected("alice");
        publisher.onTimerState(State.RUNNING, 0L);
        int before = writesFor("alice").size();

        server.onConnectedChanged(false);
        publisher.onTimerState(State.RUNNING, 0L); // sin conexión no se escribe
        assertEquals(before, writesFor("alice").size());

        server.onConnectedChanged(true);
        verify(backend, times(2)).registerOnDisconnect(eq("alice"), eq(idle()), any(), any());
        assertEquals(before + 1, writesFor("alice").size());
        assertEquals(running("focus", T0), seen("alice"));
    }

    @Test
    public void aSignInAfterTheSdkDroppedTheIdleSocketWakesItAndPublishes() {
        // Arranca sin sesión: nada mantiene ocupado el socket y el SDK lo corta a los 60 s.
        publisher.onTimerState(State.RUNNING, 0L);
        server.onUidChanged(null);
        server.onConnectedChanged(false);
        clock.advance(MIN + 1);
        idleDropped = true;

        server.onUidChanged("alice"); // inicia sesión más tarde, con el timer en marcha
        clock.advance(0L);

        InOrder order = inOrder(backend);
        order.verify(backend).registerOnDisconnect(eq("alice"), eq(idle()), any(), any());
        order.verify(backend).setStatus(eq("alice"), eq(running("focus", T0)), any(), any());
        assertEquals(running("focus", T0), seen("alice"));
    }

    @Test
    public void aLongNetworkLossWhileSignedInKeepsTheSocketAliveAndRepublishesOnReturn() {
        signedInAndConnected("alice");
        publisher.onTimerState(State.RUNNING, 0L);
        int before = writesFor("alice").size();

        // Se va la red: el servidor aplica el onDisconnect y el SDK olvida los suyos. Si en 60 s no se pide
        // ninguna operación, corta el socket por inactividad y al volver la red ya no reconecta. Cuenta también
        // la que se pide en el mismo aviso de la pérdida: el SDK ya ha olvidado los suyos cuando llega
        // .info/connected = false, así que esa operación es posterior a la pérdida.
        int opsBeforeLoss = ops;
        server.onConnectedChanged(false);
        clock.advance(MIN + 1);
        if (ops == opsBeforeLoss) {
            idleDropped = true;
        } else {
            server.onConnectedChanged(true); // vuelve la red y el SDK reconecta
        }
        clock.advance(0L);

        assertEquals(before + 1, writesFor("alice").size());
        assertEquals(running("focus", T0), seen("alice"));
    }

    @Test
    public void theSamePresenceTwiceIsWrittenOnce() {
        signedInAndConnected("alice");
        publisher.onTimerState(State.RUNNING, 0L);
        clock.advance(MIN);
        publisher.onTimerState(State.RUNNING, MIN); // p. ej. armar el modo bloqueo: misma racha
        int focusWrites = 0;
        for (Write w : writesFor("alice")) {
            if ("focus".equals(w.value.get("state"))) {
                focusWrites++;
            }
        }
        assertEquals(1, focusWrites);
    }

    @Test
    public void focusTurnsIdleOnStopped() {
        signedInAndConnected("alice");
        publisher.onTimerState(State.RUNNING, 0L);
        clock.advance(3 * MIN);
        publisher.onTimerState(State.STOPPED, 0L);
        assertEquals(idle(), seen("alice"));
    }

    @Test
    public void clearingTheTimerViewModelWritesIdle() {
        signedInAndConnected("alice");
        publisher.onTimerState(State.RUNNING, 0L);
        publisher.onTimerCleared();
        assertEquals(idle(), seen("alice"));
    }

    @Test
    public void breakPauseAndResumeAreWhatFriendsSeeWithServerTime() {
        signedInAndConnected("alice");
        server.onServerTimeOffset(2_000L); // el servidor va 2 s por delante
        publisher.onTimerState(State.RUNNING, 0L);
        assertEquals(running("focus", T0 + 2_000L), seen("alice"));

        clock.advance(25 * MIN);
        publisher.onTimerState(State.RUNNING_REST, 0L);
        assertEquals(running("break", T0 + 2_000L), seen("alice"));

        clock.advance(2 * MIN);
        publisher.onTimerState(State.PAUSED_REST, 0L);
        assertEquals(paused(T0 + 2_000L, T0 + 27 * MIN + 2_000L), seen("alice"));

        clock.advance(10 * MIN); // 10 min en pausa: el tiempo sigue en 27 min al volver
        publisher.onTimerState(State.RUNNING_REST, 0L);
        assertEquals(running("break", T0 + 10 * MIN + 2_000L), seen("alice"));
    }

    @Test
    public void aRestoredRunningSessionStartsAtItsCountdownStart() {
        signedInAndConnected("alice");
        publisher.onTimerState(State.RUNNING, 12 * MIN); // proceso nuevo; 12 min ya corridos
        assertEquals(running("focus", T0 - 12 * MIN), seen("alice"));
    }

    // ---- refresco de 2 h -----------------------------------------------------------------------------------

    @Test
    public void aRunningStateIsRewrittenEveryTwoHoursAndNeverWhileIdle() {
        signedInAndConnected("alice");
        publisher.onTimerState(State.RUNNING, 0L);
        int afterStart = writesFor("alice").size();

        clock.advance(2 * HOUR - 1);
        assertEquals("nothing in between", afterStart, writesFor("alice").size());
        clock.advance(1);
        assertEquals(afterStart + 1, writesFor("alice").size());
        assertEquals("same run, since refreshed", running("focus", T0), seen("alice"));

        publisher.onTimerState(State.STOPPED, 0L);
        int afterStop = writesFor("alice").size();
        clock.advance(6 * HOUR);
        assertEquals("no refresh while idle", afterStop, writesFor("alice").size());
    }

    @Test
    public void aForgottenPauseIsNotRefreshed() {
        signedInAndConnected("alice");
        publisher.onTimerState(State.RUNNING, 0L);
        clock.advance(MIN);
        publisher.onTimerState(State.PAUSED, 0L);
        int afterPause = writesFor("alice").size();
        clock.advance(5 * HOUR);
        assertEquals(afterPause, writesFor("alice").size());
    }

    // ---- cerrar sesión y borrar cuenta ---------------------------------------------------------------------

    @Test
    public void signOutWritesIdleThenCancelsOnDisconnectThenSignsOut() {
        signedInAndConnected("alice");
        publisher.onTimerState(State.RUNNING, 0L);
        autoAck = false;
        clearInvocations(backend); // solo cuenta lo que hace el cierre de sesión
        Runnable authSignOut = mock(Runnable.class);

        publisher.goOfflineForSignOut(authSignOut);
        verify(authSignOut, never()).run(); // espera el acuse
        for (PresenceBackend.Callback cb : new ArrayList<>(pendingAcks)) {
            cb.onSuccess();
        }

        InOrder order = inOrder(backend, authSignOut);
        order.verify(backend).setStatus(eq("alice"), eq(idle()), any(), any());
        order.verify(backend).cancelOnDisconnect(eq("alice"), any(), any());
        order.verify(authSignOut).run();
        assertEquals(idle(), seen("alice"));
    }

    @Test
    public void signOutGoesOnAfterAShortTimeoutWithoutAnAck() {
        signedInAndConnected("alice");
        publisher.onTimerState(State.RUNNING, 0L);
        autoAck = false;
        AtomicInteger signedOut = new AtomicInteger();

        publisher.goOfflineForSignOut(signedOut::incrementAndGet);
        clock.advance(PresencePublisher.ACK_TIMEOUT_MS - 1);
        assertEquals(0, signedOut.get());
        clock.advance(1);
        assertEquals(1, signedOut.get());

        for (PresenceBackend.Callback cb : new ArrayList<>(pendingAcks)) {
            cb.onSuccess(); // acuse tardío: no cierra la sesión otra vez
        }
        assertEquals(1, signedOut.get());
    }

    @Test
    public void afterSignOutStartsNothingIsWrittenForTheOldSession() {
        signedInAndConnected("alice");
        publisher.onTimerState(State.RUNNING, 0L);
        publisher.goOfflineForSignOut(() -> { });
        int after = writesFor("alice").size();
        publisher.onTimerState(State.PAUSED, 0L); // el timer sigue vivo
        server.onUidChanged(null);
        assertEquals(after, writesFor("alice").size());
        assertEquals(idle(), seen("alice"));
    }

    @Test
    public void deleteAccountCancelsOnDisconnectFirstAndNeverWritesIdle() {
        signedInAndConnected("alice");
        publisher.onTimerState(State.RUNNING, 0L);
        int before = writesFor("alice").size();
        AtomicInteger callable = new AtomicInteger();

        publisher.beforeDeleteAccount(callable::incrementAndGet);
        verify(backend).cancelOnDisconnect(eq("alice"), any(), any());
        assertEquals("the callable runs after the cancel", 1, callable.get());

        // la Function borra status/alice; AccountRepository.deleteAccount cierra la sesión (signOut interno)
        publisher.onTimerState(State.STOPPED, 0L);
        server.onUidChanged(null);
        server.onConnectedChanged(false);
        server.onConnectedChanged(true);
        assertEquals("no status write after the callable", before, writesFor("alice").size());
        verify(backend, times(1)).registerOnDisconnect(eq("alice"), anyMap(), any(), any());
    }

    @Test
    public void aConnectionLossDuringDeleteAccountDoesNotArmOnDisconnectAgain() {
        signedInAndConnected("alice");
        publisher.onTimerState(State.RUNNING, 0L);
        int before = writesFor("alice").size();
        publisher.beforeDeleteAccount(() -> { });

        // la red cae mientras corre la Function: un onDisconnect nuevo recrearía status/alice tras borrarlo
        server.onConnectedChanged(false);
        server.onConnectedChanged(true);
        verify(backend, times(1)).registerOnDisconnect(eq("alice"), anyMap(), any(), any());
        assertEquals(before, writesFor("alice").size());
    }

    @Test
    public void aFailedDeleteAccountResumesPublishing() {
        signedInAndConnected("alice");
        publisher.onTimerState(State.RUNNING, 0L);
        publisher.beforeDeleteAccount(() -> { });
        publisher.onDeleteAccountFailed();
        verify(backend, times(2)).registerOnDisconnect(eq("alice"), eq(idle()), any(), any());
        assertEquals(running("focus", T0), seen("alice"));
    }

    @Test
    public void aNewUidAfterSignInStartsClean() {
        signedInAndConnected("alice");
        publisher.onTimerState(State.RUNNING, 0L);
        publisher.goOfflineForSignOut(() -> { });
        server.onUidChanged(null);

        storedSharing.put("bob", true);
        server.onUidChanged("bob"); // otra cuenta en el mismo móvil, mismo timer en marcha
        InOrder order = inOrder(backend);
        order.verify(backend).registerOnDisconnect(eq("bob"), eq(idle()), any(), any());
        order.verify(backend).setStatus(eq("bob"), any(), any(), any());
        assertEquals(running("focus", T0), seen("bob"));
        assertEquals(idle(), seen("alice"));
    }
}
