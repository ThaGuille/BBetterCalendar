package com.example.bbettercalendar.social.friends;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.social.profile.ProfileWrites;
import com.google.firebase.Timestamp;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * De documentos ({@link FriendsBackend.Doc}: id + campos) a modelos, y su orden en el cliente (F2-u5). Puro:
 * las consultas no usan índices compuestos ni {@code orderBy} (03 §5), así que se ordena aquí.
 *
 * <ul>
 *   <li>Amigos: alfabético por nombre visible (sin distinguir mayúsculas ni acentos), luego por
 *       {@code @usuario}. «Primero quien estudia» necesita la presencia de F4.</li>
 *   <li>Solicitudes: la más reciente primero (una con hora aún pendiente cuenta como la más nueva), luego
 *       por id.</li>
 * </ul>
 * Un documento malformado (faltan campos o tienen otro tipo) se descarta: devuelve null.
 */
public final class FriendsMapping {

    private FriendsMapping() {
    }

    // ---- documentos → modelos ----------------------------------------------------------------------------

    /** {@code profiles/{uid}} → tarjeta; null si no existe o le faltan campos. */
    @Nullable
    public static FriendCard profileCard(@NonNull FriendsBackend.Doc doc) {
        Map<String, Object> d = doc.data;
        if (d == null) {
            return null;
        }
        return card(doc.id, d.get(ProfileWrites.HANDLE), d.get(ProfileWrites.DISPLAY_NAME),
                d.get(ProfileWrites.AVATAR_COLOR));
    }

    /** {@code handles/{h}} → tarjeta (el id del documento es el {@code @usuario}); null si no existe. */
    @Nullable
    public static FriendCard handleCard(@NonNull FriendsBackend.Doc doc) {
        Map<String, Object> d = doc.data;
        if (d == null || !(d.get(ProfileWrites.UID) instanceof String)) {
            return null;
        }
        return card((String) d.get(ProfileWrites.UID), doc.id, d.get(ProfileWrites.DISPLAY_NAME),
                d.get(ProfileWrites.AVATAR_COLOR));
    }

    /** {@code friendRequests/{from_to}} → solicitud; null si no existe o le faltan campos. */
    @Nullable
    public static FriendRequest request(@NonNull FriendsBackend.Doc doc) {
        Map<String, Object> d = doc.data;
        if (d == null || !(d.get(FriendRequestWrites.FROM) instanceof String)
                || !(d.get(FriendRequestWrites.TO) instanceof String)) {
            return null;
        }
        FriendCard from = card((String) d.get(FriendRequestWrites.FROM), d.get(FriendRequestWrites.FROM_HANDLE),
                d.get(FriendRequestWrites.FROM_NAME), d.get(FriendRequestWrites.FROM_COLOR));
        FriendCard to = card((String) d.get(FriendRequestWrites.TO), d.get(FriendRequestWrites.TO_HANDLE),
                d.get(FriendRequestWrites.TO_NAME), d.get(FriendRequestWrites.TO_COLOR));
        if (from == null || to == null) {
            return null;
        }
        Object reason = d.get(FriendRequestWrites.FAIL_REASON);
        Object status = d.get(FriendRequestWrites.STATUS);
        return new FriendRequest(doc.id, from, to,
                FriendRequest.Status.fromWire(status instanceof String ? (String) status : null),
                reason instanceof String ? (String) reason : null,
                date(d.get(FriendRequestWrites.CREATED_AT)));
    }

    /** Los uids de {@code users/{uid}/friends} (el id de cada documento es el uid del amigo), sin repetir. */
    @NonNull
    public static List<String> friendIds(@NonNull List<FriendsBackend.Doc> docs) {
        List<String> ids = new ArrayList<>();
        for (FriendsBackend.Doc doc : docs) {
            if (!ids.contains(doc.id)) {
                ids.add(doc.id);
            }
        }
        Collections.sort(ids);
        return ids;
    }

    /** Recibidas que se enseñan: {@code to == me} en {@code pending} o {@code failed}, la más nueva primero. */
    @NonNull
    public static List<FriendRequest> incoming(@NonNull List<FriendsBackend.Doc> docs, @NonNull String me) {
        List<FriendRequest> out = new ArrayList<>();
        for (FriendsBackend.Doc doc : docs) {
            FriendRequest r = request(doc);
            if (r != null && me.equals(r.to.uid)
                    && (r.status == FriendRequest.Status.PENDING || r.status == FriendRequest.Status.FAILED)) {
                out.add(r);
            }
        }
        sortRequests(out);
        return out;
    }

    /** Enviadas que se enseñan: {@code from == me}, solo {@code pending}, la más nueva primero. */
    @NonNull
    public static List<FriendRequest> outgoing(@NonNull List<FriendsBackend.Doc> docs, @NonNull String me) {
        List<FriendRequest> out = new ArrayList<>();
        for (FriendsBackend.Doc doc : docs) {
            FriendRequest r = request(doc);
            if (r != null && me.equals(r.from.uid) && r.status == FriendRequest.Status.PENDING) {
                out.add(r);
            }
        }
        sortRequests(out);
        return out;
    }

    /** Cuántas recibidas están en {@code pending} (el punto de la campana). */
    public static int pendingCount(@NonNull List<FriendRequest> incoming) {
        int n = 0;
        for (FriendRequest r : incoming) {
            if (r.status == FriendRequest.Status.PENDING) {
                n++;
            }
        }
        return n;
    }

    // ---- orden -------------------------------------------------------------------------------------------

    /** Amigos en orden alfabético (nombre sin mayúsculas ni acentos; empate por {@code @usuario} y uid). */
    @NonNull
    public static List<FriendCard> sortFriends(@NonNull Collection<FriendCard> cards) {
        Collator collator = Collator.getInstance(Locale.ROOT);
        collator.setStrength(Collator.PRIMARY);
        List<FriendCard> out = new ArrayList<>(cards);
        Collections.sort(out, (a, b) -> {
            int c = collator.compare(a.displayName, b.displayName);
            if (c != 0) {
                return c;
            }
            c = a.handle.compareTo(b.handle);
            return c != 0 ? c : a.uid.compareTo(b.uid);
        });
        return out;
    }

    static void sortRequests(@NonNull List<FriendRequest> requests) {
        Collections.sort(requests, NEWEST_FIRST);
    }

    private static final Comparator<FriendRequest> NEWEST_FIRST = (a, b) -> {
        long ta = a.createdAt == null ? Long.MAX_VALUE : a.createdAt.getTime();
        long tb = b.createdAt == null ? Long.MAX_VALUE : b.createdAt.getTime();
        if (ta != tb) {
            return ta > tb ? -1 : 1;
        }
        return a.id.compareTo(b.id);
    };

    // ---- interno -----------------------------------------------------------------------------------------

    @Nullable
    private static FriendCard card(@Nullable String uid, @Nullable Object handle, @Nullable Object name,
                                   @Nullable Object color) {
        if (uid == null || !(handle instanceof String) || !(name instanceof String) || !(color instanceof Number)) {
            return null;
        }
        return new FriendCard(uid, (String) handle, (String) name, ((Number) color).intValue());
    }

    @Nullable
    private static Date date(@Nullable Object value) {
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toDate();
        }
        return value instanceof Date ? (Date) value : null;
    }
}
