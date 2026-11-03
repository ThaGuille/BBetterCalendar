package com.example.bbettercalendar.social.block;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.social.common.ListenerSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Lo que {@link BlockRepository} necesita de Firebase (L-u9), como interfaz propia de la app (igual que
 * {@code InboxBackend}): el repositorio no toca clases de Firestore, Functions ni Auth y su test la simula.
 * La única implementación real es {@link FirebaseBlockBackend}.
 */
public interface BlockBackend {

    /** Un documento leído: {@link #data} es null si no existe. */
    final class Doc {
        @NonNull
        public final String id;
        @Nullable
        public final Map<String, Object> data;

        public Doc(@NonNull String id, @Nullable Map<String, Object> data) {
            this.id = id;
            this.data = data;
        }
    }

    /** El resultado de la consulta en vivo. */
    final class DocList {
        @NonNull
        public final List<Doc> docs;
        public final boolean fromCache;

        public DocList(@NonNull List<Doc> docs, boolean fromCache) {
            this.docs = Collections.unmodifiableList(docs);
            this.fromCache = fromCache;
        }
    }

    /** Cambio de sesión: el uid nuevo, o null sin sesión. Se llama también al registrarse. */
    interface AuthListener {
        void onAuth(@Nullable String uid);
    }

    /** Resultado de {@code blockUser}: el mapa que devuelve la callable (o null). */
    interface CallResult {
        void onSuccess(@Nullable Map<String, Object> data);

        void onError(@NonNull Throwable error);
    }

    /** Resultado de una escritura. */
    interface Result {
        void onSuccess();

        void onError(@NonNull Throwable error);
    }

    @Nullable
    String currentUid();

    void addAuthListener(@NonNull AuthListener listener);

    /** {@code blocks/{key}/blocked}, en vivo. */
    @NonNull
    ListenerSource<String, DocList> blocked();

    /** Llama a la callable {@code blockUser} (europe-west1) con {@code {uid}}; responde en {@code executor}. */
    void blockUser(@NonNull String uid, @NonNull Executor executor, @NonNull CallResult result);

    /** Borra {@code blocks/{me}/blocked/{uid}}; responde en {@code executor}. */
    void unblock(@NonNull String me, @NonNull String uid, @NonNull Executor executor, @NonNull Result result);
}
