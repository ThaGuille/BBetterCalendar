package com.example.bbettercalendar.social.invite;

import androidx.annotation.Nullable;

/**
 * Decide que hace el App Link de invitacion (L-u6). Puro, sin Android: {@code InviteLinkActivity} le pasa
 * las partes de la URI. La actividad esta en el manifest de release, asi que sin FEATURE_SOCIAL no guarda
 * nada y solo abre el launcher.
 */
public final class InviteLinkRouting {

    private InviteLinkRouting() {
    }

    /**
     * El codigo a guardar como invitacion pendiente, o null si no hay que guardar nada: social apagado,
     * esquema o host que no son los del enlace, o codigo mal formado.
     */
    @Nullable
    public static String codeToStore(boolean socialEnabled, @Nullable String scheme, @Nullable String host,
                                     @Nullable String path) {
        if (!socialEnabled || !InviteLinkFactory.SCHEME.equals(scheme) || host == null
                || !InviteLinkFactory.HOST.equalsIgnoreCase(host)) {
            return null;
        }
        return InviteCodes.fromLink(path);
    }
}
