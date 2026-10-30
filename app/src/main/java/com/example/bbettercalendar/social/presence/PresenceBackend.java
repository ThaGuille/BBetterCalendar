package com.example.bbettercalendar.social.presence;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Lo poco de Firebase que necesita {@link PresencePublisher} (F4-u3), detrás de una interfaz propia para
 * probarlo con Mockito sin Firebase (como {@code StatsBackend} en F3). La implementación es
 * {@link RtdbPresenceBackend}, la única clase de la presencia que importa {@code firebase-database}.
 * Todos los callbacks llegan en el {@link Executor} que se pasa.
 */
public interface PresenceBackend {

    /** Resultado de una operación; llega en el {@link Executor} que se pasó. */
    interface Callback {
        void onSuccess();

        void onError(@NonNull Throwable error);
    }

    /** Eventos de la sesión y de la conexión. */
    interface Listener {
        /** La sesión cambió: uid nuevo, o null al cerrarla. También el valor inicial. */
        void onUidChanged(@Nullable String uid);

        /** {@code .info/connected}: true al (re)conectar con el servidor, false al perder la conexión. */
        void onConnectedChanged(boolean connected);

        /** {@code .info/serverTimeOffset}: hora del servidor menos la del móvil, en ms. */
        void onServerTimeOffset(long offsetMs);
    }

    /**
     * Empieza a escuchar sesión, conexión y desfase (una vez por proceso; la presencia vive con la app).
     */
    void observe(@NonNull Executor executor, @NonNull Listener listener);

    /** El centinela de hora del servidor para {@code since} ({@code ServerValue.TIMESTAMP}). */
    @NonNull
    Object serverTimestamp();

    /** {@code status/{uid}.onDisconnect().setValue(value)}: lo aplica el servidor si se cae el socket. */
    void registerOnDisconnect(@NonNull String uid, @NonNull Map<String, Object> value, @NonNull Executor executor,
                              @NonNull Callback callback);

    /** {@code status/{uid}.onDisconnect().cancel()}. */
    void cancelOnDisconnect(@NonNull String uid, @NonNull Executor executor, @NonNull Callback callback);

    /** {@code status/{uid}.setValue(value)} (sustituye el nodo entero). */
    void setStatus(@NonNull String uid, @NonNull Map<String, Object> value, @NonNull Executor executor,
                   @NonNull Callback callback);
}
