package com.example.bbettercalendar.social.stats;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.social.common.ListenerException;
import com.example.bbettercalendar.social.common.ListenerSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * {@link StatsReadBackend} sobre Firestore y Auth (F3-u5): el único sitio de la lectura de {@code stats/} que
 * toca clases de Firebase. Escucha con {@code MetadataChanges.INCLUDE} (como {@code FirestoreFriendsBackend}):
 * sin él, tras un «no existe» de la caché no llega la confirmación del servidor. El {@code updatedAt} pendiente
 * de una escritura propia se estima ({@code ServerTimestampBehavior.ESTIMATE}).
 */
@Singleton
public class FirestoreStatsReadBackend implements StatsReadBackend {

    /** Colección de las stats compartidas (03 §5). */
    static final String STATS = "stats";

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    @Inject
    public FirestoreStatsReadBackend(FirebaseAuth auth, FirebaseFirestore db) {
        this.auth = auth;
        this.db = db;
    }

    @Nullable
    @Override
    public String currentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    @Override
    public void addAuthListener(@NonNull AuthListener listener) {
        auth.addAuthStateListener(a -> {
            FirebaseUser user = a.getCurrentUser();
            listener.onAuth(user == null ? null : user.getUid());
        });
    }

    @NonNull
    @Override
    public ListenerSource<String, Doc> stats() {
        return (uid, executor, events) -> {
            ListenerRegistration reg = db.collection(STATS).document(uid)
                    .addSnapshotListener(executor, MetadataChanges.INCLUDE, (snap, error) -> {
                        if (error != null) {
                            events.onEvent(null, false, new ListenerException(error.getCode().name(), error));
                        } else if (snap != null) {
                            boolean fromCache = snap.getMetadata().isFromCache();
                            events.onEvent(new Doc(snap.getId(),
                                    snap.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE), fromCache),
                                    fromCache, null);
                        }
                    });
            return reg::remove;
        };
    }
}
