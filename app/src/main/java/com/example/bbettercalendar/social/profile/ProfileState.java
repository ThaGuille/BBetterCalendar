package com.example.bbettercalendar.social.profile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Lo que publica {@link ProfileRepository#observeMine()} (F1-u3). Distingue «aún cargando» de «no hay
 * perfil», para que el router no mande a Crear perfil antes de que responda el servidor.
 */
public final class ProfileState {

    public enum Status {
        /** Sin sesión. */
        SIGNED_OUT,
        /** Con sesión; esperando la primera respuesta del servidor. */
        LOADING,
        /** Con sesión y sin {@code profiles/{uid}}: toca Crear perfil. */
        NONE,
        /** Con sesión y perfil: {@link #profile} no es null. */
        READY,
        /** El listener falló; {@link #error} no es null. */
        ERROR
    }

    private static final ProfileState SIGNED_OUT = new ProfileState(Status.SIGNED_OUT, null, null);
    private static final ProfileState LOADING = new ProfileState(Status.LOADING, null, null);
    private static final ProfileState NONE = new ProfileState(Status.NONE, null, null);

    @NonNull
    public final Status status;
    @Nullable
    public final Profile profile;
    @Nullable
    public final Throwable error;

    private ProfileState(@NonNull Status status, @Nullable Profile profile, @Nullable Throwable error) {
        this.status = status;
        this.profile = profile;
        this.error = error;
    }

    public static ProfileState signedOut() {
        return SIGNED_OUT;
    }

    public static ProfileState loading() {
        return LOADING;
    }

    public static ProfileState none() {
        return NONE;
    }

    public static ProfileState ready(@NonNull Profile profile) {
        return new ProfileState(Status.READY, profile, null);
    }

    public static ProfileState error(@NonNull Throwable error) {
        return new ProfileState(Status.ERROR, null, error);
    }
}
