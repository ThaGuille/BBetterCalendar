package com.example.bbettercalendar.social.invite;

/**
 * Caducidad de una invitación pendiente (F2-u6): el código que dejan {@code InviteLinkActivity} (App Link) o
 * {@link InstallReferrerReader} (Play) en {@code SocialPrefs} vale 7 días desde que se guardó. Puro.
 */
public final class PendingInvite {

    public static final long TTL_MS = 7L * 24 * 60 * 60 * 1000;

    private PendingInvite() {
    }

    /**
     * true si un código guardado en {@code savedAtMs} sigue vigente en {@code nowMs}. Una fecha futura (el
     * reloj del móvil se movió hacia atrás) no se fía: caduca.
     */
    public static boolean isFresh(long savedAtMs, long nowMs) {
        return savedAtMs > 0 && savedAtMs <= nowMs && nowMs - savedAtMs < TTL_MS;
    }
}
