package com.example.bbettercalendar.social.friends;

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
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * {@link FriendsBackend} sobre Firestore, Functions y Auth (F2-u5): el único sitio de {@code social/friends}
 * que toca clases de Firebase. Los listeners usan {@code MetadataChanges.INCLUDE} (como
 * {@code FirestoreDocumentSource}): sin él, tras un resultado de la caché no llega la confirmación del
 * servidor si no cambia nada. Los timestamps pendientes se estiman ({@code ServerTimestampBehavior.ESTIMATE}).
 */
@Singleton
public class FirestoreFriendsBackend implements FriendsBackend {

    static final String FRIENDS = "friends";
    static final String REMOVE_FRIEND = "removeFriend";

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;
    private final FirebaseFunctions functions;

    @Inject
    public FirestoreFriendsBackend(FirebaseAuth auth, FirebaseFirestore db, FirebaseFunctions functions) {
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
    public ListenerSource<String, DocList> friendsOf() {
        return querySource(uid -> db.collection(ProfileWrites.USERS).document(uid).collection(FRIENDS));
    }

    @NonNull
    @Override
    public ListenerSource<String, Doc> profile() {
        return (uid, executor, events) -> {
            ListenerRegistration reg = db.collection(ProfileWrites.PROFILES).document(uid)
                    .addSnapshotListener(executor, MetadataChanges.INCLUDE, (snap, error) -> {
                        if (error != null) {
                            events.onEvent(null, false, wrapListener(error));
                        } else if (snap != null) {
                            Doc doc = toDoc(snap);
                            events.onEvent(doc, doc.fromCache, null);
                        }
                    });
            return reg::remove;
        };
    }

    @NonNull
    @Override
    public ListenerSource<String, DocList> requestsTo() {
        return querySource(uid -> db.collection(FriendRequestWrites.FRIEND_REQUESTS)
                .whereEqualTo(FriendRequestWrites.TO, uid));
    }

    @NonNull
    @Override
    public ListenerSource<String, DocList> requestsFrom() {
        return querySource(uid -> db.collection(FriendRequestWrites.FRIEND_REQUESTS)
                .whereEqualTo(FriendRequestWrites.FROM, uid));
    }

    @Override
    public void getFromServer(@NonNull String path, @NonNull Executor executor, @NonNull Result<Doc> result) {
        db.document(path).get(Source.SERVER)
                .addOnSuccessListener(executor, snap -> result.onSuccess(toDoc(snap)))
                .addOnFailureListener(executor, e -> result.onError(wrap(e)));
    }

    @NonNull
    @Override
    public Batch batch() {
        WriteBatch batch = db.batch();
        return new Batch() {
            @NonNull
            @Override
            public Batch set(@NonNull String path, @NonNull Map<String, Object> data) {
                batch.set(db.document(path), data);
                return this;
            }

            @NonNull
            @Override
            public Batch update(@NonNull String path, @NonNull Map<String, Object> data) {
                batch.update(db.document(path), data);
                return this;
            }

            @NonNull
            @Override
            public Batch delete(@NonNull String path) {
                batch.delete(db.document(path));
                return this;
            }

            @Override
            public void commit(@NonNull Executor executor, @NonNull Result<Void> result) {
                batch.commit()
                        .addOnSuccessListener(executor, v -> result.onSuccess(null))
                        .addOnFailureListener(executor, e -> result.onError(wrap(e)));
            }
        };
    }

    @Override
    public void callRemoveFriend(@NonNull String friendUid, @NonNull Executor executor,
                                 @NonNull Result<Void> result) {
        Map<String, Object> data = new HashMap<>();
        data.put("friendUid", friendUid);
        functions.getHttpsCallable(REMOVE_FRIEND).call(data)
                .addOnSuccessListener(executor, r -> result.onSuccess(null))
                .addOnFailureListener(executor, e -> result.onError(wrap(e)));
    }

    // ---- interno -----------------------------------------------------------------------------------------

    private interface QueryFor {
        @NonNull
        Query query(@NonNull String key);
    }

    @NonNull
    private static ListenerSource<String, DocList> querySource(@NonNull QueryFor queryFor) {
        return (key, executor, events) -> {
            ListenerRegistration reg = queryFor.query(key).addSnapshotListener(executor,
                    MetadataChanges.INCLUDE, (snap, error) -> {
                        if (error != null) {
                            events.onEvent(null, false, wrapListener(error));
                        } else if (snap != null) {
                            DocList list = toDocList(snap);
                            events.onEvent(list, list.fromCache, null);
                        }
                    });
            return reg::remove;
        };
    }

    @NonNull
    private static DocList toDocList(@NonNull QuerySnapshot snap) {
        List<Doc> docs = new ArrayList<>();
        for (DocumentSnapshot d : snap.getDocuments()) {
            docs.add(toDoc(d));
        }
        return new DocList(docs, snap.getMetadata().isFromCache());
    }

    @NonNull
    private static Doc toDoc(@NonNull DocumentSnapshot snap) {
        return new Doc(snap.getId(), snap.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                snap.getMetadata().isFromCache());
    }

    /** Errores de Firestore y Functions → {@link ListenerException} con el nombre del código. */
    @NonNull
    static Throwable wrap(@NonNull Exception e) {
        if (e instanceof FirebaseFirestoreException) {
            return new ListenerException(((FirebaseFirestoreException) e).getCode().name(), e);
        }
        if (e instanceof FirebaseFunctionsException) {
            return new ListenerException(((FirebaseFunctionsException) e).getCode().name(), e);
        }
        return e;
    }

    @NonNull
    private static ListenerException wrapListener(@NonNull FirebaseFirestoreException e) {
        return new ListenerException(e.getCode().name(), e);
    }
}
