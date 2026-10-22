package com.example.bbettercalendar.social.friends;

import androidx.annotation.NonNull;

/**
 * Motivos predefinidos de un reporte (F2-u5, 04 §2, D-028 §4): sin texto libre. {@link #wire} es el valor
 * que aceptan las reglas en {@code reports/{reporter_reported}.reason}.
 */
public enum ReportReason {
    OFFENSIVE_NAME("offensive_name"),
    IMPERSONATION("impersonation"),
    HARASSMENT("harassment"),
    OTHER("other");

    @NonNull
    public final String wire;

    ReportReason(@NonNull String wire) {
        this.wire = wire;
    }

    /** Desde dónde se reporta ({@code reports/.context}): la lista de amigos o una solicitud recibida. */
    public enum Context {
        FRIEND("friend"),
        /** Reportar desde una solicitud también la rechaza, en el mismo lote. */
        REQUEST("request");

        @NonNull
        public final String wire;

        Context(@NonNull String wire) {
            this.wire = wire;
        }
    }
}
