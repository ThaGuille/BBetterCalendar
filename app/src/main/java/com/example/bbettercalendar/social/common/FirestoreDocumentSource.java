package com.example.bbettercalendar.social.common;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;

import java.util.concurrent.Executor;

/**
 * {@link ListenerSource} de un documento de Firestore por clave (F2-u1): el único sitio de
 * {@code social/common} que toca las clases de Firestore. Escucha con {@code MetadataChanges.INCLUDE}
 * (sin él no llega el «sigue sin existir» del servidor tras un «no existe» de la caché) y traduce la
 * {@code FirebaseFirestoreException} a {@link ListenerException} con el nombre de su código.
 */
public final class FirestoreDocumentSource implements ListenerSource<String, DocumentSnapshot> {

    /** El documento que se escucha para cada clave (p. ej. {@code profiles/{uid}}). */
    public interface RefFor {
        @NonNull
        DocumentReference ref(@NonNull String key);
    }

    private final RefFor refFor;

    public FirestoreDocumentSource(@NonNull RefFor refFor) {
        this.refFor = refFor;
    }

    @NonNull
    @Override
    public Registration listen(@NonNull String key, @NonNull Executor executor,
                               @NonNull Events<DocumentSnapshot> events) {
        ListenerRegistration registration = refFor.ref(key).addSnapshotListener(executor,
                MetadataChanges.INCLUDE,
                (snap, error) -> events.onEvent(snap,
                        error == null ? null : new ListenerException(error.getCode().name(), error)));
        return registration::remove;
    }
}
