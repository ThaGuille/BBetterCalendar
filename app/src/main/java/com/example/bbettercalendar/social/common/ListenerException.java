package com.example.bbettercalendar.social.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Error de un {@link ListenerSource}, con el código de Firestore por nombre (F2-u1). La causa original
 * (p. ej. la {@code FirebaseFirestoreException}) va en {@link #getCause()}.
 */
public final class ListenerException extends Exception {

    @NonNull
    public final String code;

    public ListenerException(@NonNull String code, @Nullable Throwable cause) {
        super(code, cause);
        this.code = code;
    }

    /** La causa original si la hay; si no, esta misma excepción. Es lo que se publica en los estados. */
    @NonNull
    public Throwable original() {
        Throwable cause = getCause();
        return cause != null ? cause : this;
    }
}
