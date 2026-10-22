package com.example.bbettercalendar.social.friends;

import androidx.annotation.NonNull;

/**
 * Fallos de {@link FriendsRepository} y {@link ReportRepository} que se detectan en el cliente antes de
 * escribir (F2-u5). Los de Firestore y Functions llegan como {@code ListenerException} con su código.
 */
public final class FriendsException extends Exception {

    public enum Code {
        /** Sin sesión. */
        NOT_SIGNED_IN,
        /** Con sesión pero sin {@code profiles/{uid}} propio: no hay tarjeta con la que enviar. */
        NO_PROFILE,
        /** Ya hay 20 solicitudes enviadas hoy (día UTC; {@link RequestQuota#DAILY_LIMIT}). */
        DAILY_LIMIT,
        /** Ya tienes 50 amigos (D-015; {@link FriendsRepository#MAX_FRIENDS}). */
        FRIEND_CAP
    }

    @NonNull
    public final Code code;

    public FriendsException(@NonNull Code code) {
        super(code.name());
        this.code = code;
    }
}
