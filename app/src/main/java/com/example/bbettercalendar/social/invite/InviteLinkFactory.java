package com.example.bbettercalendar.social.invite;

import androidx.annotation.NonNull;

/**
 * El enlace de invitación (F2-u6): {@code https://bbetter-dbcda.web.app/i/<código>}. Es el mismo dominio y la
 * misma ruta que el intent-filter del App Link (manifiesto de debug hasta L.7) y que el rewrite {@code /i/**}
 * de {@code firebase/firebase.json}. Puro.
 */
public final class InviteLinkFactory {

    public static final String SCHEME = "https";
    /** Dominio de Hosting del proyecto; solo lo sirve el emulador hasta L.7 (D-025). */
    public static final String HOST = "bbetter-dbcda.web.app";
    public static final String BASE = SCHEME + "://" + HOST + InviteCodes.PATH_PREFIX;

    private InviteLinkFactory() {
    }

    /** El enlace de {@code code}; el código se normaliza y tiene que ser válido. */
    @NonNull
    public static String link(@NonNull String code) {
        String c = InviteCodes.normalize(code);
        if (c == null) {
            throw new IllegalArgumentException("Invalid invite code: " + code);
        }
        return BASE + c;
    }
}
