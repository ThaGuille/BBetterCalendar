package com.example.bbettercalendar.social.inbox;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Bandeja de la campana: ánimos, empujones y solicitudes recibidos (F5-u5; 03 §5; decisiones d1 y d2 de F5).
 *
 * <ul>
 *   <li><b>Solo mientras se observa</b> (03 §12): el único listener de {@code users/me/inbox} (los 50 más
 *       nuevos) se engancha cuando {@link #observeInbox()} o {@link #unreadCount()} gana su primer observador
 *       activo y se suelta cuando el último deja de observar y al cerrar sesión. Sin sesión no hay listener y
 *       el estado es {@code SIGNED_OUT}.</li>
 *   <li><b>Un documento ilegible o malformado se descarta</b> ({@link InboxMapping}); nunca rompe la lista.</li>
 *   <li><b>Leer</b> ({@link #markAllRead()}, {@link #markRead(String)}) escribe solo {@code read = true}, el
 *       único cambio que dejan las reglas, y no espera respuesta: el listener ya publica el resultado.</li>
 * </ul>
 *
 * <p>Regla #3: un {@link SerialExecutor} sobre el {@code @IoExecutor} para el listener y el estado interno, que
 * solo se toca en ese hilo; se publica con {@code postValue}. Firebase solo se toca por {@link InboxBackend}.
 */
@Singleton
public class InboxRepository {

    private final InboxBackend backend;
    private final Executor serial;

    private final Tracked<InboxState> inbox;
    private final Tracked<Integer> unread;
    private final WatchedListener<String, InboxBackend.DocList> watcher;

    /** Observadores activos de las dos LiveData. Solo en el hilo principal. */
    private int activeObservers;

    // ---- estado: solo en el serial ----------------------------------------------------------------------
    private boolean active;
    @Nullable
    private String sessionUid;
    /** La caché vacía de antes de la primera respuesta no sabe nada; hasta que llegue algo no se publica. */
    private boolean answered;
    @NonNull
    private List<InboxItem> lastItems = Collections.emptyList();

    @Inject
    public InboxRepository(FirestoreInboxBackend backend, @IoExecutor ExecutorService ioExecutor) {
        this(backend, new SerialExecutor(ioExecutor), new MainLooperScheduler());
        // Tras un error que espera (permisos), el listener vuelve a engancharse al volver a primer plano.
        ProcessForeground.observe(this::onForeground);
    }

    /** Para tests: Firebase llega solo por {@link InboxBackend} y su {@code ListenerSource}. */
    InboxRepository(@NonNull InboxBackend backend, @NonNull Executor serial, @NonNull Scheduler scheduler) {
        this.backend = backend;
        this.serial = serial;
        // La sesión inicial ya cuenta: el primer aviso de Auth con el mismo uid no lo toma por un cambio.
        this.sessionUid = backend.currentUid();
        this.inbox = new Tracked<>(sessionUid != null ? InboxState.loading() : InboxState.signedOut());
        this.unread = new Tracked<>(0);
        this.watcher = new WatchedListener<>(serial, scheduler, backend.inbox(), new InboxSink());
        backend.addAuthListener(uid -> serial.execute(() -> onSession(uid)));
    }

    // ---- observar --------------------------------------------------------------------------------------

    /** La bandeja, la más nueva primero; sigue a la sesión y escucha solo mientras se observa. */
    @NonNull
    public LiveData<InboxState> observeInbox() {
        return inbox;
    }

    /** Avisos sin leer (el punto de la campana, sin número en pantalla); 0 sin sesión. Escucha como arriba. */
    @NonNull
    public LiveData<Integer> unreadCount() {
        return unread;
    }

    /** Marca como leídos todos los avisos sin leer que se ven (abrir la bandeja, d1). No hace nada sin ellos. */
    public void markAllRead() {
        serial.execute(() -> {
            if (sessionUid == null) {
                return;
            }
            List<String> ids = new ArrayList<>();
            for (InboxItem item : lastItems) {
                if (!item.read) {
                    ids.add(item.id);
                }
            }
            write(sessionUid, ids);
        });
    }

    /** Marca como leído ese aviso (tocar una notificación). No hace nada si ya se sabe leído. */
    public void markRead(@NonNull String id) {
        serial.execute(() -> {
            if (sessionUid == null) {
                return;
            }
            for (InboxItem item : lastItems) {
                if (item.id.equals(id) && item.read) {
                    return;
                }
            }
            write(sessionUid, Collections.singletonList(id));
        });
    }

    /** En el serial. */
    private void write(@NonNull String uid, @NonNull List<String> ids) {
        if (ids.isEmpty()) {
            return;
        }
        // Una escritura por aviso (L-u7): uno borrado, podado o rechazado no frena a los demás.
        for (String id : ids) {
            backend.markRead(uid, id, serial, new InboxBackend.Result() {
                @Override
                public void onSuccess() {
                    // El listener publica el cambio.
                }

                @Override
                public void onError(@NonNull Throwable error) {
                    // Sin consecuencias: seguirá sin leer y se reintenta al volver a abrir la bandeja.
                }
            });
        }
    }

    // ---- observadores activos (hilo principal) ----------------------------------------------------------

    private final class Tracked<T> extends MutableLiveData<T> {

        Tracked(T initial) {
            super(initial);
        }

        @Override
        protected void onActive() {
            if (activeObservers++ == 0) {
                serial.execute(InboxRepository.this::activate);
            }
        }

        @Override
        protected void onInactive() {
            if (activeObservers > 0 && --activeObservers == 0) {
                serial.execute(InboxRepository.this::deactivate);
            }
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
            return; // misma sesión ya enganchada
        }
        sessionUid = uid;
        lastItems = Collections.emptyList();
        answered = false;
        if (uid == null) {
            watcher.detach();
            publishSignedOut();
        } else if (active) {
            watcher.attach(uid);
        } else {
            inbox.postValue(InboxState.loading());
            unread.postValue(0);
        }
    }

    private void onForeground() {
        serial.execute(() -> {
            if (active) {
                watcher.onForeground();
            }
        });
    }

    private void publishSignedOut() {
        inbox.postValue(InboxState.signedOut());
        unread.postValue(0);
    }

    /** El listener de {@code users/me/inbox}. Siempre en el serial y nunca con generación vieja. */
    private final class InboxSink implements WatchedListener.Sink<InboxBackend.DocList> {

        @Override
        public void onLoading() {
            inbox.postValue(InboxState.loading());
        }

        @Override
        public void onDetached() {
            // Detach por quedarse sin observadores no dice nada nuevo; solo la sesión cerrada publica.
            if (sessionUid == null) {
                publishSignedOut();
            }
        }

        @Override
        public void onError(@NonNull ListenerException error) {
            // El contador se queda como estaba: un fallo de red no apaga ni enciende el punto.
            inbox.postValue(InboxState.error(error.original()));
        }

        @Override
        public void onValue(@NonNull InboxBackend.DocList list) {
            if (sessionUid == null) {
                return;
            }
            if (list.fromCache && list.docs.isEmpty() && !answered) {
                return; // la caché vacía no sabe nada; esperamos al servidor
            }
            answered = true;
            lastItems = InboxMapping.items(list.docs);
            InboxState state = InboxState.ready(lastItems);
            inbox.postValue(state);
            unread.postValue(state.unread());
        }
    }
}
