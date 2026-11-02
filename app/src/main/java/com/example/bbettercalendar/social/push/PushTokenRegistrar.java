package com.example.bbettercalendar.social.push;

import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.social.SocialFeature;
import com.example.bbettercalendar.social.common.MainLooperScheduler;
import com.example.bbettercalendar.social.common.Scheduler;
import com.example.bbettercalendar.social.common.SerialExecutor;
import com.example.bbettercalendar.social.profile.ProfileRepository;
import com.example.bbettercalendar.social.profile.ProfileState;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Registra el token FCM de este dispositivo en {@code users/{uid}.fcmTokens} (F5-u6, 03 §5, D-035) para que las
 * Functions puedan enviarle los push de amigos.
 *
 * <p><b>Cuándo añade.</b> Nunca sin {@code FEATURE_SOCIAL} ({@link SocialFeature#isAccountEnabled()}). Cada vez que el
 * perfil propio pasa a {@code READY} con la sesión del mismo uid ({@link ProfileRepository#observeMine()}): el primer
 * {@code READY} del proceso (arranque con sesión y perfil), al iniciar sesión y al crear el perfil. También en
 * {@link #onNewToken(String)} (FCM rota el token), que sustituye al anterior. La lista se cambia en una transacción
 * ({@link FcmTokens#with}): sin duplicados y, llena, quitando las más antiguas hasta 9 antes de añadir.
 *
 * <p><b>Cuándo quita.</b> {@link #beforeSignOut} y {@link #beforeDeleteAccount}: quita el token de la lista, borra el
 * token local y luego avisa a quien llama (una vez; a más tardar a los {@link #ACK_TIMEOUT_MS}), que cierra la
 * sesión de Auth o llama a la Function. Desde entonces no se añade nada hasta que cambie la sesión o
 * {@link #onDeleteAccountFailed()}.
 *
 * <p><b>Hilos (regla #3).</b> Todo el estado vive en un único {@link SerialExecutor} sobre el {@code @IoExecutor};
 * los métodos públicos se pueden llamar desde cualquier hilo. Firebase solo a través de {@link PushTokenBackend}.
 */
@Singleton
public class PushTokenRegistrar {

    private static final String TAG = "PushTokenRegistrar";

    /** Tope de espera para quitar el token antes de cerrar sesión o borrar la cuenta. */
    static final long ACK_TIMEOUT_MS = 4_000L;

    /** Dónde van los errores (el log; un test los recoge). */
    interface ErrorLog {
        void log(@NonNull String message, @NonNull Throwable error);
    }

    private final LiveData<ProfileState> profileStates;
    private final PushTokenBackend backend;
    private final Executor serial;
    private final Scheduler scheduler;
    private final BooleanSupplier enabled;
    private final ErrorLog errorLog;

    private final AtomicBoolean started = new AtomicBoolean(false);

    // ---- solo en el serial ----
    /** uid del perfil mientras está en {@code READY}; null en otro estado. */
    @Nullable
    private String readyUid;
    /** Token que esta instancia sabe registrado para {@link #readyUid}. */
    @Nullable
    private String registeredToken;
    /** Cierre de sesión o borrado de cuenta en marcha: no se añade nada hasta que cambie la sesión. */
    private boolean suspended;

    @Inject
    public PushTokenRegistrar(ProfileRepository profiles, FirebasePushTokenBackend backend,
                              @IoExecutor ExecutorService ioExecutor) {
        this(profiles.observeMine(), backend, new SerialExecutor(ioExecutor), new MainLooperScheduler(),
                SocialFeature::isAccountEnabled, (message, error) -> Log.w(TAG, message, error));
    }

    /** Para tests: sin Firebase ni Android (perfil por {@link LiveData}, ejecutor directo, programador falso). */
    PushTokenRegistrar(@NonNull LiveData<ProfileState> profileStates, @NonNull PushTokenBackend backend,
                       @NonNull Executor serial, @NonNull Scheduler scheduler, @NonNull BooleanSupplier enabled,
                       @NonNull ErrorLog errorLog) {
        this.profileStates = profileStates;
        this.backend = backend;
        this.serial = serial;
        this.scheduler = scheduler;
        this.enabled = enabled;
        this.errorLog = errorLog;
    }

    // ---- API ---------------------------------------------------------------------------------------------

    /**
     * Empieza a seguir el perfil propio (una vez por proceso). Lo llama {@code DBMigration.onCreate} solo con
     * {@code FEATURE_SOCIAL}; sin el flag no hace nada.
     */
    @MainThread
    public void start() {
        if (!enabled.getAsBoolean() || !started.compareAndSet(false, true)) {
            return;
        }
        profileStates.observeForever(state -> serial.execute(() -> onProfileState(state)));
    }

    /** FCM dio un token nuevo ({@code FirebaseMessagingService.onNewToken}): sustituye al anterior. */
    public void onNewToken(@NonNull String token) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        serial.execute(() -> {
            String uid = readyUid;
            if (uid == null || suspended || !uid.equals(backend.currentUid())) {
                return; // sin perfil listo: el token nuevo se registra en el próximo READY
            }
            String old = registeredToken;
            backend.updateTokens(uid, list -> FcmTokens.with(FcmTokens.without(list, old), token), serial,
                    new PushTokenBackend.Callback() {
                        @Override
                        public void onSuccess() {
                            if (uid.equals(readyUid)) {
                                registeredToken = token;
                            }
                        }

                        @Override
                        public void onError(@NonNull Throwable error) {
                            errorLog.log("fcmTokens refresh failed", error);
                        }
                    });
        });
    }

    /**
     * Antes de cerrar la sesión de Auth (Mi perfil): quita el token de {@code users/{uid}.fcmTokens} y borra el
     * token local; luego llama a {@code done} (una sola vez, en un hilo de fondo; pasados {@link #ACK_TIMEOUT_MS}
     * aunque no haya respuesta). Sin flag o sin sesión, {@code done} enseguida.
     */
    public void beforeSignOut(@NonNull Runnable done) {
        removeThisDevice(done);
    }

    /** Antes de la Function {@code deleteAccount}: lo mismo que {@link #beforeSignOut}. */
    public void beforeDeleteAccount(@NonNull Runnable done) {
        removeThisDevice(done);
    }

    /** La Function {@code deleteAccount} falló y la sesión sigue: vuelve a registrar el token. */
    public void onDeleteAccountFailed() {
        if (!enabled.getAsBoolean()) {
            return;
        }
        serial.execute(() -> {
            if (!suspended) {
                return;
            }
            suspended = false;
            String uid = readyUid;
            if (uid != null) {
                register(uid);
            }
        });
    }

    // ---- en el serial ------------------------------------------------------------------------------------

    private void onProfileState(@Nullable ProfileState state) {
        boolean ready = state != null && state.status == ProfileState.Status.READY && state.profile != null;
        if (!ready) {
            // Sin sesión o sin perfil: lo anterior ya no vale (la próxima sesión vuelve a registrar).
            readyUid = null;
            registeredToken = null;
            suspended = false;
            return;
        }
        String uid = state.profile.uid;
        if (uid.equals(readyUid)) {
            return; // el mismo perfil vuelve a emitir (nombre, color): ya está registrado
        }
        readyUid = uid;
        registeredToken = null;
        suspended = false;
        register(uid);
    }

    private void register(@NonNull String uid) {
        if (!uid.equals(backend.currentUid())) {
            return; // la sesión ya no es la del perfil
        }
        backend.fetchToken(serial, new PushTokenBackend.TokenCallback() {
            @Override
            public void onToken(@NonNull String token) {
                if (!uid.equals(readyUid) || suspended) {
                    return; // cambió la sesión o empezó un cierre mientras tanto
                }
                backend.updateTokens(uid, list -> FcmTokens.with(list, token), serial,
                        new PushTokenBackend.Callback() {
                            @Override
                            public void onSuccess() {
                                if (uid.equals(readyUid)) {
                                    registeredToken = token;
                                }
                            }

                            @Override
                            public void onError(@NonNull Throwable error) {
                                errorLog.log("fcmTokens add failed", error);
                            }
                        });
            }

            @Override
            public void onError(@NonNull Throwable error) {
                errorLog.log("FCM token fetch failed", error);
            }
        });
    }

    private void removeThisDevice(@NonNull Runnable done) {
        if (!enabled.getAsBoolean()) {
            done.run();
            return;
        }
        serial.execute(() -> {
            suspended = true;
            String uid = backend.currentUid();
            if (uid == null) {
                done.run();
                return;
            }
            Runnable finish = once(done);
            String known = uid.equals(readyUid) ? registeredToken : null;
            if (known != null) {
                removeAndDelete(uid, known, finish);
                return;
            }
            // Aún no registrado en este proceso (o falló): el token local, si lo hay, igualmente fuera.
            backend.fetchToken(serial, new PushTokenBackend.TokenCallback() {
                @Override
                public void onToken(@NonNull String token) {
                    removeAndDelete(uid, token, finish);
                }

                @Override
                public void onError(@NonNull Throwable error) {
                    errorLog.log("FCM token fetch before sign-out failed", error);
                    finish.run();
                }
            });
        });
    }

    /** Quita {@code token} de la lista y, pase lo que pase, borra el token local; luego {@code finish}. */
    private void removeAndDelete(@NonNull String uid, @NonNull String token, @NonNull Runnable finish) {
        backend.updateTokens(uid, list -> FcmTokens.without(list, token), serial, new PushTokenBackend.Callback() {
            @Override
            public void onSuccess() {
                deleteLocal(finish);
            }

            @Override
            public void onError(@NonNull Throwable error) {
                errorLog.log("fcmTokens remove failed", error);
                deleteLocal(finish);
            }
        });
    }

    private void deleteLocal(@NonNull Runnable finish) {
        backend.deleteToken(serial, new PushTokenBackend.Callback() {
            @Override
            public void onSuccess() {
                registeredToken = null;
                finish.run();
            }

            @Override
            public void onError(@NonNull Throwable error) {
                errorLog.log("FCM token delete failed", error);
                registeredToken = null;
                finish.run();
            }
        });
    }

    /** {@code done} una sola vez: al terminar o, si no llega respuesta, pasados {@link #ACK_TIMEOUT_MS}. */
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
}
