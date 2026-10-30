package com.example.bbettercalendar.social.presence;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.social.common.ListenerSource;

import java.util.Map;

/**
 * Lo que {@code FirebaseSocialRepository} necesita de Firebase para leer la presencia de los amigos (F4-u4), como
 * interfaz propia de la app (igual que {@code StatsReadBackend} en F3): el repositorio no toca clases de la Realtime
 * Database ni de Auth, así que sus tests la simulan. La única implementación real es
 * {@link RtdbPresenceReadBackend}. La escritura de {@code status/} es de {@code PresencePublisher} (u3), no de aquí.
 *
 * <p>Los errores de los listeners llegan como {@code ListenerException} con un nombre de código al estilo de
 * Firestore ({@code PERMISSION_DENIED} si las reglas aún no dejan leer; los demás, reintentables).
 */
public interface PresenceReadBackend {

    /** {@code status/{uid}} leído: {@link #data} es null si no existe o no es un mapa. */
    final class Status {
        @NonNull
        public final String uid;
        @Nullable
        public final Map<String, Object> data;

        public Status(@NonNull String uid, @Nullable Map<String, Object> data) {
            this.uid = uid;
            this.data = data;
        }
    }

    /** Cambio de sesión: el uid nuevo, o null sin sesión. Se llama también al registrarse. */
    interface AuthListener {
        void onAuth(@Nullable String uid);
    }

    @Nullable
    String currentUid();

    void addAuthListener(@NonNull AuthListener listener);

    /**
     * Desfase estimado del reloj del servidor ({@code .info/serverTimeOffset}): hora del servidor = reloj del móvil
     * + este valor. 0 mientras no se sepa.
     */
    long serverTimeOffsetMs();

    /** {@code status/{uid}} en vivo. */
    @NonNull
    ListenerSource<String, Status> status();
}
