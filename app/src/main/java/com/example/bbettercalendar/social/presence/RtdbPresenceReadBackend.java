package com.example.bbettercalendar.social.presence;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.social.common.ListenerException;
import com.example.bbettercalendar.social.common.ListenerSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * {@link PresenceReadBackend} sobre la Realtime Database y Auth (F4-u4): el único sitio de la lectura de
 * {@code status/} que toca clases de Firebase. Escucha {@code .info/serverTimeOffset} una vez (es un nodo local
 * del cliente, no cuesta lecturas) y un {@code status/{uid}} por amigo mientras el repositorio lo pide.
 *
 * <p>La RTDB entrega en el hilo principal; aquí solo se reenvía al ejecutor que pide {@link ListenerSource}, y el
 * paso del snapshot a mapa se hace ya en él. Un {@code onCancelled} termina ese listener (como un error de
 * Firestore): {@code PERMISSION_DENIED} se pasa con ese nombre y lo demás como reintentable.
 */
@Singleton
public class RtdbPresenceReadBackend implements PresenceReadBackend {

    /** Raíz de la presencia (03 §5). */
    static final String STATUS = "status";
    static final String SERVER_TIME_OFFSET = ".info/serverTimeOffset";

    private final FirebaseAuth auth;
    private final FirebaseDatabase db;
    private volatile long serverTimeOffsetMs;

    @Inject
    public RtdbPresenceReadBackend(FirebaseAuth auth, FirebaseDatabase db) {
        this.auth = auth;
        this.db = db;
        db.getReference(SERVER_TIME_OFFSET).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Object value = snapshot.getValue();
                if (value instanceof Number) {
                    serverTimeOffsetMs = ((Number) value).longValue();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Nodo local: no se cancela. Sin él, el desfase se queda en lo último que se supo.
            }
        });
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

    @Override
    public long serverTimeOffsetMs() {
        return serverTimeOffsetMs;
    }

    @NonNull
    @Override
    public ListenerSource<String, Status> status() {
        return (uid, executor, events) -> {
            DatabaseReference ref = db.getReference(STATUS).child(uid);
            ValueEventListener listener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    executor.execute(() -> events.onEvent(new Status(uid, asMap(snapshot.getValue())), false, null));
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    ListenerException e = new ListenerException(codeOf(error), error.toException());
                    executor.execute(() -> events.onEvent(null, false, e));
                }
            };
            ref.addValueEventListener(listener);
            return () -> ref.removeEventListener(listener);
        };
    }

    /** El nombre de código que entiende {@code WatchedListener}: solo la denegación espera; lo demás se reintenta. */
    @NonNull
    static String codeOf(@NonNull DatabaseError error) {
        return error.getCode() == DatabaseError.PERMISSION_DENIED ? "PERMISSION_DENIED" : "UNAVAILABLE";
    }

    @Nullable
    private static Map<String, Object> asMap(@Nullable Object value) {
        if (!(value instanceof Map)) {
            return null;
        }
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
            if (e.getKey() instanceof String) {
                out.put((String) e.getKey(), e.getValue());
            }
        }
        return out;
    }
}
