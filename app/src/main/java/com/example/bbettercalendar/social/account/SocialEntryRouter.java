package com.example.bbettercalendar.social.account;

import androidx.annotation.Nullable;

/**
 * Decide el siguiente paso al tocar un bloque social (F1-u2). Puro: sin Context ni Android.
 * El año se recalcula en cada llamada, así que cruzar el umbral con el tiempo se nota solo.
 */
public final class SocialEntryRouter {

    public enum Step {
        AGE_GATE,
        UNDERAGE,
        JOIN,
        CREATE_PROFILE,
        ACCEPT_TERMS,
        DESTINATION
    }

    private SocialEntryRouter() { }

    /**
     * @param birthYear           año guardado, o null si aún no se ha preguntado
     * @param currentYear         año actual
     * @param state               sesión
     * @param profileTermsVersion termsVersion aceptada en el perfil, o null si no hay perfil
     * @param currentTermsVersion versión vigente de los términos
     */
    public static Step route(@Nullable Integer birthYear, int currentYear,
                             AccountState state,
                             @Nullable Integer profileTermsVersion, int currentTermsVersion) {
        if (birthYear == null) return Step.AGE_GATE;
        if (!AgeGate.isAllowed(birthYear, currentYear)) return Step.UNDERAGE;
        if (state != AccountState.SIGNED_IN) return Step.JOIN;
        if (profileTermsVersion == null) return Step.CREATE_PROFILE;
        if (profileTermsVersion < currentTermsVersion) return Step.ACCEPT_TERMS;
        return Step.DESTINATION;
    }
}
