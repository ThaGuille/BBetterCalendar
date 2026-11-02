package com.example.bbettercalendar.social.interaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Lo que {@link InteractionRepository} necesita de Firebase (F5-u5): llamar a {@code sendCheer} o
 * {@code sendNudge}, e identificar la sesión. Interfaz propia de la app: el repositorio no toca clases de
 * Functions ni de Auth y su test la simula. La única implementación real es {@link FunctionsInteractionBackend}.
 */
public interface InteractionBackend {

    /** El fallo de una callable: el código ({@code PERMISSION_DENIED}…) y el mensaje del servidor. */
    final class Failure extends Exception {
        @NonNull
        public final String code;
        /** {@code not-friends}, {@code studied-today}, {@code no-profile}…; null si no hay. */
        @Nullable
        public final String serverMessage;

        public Failure(@NonNull String code, @Nullable String serverMessage, @Nullable Throwable cause) {
            super(code + ": " + serverMessage, cause);
            this.code = code;
            this.serverMessage = serverMessage;
        }
    }

    /** Resultado de la llamada, entregado en el {@link Executor} que se pasa. */
    interface Result {
        /** {@code data} es el mapa que devuelve la callable ({@code {status: 'sent'|'already'}}), o null. */
        void onSuccess(@Nullable Map<String, Object> data);

        void onError(@NonNull Failure failure);
    }

    @Nullable
    String currentUid();

    /** Llama a la callable con {@code {friendUid, dayKey}}. */
    void call(@NonNull String callable, @NonNull String friendUid, @NonNull String dayKey,
              @NonNull Executor executor, @NonNull Result result);
}
