package com.example.bbettercalendar.social.friends;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Lo que publican {@code FriendsRepository.observeFriends()} ({@code FriendsState<FriendCard>}),
 * {@code observeIncoming()} y {@code observeOutgoing()} ({@code FriendsState<FriendRequest>}) (F2-u5).
 * Distingue «aún cargando» de «lista vacía», como {@code ProfileState}. Inmutable.
 */
public final class FriendsState<T> {

    public enum Status {
        /** Sin sesión. */
        SIGNED_OUT,
        /** Con sesión; esperando la primera respuesta del servidor. */
        LOADING,
        /** {@link #items} es la lista ya ordenada (puede estar vacía). */
        READY,
        /** El listener falló; {@link #error} no es null. */
        ERROR
    }

    @NonNull
    public final Status status;
    /** Vacía salvo en {@code READY}. */
    @NonNull
    public final List<T> items;
    @Nullable
    public final Throwable error;

    private FriendsState(@NonNull Status status, @NonNull List<T> items, @Nullable Throwable error) {
        this.status = status;
        this.items = items;
        this.error = error;
    }

    @NonNull
    public static <T> FriendsState<T> signedOut() {
        return new FriendsState<>(Status.SIGNED_OUT, Collections.<T>emptyList(), null);
    }

    @NonNull
    public static <T> FriendsState<T> loading() {
        return new FriendsState<>(Status.LOADING, Collections.<T>emptyList(), null);
    }

    @NonNull
    public static <T> FriendsState<T> ready(@NonNull List<T> items) {
        return new FriendsState<>(Status.READY, Collections.unmodifiableList(items), null);
    }

    @NonNull
    public static <T> FriendsState<T> error(@NonNull Throwable error) {
        return new FriendsState<>(Status.ERROR, Collections.<T>emptyList(), error);
    }
}
