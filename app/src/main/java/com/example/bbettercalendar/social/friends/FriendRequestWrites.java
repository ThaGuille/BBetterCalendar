package com.example.bbettercalendar.social.friends;

import androidx.annotation.NonNull;

import com.example.bbettercalendar.social.profile.ProfileWrites;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Los mapas que escribe {@link FriendsRepository} en {@code friendRequests/} y en el contador de
 * {@code users/{uid}} (F2-u5). Cada uno tiene exactamente las claves que aceptan las reglas de F2-u3
 * ({@code validRequestFields}, {@code validRequestCounter}, {@code validRequestAccept});
 * {@code FriendRequestWritesTest} lo fija.
 *
 * <ul>
 *   <li>Enviar = un lote con {@link #request} (set de {@code friendRequests/{from_to}}) y {@link #counter}
 *       (update de {@code users/{from}}): las reglas exigen los dos a la vez y {@code reqLastTo == to}.</li>
 *   <li>Las tarjetas cuadran con {@code handles/fromHandle} y {@code handles/toHandle} al enviar.</li>
 *   <li>{@code createdAt} va con {@code FieldValue.serverTimestamp()}: las reglas lo comparan con
 *       {@code request.time}.</li>
 * </ul>
 * Puro: sin {@code Context} ni llamadas de red; solo construye mapas y rutas.
 */
public final class FriendRequestWrites {

    public static final String FRIEND_REQUESTS = "friendRequests";
    public static final String FRIENDS = "friends";

    // friendRequests/{from_to}
    public static final String FROM = "from";
    public static final String TO = "to";
    public static final String STATUS = "status";
    public static final String CREATED_AT = "createdAt";
    public static final String FROM_HANDLE = "fromHandle";
    public static final String FROM_NAME = "fromName";
    public static final String FROM_COLOR = "fromColor";
    public static final String TO_HANDLE = "toHandle";
    public static final String TO_NAME = "toName";
    public static final String TO_COLOR = "toColor";
    /** Solo lo escribe la Function ({@code cap} o {@code gone}). */
    public static final String FAIL_REASON = "failReason";
    // users/{uid}: contador diario (D-028 §2)
    public static final String REQ_DAY = "reqDay";
    public static final String REQ_COUNT = "reqCount";
    public static final String REQ_LAST_TO = "reqLastTo";

    private FriendRequestWrites() {
    }

    /** Id determinista de una solicitud (D-028 §1): volver a enviar crea el mismo documento. */
    @NonNull
    public static String requestId(@NonNull String from, @NonNull String to) {
        return from + "_" + to;
    }

    @NonNull
    public static String requestPath(@NonNull String id) {
        return FRIEND_REQUESTS + "/" + id;
    }

    @NonNull
    public static String userPath(@NonNull String uid) {
        return ProfileWrites.USERS + "/" + uid;
    }

    @NonNull
    public static String profilePath(@NonNull String uid) {
        return ProfileWrites.PROFILES + "/" + uid;
    }

    @NonNull
    public static String handlePath(@NonNull String handle) {
        return ProfileWrites.HANDLES + "/" + handle;
    }

    @NonNull
    public static String friendPath(@NonNull String uid, @NonNull String friendUid) {
        return userPath(uid) + "/" + FRIENDS + "/" + friendUid;
    }

    /** {@code friendRequests/{from_to}} al enviar: las 10 claves de las reglas, en {@code pending}. */
    @NonNull
    public static Map<String, Object> request(@NonNull FriendCard from, @NonNull FriendCard to) {
        Map<String, Object> m = new HashMap<>();
        m.put(FROM, from.uid);
        m.put(TO, to.uid);
        m.put(STATUS, FriendRequest.Status.PENDING.wire);
        m.put(CREATED_AT, FieldValue.serverTimestamp());
        m.put(FROM_HANDLE, from.handle);
        m.put(FROM_NAME, from.displayName);
        m.put(FROM_COLOR, from.avatarColor);
        m.put(TO_HANDLE, to.handle);
        m.put(TO_NAME, to.displayName);
        m.put(TO_COLOR, to.avatarColor);
        return m;
    }

    /** {@code users/{from}} en el mismo lote (update): el contador que calcula {@link RequestQuota}. */
    @NonNull
    public static Map<String, Object> counter(@NonNull RequestQuota.Next next, @NonNull String to) {
        Map<String, Object> m = new HashMap<>();
        m.put(REQ_DAY, next.day);
        m.put(REQ_COUNT, next.count);
        m.put(REQ_LAST_TO, to);
        return m;
    }

    /** {@code friendRequests/{id}} al aceptar (update): solo {@code status}, de {@code pending} a {@code accepted}. */
    @NonNull
    public static Map<String, Object> accept() {
        Map<String, Object> m = new HashMap<>();
        m.put(STATUS, FriendRequest.Status.ACCEPTED.wire);
        return m;
    }
}
