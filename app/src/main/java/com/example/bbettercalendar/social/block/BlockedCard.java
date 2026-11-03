package com.example.bbettercalendar.social.block;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;

/** Una persona bloqueada (L-u9): {@code blocks/{me}/blocked/{uid}}. Inmutable. */
public final class BlockedCard {
    @NonNull
    public final String uid;
    @NonNull
    public final String handle;
    @NonNull
    public final String displayName;
    /** Índice 0-7 de la paleta de avatares (como {@code FriendCard.avatarColor}). */
    public final int avatarColor;
    /** Cuándo se bloqueó; null mientras la hora del servidor está pendiente. */
    @Nullable
    public final Date since;

    public BlockedCard(@NonNull String uid, @NonNull String handle, @NonNull String displayName, int avatarColor,
                       @Nullable Date since) {
        this.uid = uid;
        this.handle = handle;
        this.displayName = displayName;
        this.avatarColor = avatarColor;
        this.since = since;
    }
}
