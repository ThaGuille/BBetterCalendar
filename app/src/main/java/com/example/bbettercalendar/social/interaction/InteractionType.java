package com.example.bbettercalendar.social.interaction;

import androidx.annotation.NonNull;

/** Lo que se le puede enviar a un amigo (F5; 02 §8): un ánimo o un empujón, ambos predefinidos. */
public enum InteractionType {
    CHEER("cheer", "sendCheer"),
    NUDGE("nudge", "sendNudge");

    /** El tipo en el contrato de la bandeja ({@code users/{uid}/inbox/{id}.type}). */
    @NonNull
    public final String wire;
    /** La callable de Functions que lo envía. */
    @NonNull
    public final String callable;

    InteractionType(@NonNull String wire, @NonNull String callable) {
        this.wire = wire;
        this.callable = callable;
    }
}
