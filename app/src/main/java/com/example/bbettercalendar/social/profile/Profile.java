package com.example.bbettercalendar.social.profile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * El perfil propio, tal como está en {@code profiles/{uid}} (F1-u3, 02 §3, 03 §5). Inmutable y puro.
 *
 * <p>{@code handleChangedAt} es null hasta el primer renombrado (D-022): ese primero es libre y desde
 * él rige la espera de 30 días (D-019), que también comprueban las reglas en el servidor.
 */
public final class Profile {

    /** D-019: un renombrado cada 30 días como mucho. */
    public static final long RENAME_WINDOW_MS = TimeUnit.DAYS.toMillis(30);

    @NonNull
    public final String uid;
    @NonNull
    public final String displayName;
    @NonNull
    public final String handle;
    /** Índice 0–7 de la paleta de avatar. */
    public final int avatarColor;
    /** null mientras el timestamp del servidor está pendiente. */
    @Nullable
    public final Date createdAt;
    /** null si nunca se ha renombrado (D-022). */
    @Nullable
    public final Date handleChangedAt;

    public Profile(@NonNull String uid, @NonNull String displayName, @NonNull String handle,
                   int avatarColor, @Nullable Date createdAt, @Nullable Date handleChangedAt) {
        this.uid = uid;
        this.displayName = displayName;
        this.handle = handle;
        this.avatarColor = avatarColor;
        this.createdAt = createdAt;
        this.handleChangedAt = handleChangedAt;
    }

    /**
     * Desde cuándo se puede renombrar: {@code handleChangedAt + 30 días}, o {@code now} si nunca se ha
     * renombrado (D-022). Si el resultado es posterior a {@code now}, la UI muestra la fecha.
     */
    @NonNull
    public Date canRenameAt(@NonNull Date now) {
        return handleChangedAt == null
                ? now
                : new Date(handleChangedAt.getTime() + RENAME_WINDOW_MS);
    }

    public boolean canRenameNow(@NonNull Date now) {
        return !canRenameAt(now).after(now);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Profile)) {
            return false;
        }
        Profile p = (Profile) o;
        return avatarColor == p.avatarColor
                && uid.equals(p.uid)
                && displayName.equals(p.displayName)
                && handle.equals(p.handle)
                && Objects.equals(createdAt, p.createdAt)
                && Objects.equals(handleChangedAt, p.handleChangedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uid, displayName, handle, avatarColor, createdAt, handleChangedAt);
    }
}
