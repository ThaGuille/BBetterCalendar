package com.example.bbettercalendar.social.inbox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.social.common.ListenerException;
import com.example.bbettercalendar.social.common.ListenerSource;
import com.example.bbettercalendar.social.profile.ProfileWrites;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * {@link InboxBackend} sobre Firestore y Auth (F5-u5): el único sitio de {@code social/inbox} que toca clases de
 * Firebase. El listener usa {@code MetadataChanges.INCLUDE} (como {@code FirestoreFriendsBackend}).
 */
@Singleton
public class FirestoreInboxBackend implements InboxBackend {

    static final String INBOX = "inbox";

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    @Inject
    public FirestoreInboxBackend(FirebaseAuth auth, FirebaseFirestore db) {
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
    public ListenerSource<String, DocList> inbox() {
        return (uid, executor, events) -> {
            Query query = db.collection(ProfileWrites.USERS).document(uid).collection(INBOX)
                    .orderBy(InboxMapping.CREATED_AT, Query.Direction.DESCENDING)
                    .limit(InboxMapping.LIMIT);
            ListenerRegistration reg = query.addSnapshotListener(executor, MetadataChanges.INCLUDE,
                    (snap, error) -> {
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
    public void markRead(@NonNull String uid, @NonNull String id, @NonNull Executor executor,
                         @NonNull Result result) {
        // Solo ese campo: las reglas rechazan cualquier otro cambio.
        db.collection(ProfileWrites.USERS).document(uid).collection(INBOX).document(id)
                .update(InboxMapping.READ, true)
                .addOnSuccessListener(executor, v -> result.onSuccess())
                .addOnFailureListener(executor, e -> result.onError(e instanceof FirebaseFirestoreException
                        ? new ListenerException(((FirebaseFirestoreException) e).getCode().name(), e) : e));
    }

    @NonNull
    private static DocList toDocList(@NonNull QuerySnapshot snap) {
        List<Doc> docs = new ArrayList<>();
        for (DocumentSnapshot d : snap.getDocuments()) {
            // Un documento ilegible llega con data null y se descarta en InboxMapping.
            docs.add(new Doc(d.getId(), readData(d)));
        }
        return new DocList(docs, snap.getMetadata().isFromCache());
    }

    @Nullable
    private static java.util.Map<String, Object> readData(@NonNull DocumentSnapshot d) {
        try {
            return d.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
