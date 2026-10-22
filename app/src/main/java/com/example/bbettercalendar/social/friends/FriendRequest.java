package com.example.bbettercalendar.social.friends;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;
import java.util.Objects;

/**
 * Una solicitud de amistad, tal como está en {@code friendRequests/{from_to}} (F2-u5, D-028 §1). Lleva las
 * dos tarjetas desnormalizadas porque el destinatario no puede leer el perfil del remitente. Inmutable.
 *
 * <p>No hay estado {@code declined}: rechazar o cancelar borra la solicitud. {@code failed} (con
 * {@link #failReason} {@code cap} o {@code gone}) solo lo escribe la Function {@code onFriendRequestAccepted}.
 */
public final class FriendRequest {

    public enum Status {
        PENDING("pending"), ACCEPTED("accepted"), FAILED("failed"), UNKNOWN("");

        @NonNull
        public final String wire;

        Status(@NonNull String wire) {
            this.wire = wire;
        }

        @NonNull
        static Status fromWire(@Nullable String s) {
            for (Status st : values()) {
                if (st != UNKNOWN && st.wire.equals(s)) {
                    return st;
                }
            }
            return UNKNOWN;
        }
    }

    /** Motivos de {@code failed} que escribe la Function. */
    public static final String FAIL_CAP = "cap";
    public static final String FAIL_GONE = "gone";

    @NonNull
    public final String id;
    @NonNull
    public final FriendCard from;
    @NonNull
    public final FriendCard to;
    @NonNull
    public final Status status;
    /** {@link #FAIL_CAP}, {@link #FAIL_GONE} u otro; null salvo en {@code failed}. */
    @Nullable
    public final String failReason;
    /** null mientras el timestamp del servidor está pendiente (se estima) o si falta. */
    @Nullable
    public final Date createdAt;

    public FriendRequest(@NonNull String id, @NonNull FriendCard from, @NonNull FriendCard to,
                         @NonNull Status status, @Nullable String failReason, @Nullable Date createdAt) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.status = status;
        this.failReason = failReason;
        this.createdAt = createdAt;
    }

    /** La otra persona de la solicitud vista desde {@code myUid}. */
    @NonNull
    public FriendCard other(@NonNull String myUid) {
        return myUid.equals(from.uid) ? to : from;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FriendRequest)) {
            return false;
        }
        FriendRequest r = (FriendRequest) o;
        return id.equals(r.id) && from.equals(r.from) && to.equals(r.to) && status == r.status
                && Objects.equals(failReason, r.failReason) && Objects.equals(createdAt, r.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, from, to, status, failReason, createdAt);
    }

    @NonNull
    @Override
    public String toString() {
        return "FriendRequest{" + id + ", " + status + (failReason == null ? "" : "/" + failReason) + "}";
    }
}
