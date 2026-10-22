package com.example.bbettercalendar.social.friends;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * La tarjeta de una persona en Amigos (F2-u5): uid, {@code @usuario}, nombre visible y color de avatar.
 * Sale de {@code profiles/{uid}} (amigos), de {@code handles/{h}} ({@code lookupHandle}) o de los campos
 * desnormalizados de una solicitud. Inmutable y pura.
 *
 * <p>No es {@code social/Friend}: ese es el modelo de la maqueta de F4 y no se toca hasta entonces.
 */
public final class FriendCard {

    @NonNull
    public final String uid;
    @NonNull
    public final String handle;
    @NonNull
    public final String displayName;
    /** Índice 0–7 de la paleta de avatar. */
    public final int avatarColor;

    public FriendCard(@NonNull String uid, @NonNull String handle, @NonNull String displayName, int avatarColor) {
        this.uid = uid;
        this.handle = handle;
        this.displayName = displayName;
        this.avatarColor = avatarColor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FriendCard)) {
            return false;
        }
        FriendCard c = (FriendCard) o;
        return avatarColor == c.avatarColor && uid.equals(c.uid) && handle.equals(c.handle)
                && displayName.equals(c.displayName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uid, handle, displayName, avatarColor);
    }

    @NonNull
    @Override
    public String toString() {
        return "FriendCard{" + uid + ", @" + handle + ", " + displayName + ", " + avatarColor + "}";
    }
}
