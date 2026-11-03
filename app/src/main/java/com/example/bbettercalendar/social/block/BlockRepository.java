package com.example.bbettercalendar.social.block;

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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Bloquear usuarios (L-u9; decisión d1 de L). Bloquear llama a la callable {@code blockUser}; la lista de
 * bloqueados es {@code blocks/me/blocked}; desbloquear borra el documento.
 *
 * <ul>
 *   <li><b>Solo mientras se observa</b> (03 §12): el listener se engancha con el primer observador activo de
 *       {@link #observeBlocked()} y se suelta con el último y al cerrar sesión. Sin sesión: {@code SIGNED_OUT}
 *       y ningún listener.</li>
 *   <li>Un documento malformado se descarta ({@link BlockMapping}); nunca rompe la lista.</li>
 * </ul>
 *
 * <p>Regla #3: un {@link SerialExecutor} sobre el {@code @IoExecutor}; el estado interno solo se toca ahí y se
 * publica con {@code postValue}. Los callbacks se llaman en el serial. Firebase solo por {@link BlockBackend}.
 */
@Singleton
public class BlockRepository {

    /** El resultado de {@link #block}, en el serial. */
    public interface Callback {
        void onResult(@NonNull BlockResult result);
    }

    /** El resultado de {@link #unblock}, en el serial: true si se borró. */
    public interface UnblockCallback {
        void onResult(boolean ok);
    }

    private final BlockBackend backend;
    private final Executor serial;

    private final Tracked blocked;
    private final WatchedListener<String, BlockBackend.DocList> watcher;

    // ---- estado: solo en el serial ----------------------------------------------------------------------
    private boolean active;
    @Nullable
    private String sessionUid;
    /** La caché vacía de antes de la primera respuesta no sabe nada; hasta que llegue algo no se publica. */
    private boolean answered;

    @Inject
    public BlockRepository(FirebaseBlockBackend backend, @IoExecutor ExecutorService ioExecutor) {
        this(backend, new SerialExecutor(ioExecutor), new MainLooperScheduler());
        // Tras un error que espera (permisos), el listener vuelve a engancharse al volver a primer plano.
        ProcessForeground.observe(this::onForeground);
    }

    /** Para tests: Firebase llega solo por {@link BlockBackend} y su {@code ListenerSource}. */
    BlockRepository(@NonNull BlockBackend backend, @NonNull Executor serial, @NonNull Scheduler scheduler) {
        this.backend = backend;
        this.serial = serial;
        this.sessionUid = backend.currentUid();
        this.blocked = new Tracked(sessionUid != null ? BlockState.loading() : BlockState.signedOut());
        this.watcher = new WatchedListener<>(serial, scheduler, backend.blocked(), new BlockSink());
        backend.addAuthListener(uid -> serial.execute(() -> onSession(uid)));
    }

    /** La lista de bloqueados, el más nuevo primero; escucha solo mientras se observa. */
    @NonNull
    public LiveData<BlockState> observeBlocked() {
        return blocked;
    }

    /** Bloquea a {@code uid} por la callable: {@code {status:'blocked'}} → BLOCKED, lo demás → ERROR. */
    public void block(@NonNull String uid, @NonNull Callback callback) {
        serial.execute(() -> {
            if (sessionUid == null) {
                callback.onResult(BlockResult.ERROR);
                return;
            }
            backend.blockUser(uid, serial, new BlockBackend.CallResult() {
                @Override
                public void onSuccess(@Nullable Map<String, Object> data) {
                    callback.onResult(BlockMapping.fromData(data));
                }

                @Override
                public void onError(@NonNull Throwable error) {
                    callback.onResult(BlockResult.ERROR);
                }
            });
        });
    }

    /** Desbloquea: borra {@code blocks/me/blocked/uid}. El listener publica el cambio. */
    public void unblock(@NonNull String uid, @NonNull UnblockCallback callback) {
        serial.execute(() -> {
            if (sessionUid == null) {
                callback.onResult(false);
                return;
            }
            backend.unblock(sessionUid, uid, serial, new BlockBackend.Result() {
                @Override
                public void onSuccess() {
                    callback.onResult(true);
                }

                @Override
                public void onError(@NonNull Throwable error) {
                    callback.onResult(false);
                }
            });
        });
    }

    // ---- observadores activos (hilo principal) ----------------------------------------------------------

    private final class Tracked extends MutableLiveData<BlockState> {

        Tracked(BlockState initial) {
            super(initial);
        }

        @Override
        protected void onActive() {
            serial.execute(BlockRepository.this::activate);
        }

        @Override
        protected void onInactive() {
            serial.execute(BlockRepository.this::deactivate);
        }
    }

    // ---- sesión y activación (serial) ------------------------------------------------------------------

    private void activate() {
        active = true;
        if (sessionUid != null) {
            answered = false;
            watcher.attach(sessionUid);
        }
    }

    private void deactivate() {
        active = false;
        watcher.detach();
    }

    private void onSession(@Nullable String uid) {
        if (Objects.equals(uid, sessionUid)) {
            return;
        }
        sessionUid = uid;
        answered = false;
        if (uid == null) {
            watcher.detach();
            blocked.postValue(BlockState.signedOut());
        } else if (active) {
            watcher.attach(uid);
        } else {
            blocked.postValue(BlockState.loading());
        }
    }

    private void onForeground() {
        serial.execute(() -> {
            if (active) {
                watcher.onForeground();
            }
        });
    }

    /** El listener de {@code blocks/me/blocked}. Siempre en el serial y nunca con generación vieja. */
    private final class BlockSink implements WatchedListener.Sink<BlockBackend.DocList> {

        @Override
        public void onLoading() {
            blocked.postValue(BlockState.loading());
        }

        @Override
        public void onDetached() {
            if (sessionUid == null) {
                blocked.postValue(BlockState.signedOut());
            }
        }

        @Override
        public void onError(@NonNull ListenerException error) {
            blocked.postValue(BlockState.error(error.original()));
        }

        @Override
        public void onValue(@NonNull BlockBackend.DocList list) {
            if (sessionUid == null) {
                return;
            }
            if (list.fromCache && list.docs.isEmpty() && !answered) {
                return;
            }
            answered = true;
            blocked.postValue(BlockState.ready(BlockMapping.cards(list.docs)));
        }
    }
}
