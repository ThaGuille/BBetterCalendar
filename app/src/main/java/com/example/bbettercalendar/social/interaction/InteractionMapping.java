package com.example.bbettercalendar.social.interaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

/**
 * De la respuesta de {@code sendCheer} / {@code sendNudge} a {@link InteractionResult} (F5-u5; 03 §7). Puro.
 * {@code {status: 'sent'}} → SENT, {@code 'already'} → ALREADY_SENT, {@code 'studied-today'} →
 * STUDIED_TODAY, {@code 'not-friends'} → NOT_FRIENDS y cualquier otra cosa → ERROR. Los dos últimos suelen
 * llegar como mensaje de un error ({@code failed-precondition} / {@code permission-denied}); se aceptan en
 * los dos sitios.
 */
public final class InteractionMapping {

    static final String STATUS = "status";

    private InteractionMapping() {
    }

    @NonNull
    public static InteractionResult fromData(@Nullable Map<String, Object> data) {
        Object status = data == null ? null : data.get(STATUS);
        return fromKeyword(status instanceof String ? (String) status : null);
    }

    @NonNull
    public static InteractionResult fromFailure(@NonNull InteractionBackend.Failure failure) {
        InteractionResult r = fromKeyword(failure.serverMessage);
        // Un error nunca es un envío: «sent» o «already» como mensaje de error no cuentan.
        return r == InteractionResult.SENT || r == InteractionResult.ALREADY_SENT ? InteractionResult.ERROR : r;
    }

    @NonNull
    private static InteractionResult fromKeyword(@Nullable String word) {
        if (word == null) {
            return InteractionResult.ERROR;
        }
        switch (word) {
            case "sent":
                return InteractionResult.SENT;
            case "already":
                return InteractionResult.ALREADY_SENT;
            case "studied-today":
                return InteractionResult.STUDIED_TODAY;
            case "not-friends":
                return InteractionResult.NOT_FRIENDS;
            default:
                return InteractionResult.ERROR;
        }
    }
}
