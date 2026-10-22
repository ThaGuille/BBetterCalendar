package com.example.bbettercalendar.social.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Executor;

/**
 * Una fuente de eventos en vivo por clave (F2-u1), p. ej. el {@code addSnapshotListener} de un documento
 * de Firestore. Es una interfaz propia de la app: {@link WatchedListener} solo habla con ella, así que
 * sus tests no tocan clases de Firebase (decisión del usuario: interfaces propias + Mockito).
 *
 * <p>Los errores llegan como {@link ListenerException} con el código de Firestore por nombre
 * ({@code UNAVAILABLE}, {@code PERMISSION_DENIED}…); ver {@link FirestoreDocumentSource}.
 */
public interface ListenerSource<K, T> {

    interface Events<T> {
        /**
         * Un valor nuevo ({@code error == null}) o el error que termina este listener. {@code fromCache}
         * es true si el valor sale de la caché local y no del servidor (Firestore entrega primero el de
         * la caché al reenganchar): no prueba que la conexión funcione.
         */
        void onEvent(@Nullable T value, boolean fromCache, @Nullable ListenerException error);
    }

    /** Quita el listener; puede llamarse más de una vez. */
    interface Registration {
        void remove();
    }

    /** Engancha un listener para {@code key}; los eventos se entregan en {@code executor}. */
    @NonNull
    Registration listen(@NonNull K key, @NonNull Executor executor, @NonNull Events<T> events);
}
