package com.example.bbettercalendar.social.presence;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.social.SocialFeature;
import com.example.bbettercalendar.social.common.MainLooperScheduler;
import com.example.bbettercalendar.social.common.Scheduler;
import com.example.bbettercalendar.social.common.SerialExecutor;
import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine.State;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Publica la presencia propia en {@code status/{uid}} de la Realtime Database (F4-u3, 03 §8, D-032, D-033).
 *
 * <p><b>Cuándo escribe.</b> Solo con {@code FEATURE_SOCIAL} ({@link SocialFeature#isAccountEnabled()}), una
 * sesión, conexión con el servidor y sin un cierre de sesión o un borrado de cuenta en marcha. Con el
 * interruptor ({@link PresenceSharing}) apagado, o mientras aún no se sabe su valor, publica {@code idle} y nunca
 * {@code focus}, {@code break} ni {@code paused}.
 * <ul>
 *   <li>En cada (re)conexión ({@code .info/connected}) <b>primero</b> registra
 *       {@code onDisconnect().setValue(idle)} y <b>después</b> escribe el estado: si la app muere o pierde la red,
 *       el servidor pone {@code idle} solo.</li>
 *   <li>Escribe solo cuando cambia lo publicado (nada por segundo), con una excepción: mientras corre
 *       {@code focus} o {@code break} reescribe lo mismo (el mismo {@code sessionStart}) cada
 *       {@link #REFRESH_MS} para refrescar {@code since} (regla de 4 h del lector). Una pausa no se refresca.</li>
 *   <li>El mapa del temporizador lo decide {@link PresenceTransitions}; los mapas, {@link PresenceWrites}.</li>
 * </ul>
 *
 * <p><b>Cerrar sesión y borrar cuenta.</b> {@link #goOfflineForSignOut} escribe {@code idle}, cancela el
 * {@code onDisconnect} y avisa con el acuse de las dos operaciones o pasados {@link #ACK_TIMEOUT_MS}: quien llama
 * cierra la sesión de Auth dentro de ese aviso. {@link #beforeDeleteAccount} solo cancela el {@code onDisconnect}
 * y no escribe nada (la Function borra {@code status/{uid}}; un {@code idle} posterior lo recrearía). Desde
 * cualquiera de las dos, nada se escribe hasta que cambie la sesión (o {@link #onDeleteAccountFailed()}).
 *
 * <p><b>Hilos (regla #3).</b> Todo el estado vive en un único {@link SerialExecutor} sobre el {@code @IoExecutor};
 * los métodos públicos se pueden llamar desde cualquier hilo. Firebase solo a través de {@link PresenceBackend}.
 */
@Singleton
public class PresencePublisher {

    private static final String TAG = "PresencePublisher";

    /** Cada cuánto se reescribe {@code focus} / {@code break} para refrescar {@code since} (la regla es 4 h). */
    static final long REFRESH_MS = TimeUnit.HOURS.toMillis(2);
    /** Tope de espera del acuse al cerrar sesión o antes de borrar la cuenta. */
    static final long ACK_TIMEOUT_MS = 3_000L;

    /** Dónde van los errores de escritura (el log; un test los recoge). */
    interface ErrorLog {
        void log(@NonNull String message, @NonNull Throwable error);
    }

    private final PresenceBackend backend;
    private final PresenceSharing sharing;
    private final Executor serial;
    private final Scheduler scheduler;
    private final LongSupplier wallClock;
    private final BooleanSupplier enabled;
    private final ErrorLog errorLog;

    private final AtomicBoolean started = new AtomicBoolean(false);

    // ---- solo en el serial ----
    /** Lo que diría el temporizador (independiente de la sesión y del interruptor). */
    @NonNull
    private OwnPresence run = OwnPresence.IDLE;
    @Nullable
    private String uid;
    private boolean connected;
    private long serverOffsetMs;
    /** Interruptor para {@link #uid}; null mientras no se ha leído (cuenta como apagado). */
    @Nullable
    private Boolean sharingOn;
    /** {@code onDisconnect(idle)} registrado para {@link #uid} en esta conexión. */
    private boolean onDisconnectArmed;
    /** Lo último escrito para {@link #uid} en esta conexión; null = nada (la próxima vez se escribe). */
    @Nullable
    private OwnPresence written;
    /** Cierre de sesión o borrado de cuenta en marcha: no se escribe nada hasta que cambie la sesión. */
    private boolean suspended;
    @Nullable
    private Scheduler.Cancellable refresh;
    private int refreshGeneration;

    @Inject
    public PresencePublisher(RtdbPresenceBackend backend, PresenceSharing sharing,
                             @IoExecutor ExecutorService ioExecutor) {
        this(backend, sharing, new SerialExecutor(ioExecutor), new MainLooperScheduler(),
                System::currentTimeMillis, SocialFeature::isAccountEnabled,
                (message, error) -> Log.w(TAG, message, error));
    }

    /** Para tests: sin Firebase ni Android (ejecutor directo, programador y reloj falsos). */
    PresencePublisher(@NonNull PresenceBackend backend, @NonNull PresenceSharing sharing, @NonNull Executor serial,
                      @NonNull Scheduler scheduler, @NonNull LongSupplier wallClock,
                      @NonNull BooleanSupplier enabled, @NonNull ErrorLog errorLog) {
        this.backend = backend;
        this.sharing = sharing;
        this.serial = serial;
        this.scheduler = scheduler;
        this.wallClock = wallClock;
        this.enabled = enabled;
        this.errorLog = errorLog;
    }

    // ---- API ---------------------------------------------------------------------------------------------

    /**
     * Transición del temporizador ({@code FocusSessionViewModel.onTransition}).
     *
     * @param countdownElapsedMs lo que lleva corrido la cuenta atrás actual ({@code countdown - remaining});
     *                           fija el inicio de una racha restaurada tras la muerte del proceso
     */
    public void onTimerState(@NonNull State state, long countdownElapsedMs) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        long now = wallClock.getAsLong();
        ensureStarted();
        serial.execute(() -> {
            run = PresenceTransitions.next(run, state, countdownElapsedMs, now);
            publish();
        });
    }

    /** El ViewModel del temporizador se destruye ({@code onCleared}): {@code idle}. */
    public void onTimerCleared() {
        if (!enabled.getAsBoolean()) {
            return;
        }
        ensureStarted();
        serial.execute(() -> {
            run = OwnPresence.IDLE;
            publish();
        });
    }

    /**
     * Antes de cerrar sesión (Mi perfil): escribe {@code idle}, cancela el {@code onDisconnect} y llama a
     * {@code done} (una sola vez, en un hilo de fondo) cuando el servidor confirma las dos operaciones o pasados
     * {@link #ACK_TIMEOUT_MS}. Quien llama cierra la sesión de Auth dentro de {@code done}.
     */
    public void goOfflineForSignOut(@NonNull Runnable done) {
        if (!enabled.getAsBoolean()) {
            done.run();
            return;
        }
        ensureStarted();
        serial.execute(() -> {
            String u = uid;
            suspended = true;
            cancelRefresh();
            if (u == null) {
                done.run();
                return;
            }
            Runnable finish = once(done);
            PresenceBackend.Callback ack = countDown(2, finish, "sign-out " + PresenceWrites.STATUS + "/" + u);
            written = OwnPresence.IDLE;
            onDisconnectArmed = false;
            backend.setStatus(u, PresenceWrites.idle(backend.serverTimestamp()), serial, ack);
            backend.cancelOnDisconnect(u, serial, ack);
        });
    }

    /**
     * Antes de llamar a la Function {@code deleteAccount}: cancela el {@code onDisconnect} y llama a {@code done}
     * con el acuse o pasados {@link #ACK_TIMEOUT_MS}. No escribe {@code idle} ni ahora ni después: la Function
     * borra {@code status/{uid}} y cualquier escritura posterior lo recrearía.
     */
    public void beforeDeleteAccount(@NonNull Runnable done) {
        if (!enabled.getAsBoolean()) {
            done.run();
            return;
        }
        ensureStarted();
        serial.execute(() -> {
            String u = uid;
            suspended = true;
            cancelRefresh();
            if (u == null) {
                done.run();
                return;
            }
            onDisconnectArmed = false;
            backend.cancelOnDisconnect(u, serial,
                    countDown(1, once(done), "cancel onDisconnect " + PresenceWrites.STATUS + "/" + u));
        });
    }

    /** La Function {@code deleteAccount} falló y la sesión sigue: vuelve a publicar como antes. */
    public void onDeleteAccountFailed() {
        if (!enabled.getAsBoolean()) {
            return;
        }
        serial.execute(() -> {
            if (!suspended) {
                return;
            }
            suspended = false;
            written = null;
            onDisconnectArmed = false;
            publish();
        });
    }

    // ---- en el serial ------------------------------------------------------------------------------------

    private void ensureStarted() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        sharing.setListener((u, on) -> serial.execute(() -> onSharingKnown(u, on)));
        backend.observe(serial, new PresenceBackend.Listener() {
            @Override
            public void onUidChanged(@Nullable String newUid) {
                PresencePublisher.this.onUidChanged(newUid);
            }

            @Override
            public void onConnectedChanged(boolean isConnected) {
                PresencePublisher.this.onConnectedChanged(isConnected);
            }

            @Override
            public void onServerTimeOffset(long offsetMs) {
                serverOffsetMs = offsetMs;
            }
        });
    }

    private void onUidChanged(@Nullable String newUid) {
        if (newUid == null ? uid == null : newUid.equals(uid)) {
            return;
        }
        // Sesión nueva (o ninguna): nada de lo anterior vale para ella.
        uid = newUid;
        sharingOn = null;
        onDisconnectArmed = false;
        written = null;
        suspended = false;
        cancelRefresh();
        if (newUid != null) {
            sharing.readSharing(newUid, on -> serial.execute(() -> onSharingKnown(newUid, on)));
        }
        publish(); // hasta saber el interruptor, idle
    }

    private void onConnectedChanged(boolean isConnected) {
        connected = isConnected;
        if (!isConnected) {
            // El servidor aplica (o aplicará) el onDisconnect: al volver hay que registrarlo y escribir otra vez.
            onDisconnectArmed = false;
            written = null;
            cancelRefresh();
            return;
        }
        publish();
    }

    private void onSharingKnown(@NonNull String forUid, boolean on) {
        if (!forUid.equals(uid)) {
            return; // respuesta de otra sesión
        }
        sharingOn = on;
        publish();
    }

    private void publish() {
        String u = uid;
        if (!enabled.getAsBoolean() || u == null || !connected || suspended) {
            return;
        }
        if (!onDisconnectArmed) {
            onDisconnectArmed = true;
            backend.registerOnDisconnect(u, PresenceWrites.idle(backend.serverTimestamp()), serial,
                    new PresenceBackend.Callback() {
                        @Override
                        public void onSuccess() { }

                        @Override
                        public void onError(@NonNull Throwable error) {
                            errorLog.log("onDisconnect " + PresenceWrites.STATUS + "/" + u + " failed", error);
                            if (u.equals(uid)) {
                                onDisconnectArmed = false; // se reintenta en la próxima publicación
                            }
                        }
                    });
        }
        OwnPresence target = Boolean.TRUE.equals(sharingOn) ? run : OwnPresence.IDLE;
        if (target.equals(written)) {
            return;
        }
        write(u, target);
    }

    private void write(@NonNull String u, @NonNull OwnPresence target) {
        written = target;
        backend.setStatus(u, PresenceWrites.toMap(target, serverOffsetMs, backend.serverTimestamp()), serial,
                new PresenceBackend.Callback() {
                    @Override
                    public void onSuccess() { }

                    @Override
                    public void onError(@NonNull Throwable error) {
                        errorLog.log(PresenceWrites.STATUS + "/" + u + " write failed", error);
                        if (u.equals(uid) && target.equals(written)) {
                            written = null; // la próxima publicación lo vuelve a intentar
                        }
                    }
                });
        scheduleRefresh(target);
    }

    private void scheduleRefresh(@NonNull OwnPresence target) {
        cancelRefresh();
        if (!target.isRunning()) {
            return;
        }
        int generation = refreshGeneration;
        refresh = scheduler.schedule(REFRESH_MS, () -> serial.execute(() -> onRefreshDue(generation)));
    }

    private void cancelRefresh() {
        refreshGeneration++;
        if (refresh != null) {
            refresh.cancel();
            refresh = null;
        }
    }

    private void onRefreshDue(int generation) {
        if (generation != refreshGeneration) {
            return; // otra escritura lo reprogramó
        }
        refresh = null;
        if (written != null && written.isRunning()) {
            written = null; // fuerza la reescritura del mismo estado: since se refresca
            publish();
        }
    }

    // ---- avisos de una sola vez --------------------------------------------------------------------------

    /** {@code done} como mucho una vez: con el acuse o con el tope de {@link #ACK_TIMEOUT_MS}. */
    @NonNull
    private Runnable once(@NonNull Runnable done) {
        AtomicBoolean fired = new AtomicBoolean(false);
        Scheduler.Cancellable[] timeout = new Scheduler.Cancellable[1];
        Runnable finish = () -> {
            if (fired.compareAndSet(false, true)) {
                Scheduler.Cancellable t = timeout[0];
                if (t != null) {
                    t.cancel();
                }
                done.run();
            }
        };
        timeout[0] = scheduler.schedule(ACK_TIMEOUT_MS, () -> serial.execute(finish));
        return finish;
    }

    /** Callback que llama a {@code finish} cuando han respondido {@code count} operaciones (bien o mal). */
    @NonNull
    private PresenceBackend.Callback countDown(int count, @NonNull Runnable finish, @NonNull String what) {
        AtomicInteger left = new AtomicInteger(count);
        return new PresenceBackend.Callback() {
            @Override
            public void onSuccess() {
                if (left.decrementAndGet() == 0) {
                    finish.run();
                }
            }

            @Override
            public void onError(@NonNull Throwable error) {
                errorLog.log(what + " failed", error);
                onSuccess();
            }
        };
    }
}
