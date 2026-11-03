package com.example.bbettercalendar.social.block;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/** Lo que publica {@code BlockRepository.observeBlocked()} (L-u9). Inmutable. */
public final class BlockState {

    public enum Status {
        /** Sin sesión: no hay listener. */
        SIGNED_OUT,
        /** Con sesión; esperando la primera respuesta del servidor. */
        LOADING,
        /** {@link #blocked} es la lista, el bloqueo más nuevo primero (puede estar vacía). */
        READY,
        /** El listener falló; {@link #error} no es null. */
        ERROR
    }

    @NonNull
    public final Status status;
    /** Vacía salvo en {@code READY}. */
    @NonNull
    public final List<BlockedCard> blocked;
    @Nullable
    public final Throwable error;

    private BlockState(@NonNull Status status, @NonNull List<BlockedCard> blocked, @Nullable Throwable error) {
        this.status = status;
        this.blocked = blocked;
        this.error = error;
    }

    @NonNull
    public static BlockState signedOut() {
        return new BlockState(Status.SIGNED_OUT, Collections.<BlockedCard>emptyList(), null);
    }

    @NonNull
    public static BlockState loading() {
        return new BlockState(Status.LOADING, Collections.<BlockedCard>emptyList(), null);
    }

    @NonNull
    public static BlockState ready(@NonNull List<BlockedCard> blocked) {
        return new BlockState(Status.READY, Collections.unmodifiableList(blocked), null);
    }

    @NonNull
    public static BlockState error(@NonNull Throwable error) {
        return new BlockState(Status.ERROR, Collections.<BlockedCard>emptyList(), error);
    }
}
