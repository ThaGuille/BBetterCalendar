package com.example.bbettercalendar.social.inbox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.social.common.ListenerSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Lo que {@link InboxRepository} necesita de Firebase (F5-u5), como interfaz propia de la app (igual que
 * {@code FriendsBackend}): el repositorio no toca clases de Firestore ni de Auth, así que su test la simula con
 * Mockito. La única implementación real es {@link FirestoreInboxBackend}. Los errores llegan como
 * {@code ListenerException} con el código de Firestore por nombre.
 */
public interface InboxBackend {

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

    /** Resultado de una escritura, entregado en el {@link Executor} que se pasa. */
    interface Result {
        void onSuccess();

        void onError(@NonNull Throwable error);
    }

    @Nullable
    String currentUid();

    void addAuthListener(@NonNull AuthListener listener);

    /** {@code users/{key}/inbox}: los {@link InboxMapping#LIMIT} más nuevos por {@code createdAt}, en vivo. */
    @NonNull
    ListenerSource<String, DocList> inbox();

    /**
     * Pone {@code read = true} en ese aviso de {@code users/{uid}/inbox}, con su propia escritura y únicamente ese
     * campo (L-u7, d4: un aviso que falla no tumba a los demás; las reglas aceptan false o true antes).
     */
    void markRead(@NonNull String uid, @NonNull String id, @NonNull Executor executor, @NonNull Result result);
}
