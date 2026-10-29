package com.example.bbettercalendar.social.stats;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Map;
import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * {@link StatsBackend} sobre Auth y Firestore (F3-u4): el único sitio de la publicación de stats que toca
 * clases de Firebase. Sin red, la caché offline de Firestore pone el {@code set()} en cola y lo envía al volver
 * la conexión (03 §9); el callback llega cuando el servidor responde.
 */
@Singleton
public class FirestoreStatsBackend implements StatsBackend {

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    @Inject
    public FirestoreStatsBackend(FirebaseAuth auth, FirebaseFirestore db) {
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
    public void setStats(@NonNull String uid, @NonNull Map<String, Object> data, @NonNull Executor executor,
                         @NonNull Callback callback) {
        db.collection(StatsWrites.STATS).document(uid).set(data)
                .addOnSuccessListener(executor, unused -> callback.onSuccess())
                .addOnFailureListener(executor, callback::onError);
    }
}
