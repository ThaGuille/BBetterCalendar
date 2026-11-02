package com.example.bbettercalendar.social.inbox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Lo que publica {@code InboxRepository.observeInbox()} (F5-u5). Como {@code FriendsState}: distingue «aún
 * cargando» de «bandeja vacía». Inmutable.
 */
public final class InboxState {

    public enum Status {
        /** Sin sesión: no hay listener. */
        SIGNED_OUT,
        /** Con sesión; esperando la primera respuesta del servidor. */
        LOADING,
        /** {@link #items} es la bandeja, la más nueva primero (puede estar vacía). */
        READY,
        /** El listener falló; {@link #error} no es null. */
        ERROR
    }

    @NonNull
    public final Status status;
    /** Vacía salvo en {@code READY}. */
    @NonNull
    public final List<InboxItem> items;
    @Nullable
    public final Throwable error;

    private InboxState(@NonNull Status status, @NonNull List<InboxItem> items, @Nullable Throwable error) {
        this.status = status;
        this.items = items;
        this.error = error;
    }

    @NonNull
    public static InboxState signedOut() {
        return new InboxState(Status.SIGNED_OUT, Collections.<InboxItem>emptyList(), null);
    }

    @NonNull
    public static InboxState loading() {
        return new InboxState(Status.LOADING, Collections.<InboxItem>emptyList(), null);
    }

    @NonNull
    public static InboxState ready(@NonNull List<InboxItem> items) {
        return new InboxState(Status.READY, Collections.unmodifiableList(items), null);
    }

    @NonNull
    public static InboxState error(@NonNull Throwable error) {
        return new InboxState(Status.ERROR, Collections.<InboxItem>emptyList(), error);
    }

    /** Cuántos avisos sin leer hay (0 si no está {@code READY}). */
    public int unread() {
        int n = 0;
        for (InboxItem item : items) {
            if (!item.read) {
                n++;
            }
        }
        return n;
    }
}
