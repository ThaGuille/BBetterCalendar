package com.example.bbettercalendar.social.stats;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.social.common.ListenerSource;

import java.util.Map;

/**
 * Lo que {@link RankingRepository} necesita de Firebase para leer {@code stats/} (F3-u5), como interfaz propia
 * de la app (igual que {@code FriendsBackend} en F2): el repositorio no toca clases de Firestore ni de Auth, así
 * que {@code RankingRepositoryTest} la simula con fakes. La única implementación real es
 * {@link FirestoreStatsReadBackend}. La escritura de {@code stats/} es de {@code StatsPublisher} (u4), no de aquí.
 *
 * <p>Los errores de los listeners llegan como {@code ListenerException} con el nombre del código de Firestore
 * ({@code PERMISSION_DENIED}…). El paso del mapa a {@link FriendStats} lo hace {@link StatsMapping}, que es puro.
 */
public interface StatsReadBackend {

    /** {@code stats/{uid}} leído: {@link #data} es null si no existe. */
    final class Doc {
        @NonNull
        public final String id;
        @Nullable
        public final Map<String, Object> data;
        /** true si sale de la caché local: un «no existe» de la caché no prueba nada. */
        public final boolean fromCache;

        public Doc(@NonNull String id, @Nullable Map<String, Object> data, boolean fromCache) {
            this.id = id;
            this.data = data;
            this.fromCache = fromCache;
        }

        public boolean exists() {
            return data != null;
        }
    }

    /** Cambio de sesión: el uid nuevo, o null sin sesión. Se llama también al registrarse. */
    interface AuthListener {
        void onAuth(@Nullable String uid);
    }

    @Nullable
    String currentUid();

    void addAuthListener(@NonNull AuthListener listener);

    /** {@code stats/{key}} en vivo (con los cambios de metadatos: el «sigue sin existir» del servidor). */
    @NonNull
    ListenerSource<String, Doc> stats();
}
