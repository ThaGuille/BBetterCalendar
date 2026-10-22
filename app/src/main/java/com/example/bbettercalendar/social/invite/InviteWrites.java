package com.example.bbettercalendar.social.invite;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Los mapas de {@code invites/{code}} y de {@code users/{uid}.inviteCode} (F2-u6, D-028 §3), con exactamente
 * las claves que aceptan las reglas de F2-u3 ({@code validInviteCreate}, {@code validInviteUpdate} y
 * {@code validInviteCodeChange}); {@code InviteWritesTest} lo fija. Puro: solo construye mapas y rutas.
 *
 * <ul>
 *   <li>Crear: {@code invites/{code}} = {@code uid}, {@code handle} (el del perfil final) y {@code createdAt}
 *       del servidor, en el mismo lote que {@code users/{uid}.inviteCode = code}.</li>
 *   <li>Renombrar ({@code ProfileRepository.rename}): solo {@code handle}, en el lote del renombrado.</li>
 * </ul>
 */
public final class InviteWrites {

    public static final String INVITES = "invites";
    // invites/{code}
    public static final String UID = "uid";
    public static final String HANDLE = "handle";
    public static final String CREATED_AT = "createdAt";
    // users/{uid}
    public static final String INVITE_CODE = "inviteCode";

    private InviteWrites() {
    }

    @NonNull
    public static String invitePath(@NonNull String code) {
        return INVITES + "/" + code;
    }

    /** {@code invites/{code}} al crearlo. */
    @NonNull
    public static Map<String, Object> inviteCreate(@NonNull String uid, @NonNull String handle) {
        Map<String, Object> m = new HashMap<>();
        m.put(UID, uid);
        m.put(HANDLE, handle);
        m.put(CREATED_AT, FieldValue.serverTimestamp());
        return m;
    }

    /** {@code users/{uid}} (update) al estrenar código. */
    @NonNull
    public static Map<String, Object> userInviteCode(@NonNull String code) {
        Map<String, Object> m = new HashMap<>();
        m.put(INVITE_CODE, code);
        return m;
    }

    /** {@code invites/{code}} (update) al renombrar: el {@code @usuario} nuevo del perfil. */
    @NonNull
    public static Map<String, Object> inviteHandleUpdate(@NonNull String newHandle) {
        Map<String, Object> m = new HashMap<>();
        m.put(HANDLE, newHandle);
        return m;
    }
}
