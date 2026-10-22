package com.example.bbettercalendar.social.friends;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.social.common.ListenerSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Lo que {@link FriendsRepository} y {@link ReportRepository} necesitan de Firebase (F2-u5), como interfaz
 * propia de la app: los repositorios no tocan clases de Firestore ni de Functions, así que sus tests
 * simulan esta interfaz y los {@link ListenerSource} con Mockito (decisión del usuario). La única
 * implementación real es {@link FirestoreFriendsBackend}.
 *
 * <p>Los documentos llegan como {@link Doc} (id + mapa de campos): el paso a modelos lo hace
 * {@link FriendsMapping}, que es puro. Los errores de Firestore y Functions llegan como
 * {@code ListenerException} con el nombre de su código ({@code PERMISSION_DENIED}…).
 */
public interface FriendsBackend {

    /** Un documento leído: {@link #data} es null si no existe. */
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

    /** El resultado de una consulta en vivo. */
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

    /** Resultado de una operación suelta, entregado en el {@link Executor} que se pasa. */
    interface Result<T> {
        void onSuccess(@Nullable T value);

        void onError(@NonNull Throwable error);
    }

    /**
     * Un lote de escrituras que se aplica entero o nada (un {@code WriteBatch} de Firestore): las reglas de
     * F2-u3 leen su estado final (solicitud + contador; reporte + rechazo). Las rutas son de documento,
     * p. ej. {@code friendRequests/a_b}.
     */
    interface Batch {
        @NonNull
        Batch set(@NonNull String path, @NonNull Map<String, Object> data);

        @NonNull
        Batch update(@NonNull String path, @NonNull Map<String, Object> data);

        @NonNull
        Batch delete(@NonNull String path);

        void commit(@NonNull Executor executor, @NonNull Result<Void> result);
    }

    @Nullable
    String currentUid();

    void addAuthListener(@NonNull AuthListener listener);

    /** {@code users/{key}/friends} en vivo. */
    @NonNull
    ListenerSource<String, DocList> friendsOf();

    /** {@code profiles/{key}} en vivo. */
    @NonNull
    ListenerSource<String, Doc> profile();

    /** {@code friendRequests} con {@code to == key} en vivo (las reglas exigen ese filtro). */
    @NonNull
    ListenerSource<String, DocList> requestsTo();

    /** {@code friendRequests} con {@code from == key} en vivo. */
    @NonNull
    ListenerSource<String, DocList> requestsFrom();

    /** Lee un documento del servidor (no de la caché). Uno que no existe llega con {@code data == null}. */
    void getFromServer(@NonNull String path, @NonNull Executor executor, @NonNull Result<Doc> result);

    @NonNull
    Batch batch();

    /** La callable {@code removeFriend} (F2-u4): borra los dos lados y el espejo; idempotente. */
    void callRemoveFriend(@NonNull String friendUid, @NonNull Executor executor, @NonNull Result<Void> result);
}
