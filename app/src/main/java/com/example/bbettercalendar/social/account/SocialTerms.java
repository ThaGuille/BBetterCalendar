package com.example.bbettercalendar.social.account;

/**
 * Versión vigente de los términos de uso de la parte social (F1-u5). Se guarda en
 * {@code users/{uid}.termsVersion} al crear el perfil y {@link SocialEntryRouter} la compara para
 * pedir una nueva aceptación cuando suba. La URL de los términos vive en strings_social.xml.
 */
public final class SocialTerms {

    public static final int TERMS_VERSION = 1;

    private SocialTerms() { }
}
