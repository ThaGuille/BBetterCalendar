package com.example.bbettercalendar.social.presence;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.Map;
import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * {@link PresenceBackend} sobre Auth y la Realtime Database (F4-u3): la única clase de la presencia propia que
 * importa {@code firebase-database}. Escucha {@code .info/connected} y {@code .info/serverTimeOffset} y escribe
 * {@code status/{uid}} y su {@code onDisconnect}. Los callbacks de Firebase llegan al hilo principal y se pasan
 * en el acto al {@link Executor} del publicador (regla #3).
 */
@Singleton
public class RtdbPresenceBackend implements PresenceBackend {

    private static final String TAG = "RtdbPresenceBackend";

    private final FirebaseAuth auth;
    private final FirebaseDatabase db;

    @Inject
    public RtdbPresenceBackend(FirebaseAuth auth, FirebaseDatabase db) {
        this.auth = auth;
        this.db = db;
    }

    @Override
    public void observe(@NonNull Executor executor, @NonNull Listener listener) {
        auth.addAuthStateListener(a -> {
            FirebaseUser user = a.getCurrentUser();
            String uid = user == null ? null : user.getUid();
            executor.execute(() -> listener.onUidChanged(uid));
        });
        db.getReference(".info/connected").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean connected = Boolean.TRUE.equals(snapshot.getValue(Boolean.class));
                executor.execute(() -> listener.onConnectedChanged(connected));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, ".info/connected cancelled", error.toException());
            }
        });
        db.getReference(".info/serverTimeOffset").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Object value = snapshot.getValue();
                if (value instanceof Number) {
                    long offset = ((Number) value).longValue();
                    executor.execute(() -> listener.onServerTimeOffset(offset));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, ".info/serverTimeOffset cancelled", error.toException());
            }
        });
    }

    @NonNull
    @Override
    public Object serverTimestamp() {
        return ServerValue.TIMESTAMP;
    }

    @Override
    public void registerOnDisconnect(@NonNull String uid, @NonNull Map<String, Object> value,
                                     @NonNull Executor executor, @NonNull Callback callback) {
        status(uid).onDisconnect().setValue(value)
                .addOnSuccessListener(executor, unused -> callback.onSuccess())
                .addOnFailureListener(executor, callback::onError);
    }

    @Override
    public void cancelOnDisconnect(@NonNull String uid, @NonNull Executor executor, @NonNull Callback callback) {
        status(uid).onDisconnect().cancel()
                .addOnSuccessListener(executor, unused -> callback.onSuccess())
                .addOnFailureListener(executor, callback::onError);
    }

    @Override
    public void setStatus(@NonNull String uid, @NonNull Map<String, Object> value, @NonNull Executor executor,
                          @NonNull Callback callback) {
        status(uid).setValue(value)
                .addOnSuccessListener(executor, unused -> callback.onSuccess())
                .addOnFailureListener(executor, callback::onError);
    }

    @NonNull
    private DatabaseReference status(@NonNull String uid) {
        return db.getReference(PresenceWrites.STATUS).child(uid);
    }
}
