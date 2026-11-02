package com.example.bbettercalendar.social.push;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

/**
 * Lo poco de Firebase que necesita {@link PushTokenRegistrar} (F5-u6), detrás de una interfaz propia para probarlo
 * sin Firebase (como {@code StatsBackend} en F3). La implementación es {@link FirebasePushTokenBackend}: Auth,
 * Firestore y el token del dispositivo (FCM, o uno falso en las compilaciones de emulador, decisión d8).
 * Todos los callbacks llegan en el {@link Executor} que se pasa.
 */
public interface PushTokenBackend {

    /** Resultado de una operación sin valor. */
    interface Callback {
        void onSuccess();

        void onError(@NonNull Throwable error);
    }

    /** Resultado de pedir el token del dispositivo. */
    interface TokenCallback {
        void onToken(@NonNull String token);

        void onError(@NonNull Throwable error);
    }

    /** uid de la sesión actual, o null sin sesión. */
    @Nullable
    String currentUid();

    /** El token de este dispositivo (lo crea si aún no hay). */
    void fetchToken(@NonNull Executor executor, @NonNull TokenCallback callback);

    /** Borra el token local de este dispositivo: el siguiente {@link #fetchToken} da uno nuevo. */
    void deleteToken(@NonNull Executor executor, @NonNull Callback callback);

    /**
     * En una transacción: lee {@code users/{uid}.fcmTokens} (ya limpia, la más antigua primero), aplica
     * {@code change} y escribe el resultado si cambió. Sin {@code users/{uid}} no escribe nada.
     */
    void updateTokens(@NonNull String uid, @NonNull UnaryOperator<List<String>> change,
                      @NonNull Executor executor, @NonNull Callback callback);
}
