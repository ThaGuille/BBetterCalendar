package com.example.bbettercalendar.social.stats;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Lo poco de Firebase que necesita {@link StatsPublisher} (F3-u4), detrás de una interfaz propia para probarlo
 * con Mockito sin Firebase (como {@code FriendsBackend} en F2). La implementación es
 * {@link FirestoreStatsBackend}.
 */
public interface StatsBackend {

    /** Resultado de una escritura; llega en el {@link Executor} que se pasó. */
    interface Callback {
        void onSuccess();

        void onError(@NonNull Throwable error);
    }

    /** uid de la sesión actual, o null sin sesión. */
    @Nullable
    String currentUid();

    /** {@code set()} completo de {@code stats/{uid}} con {@code data} (idempotente, la última gana). */
    void setStats(@NonNull String uid, @NonNull Map<String, Object> data, @NonNull Executor executor,
                  @NonNull Callback callback);
}
