package com.example.bbettercalendar.social.block;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.social.common.ListenerException;
import com.example.bbettercalendar.social.common.ListenerSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * {@link BlockBackend} sobre Firestore, Functions y Auth (L-u9): el único sitio de {@code social/block} que toca
 * clases de Firebase. Escucha {@code blocks/{me}/blocked}; bloquear va por la callable (las reglas no dejan al
 * cliente crear) y desbloquear es borrar el documento (el dueño puede).
 */
@Singleton
public class FirebaseBlockBackend implements BlockBackend {

    static final String BLOCKS = "blocks";
    static final String BLOCKED = "blocked";
    static final String CALLABLE = "blockUser";

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;
    private final FirebaseFunctions functions;

    @Inject
    public FirebaseBlockBackend(FirebaseAuth auth, FirebaseFirestore db, FirebaseFunctions functions) {
        this.auth = auth;
        this.db = db;
        this.functions = functions;
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
    public ListenerSource<String, DocList> blocked() {
        return (uid, executor, events) -> {
            ListenerRegistration reg = db.collection(BLOCKS).document(uid).collection(BLOCKED)
                    .addSnapshotListener(executor, MetadataChanges.INCLUDE, (snap, error) -> {
                        if (error != null) {
                            events.onEvent(null, false, new ListenerException(error.getCode().name(), error));
                        } else if (snap != null) {
                            DocList list = toDocList(snap);
                            events.onEvent(list, list.fromCache, null);
                        }
                    });
            return reg::remove;
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public void blockUser(@NonNull String uid, @NonNull Executor executor, @NonNull CallResult result) {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid);
        functions.getHttpsCallable(CALLABLE).call(data)
                .addOnSuccessListener(executor, r -> {
                    Object body = r.getData();
                    result.onSuccess(body instanceof Map ? (Map<String, Object>) body : null);
                })
                .addOnFailureListener(executor, result::onError);
    }

    @Override
    public void unblock(@NonNull String me, @NonNull String uid, @NonNull Executor executor,
                        @NonNull Result result) {
        db.collection(BLOCKS).document(me).collection(BLOCKED).document(uid).delete()
                .addOnSuccessListener(executor, v -> result.onSuccess())
                .addOnFailureListener(executor, e -> result.onError(e instanceof FirebaseFirestoreException
                        ? new ListenerException(((FirebaseFirestoreException) e).getCode().name(), e) : e));
    }

    @NonNull
    private static DocList toDocList(@NonNull QuerySnapshot snap) {
        List<Doc> docs = new ArrayList<>();
        for (DocumentSnapshot d : snap.getDocuments()) {
            // Un documento ilegible llega con data null y se descarta en BlockMapping.
            docs.add(new Doc(d.getId(), readData(d)));
        }
        return new DocList(docs, snap.getMetadata().isFromCache());
    }

    @Nullable
    private static Map<String, Object> readData(@NonNull DocumentSnapshot d) {
        try {
            return d.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
