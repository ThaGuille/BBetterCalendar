package com.example.bbettercalendar.social.profile;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Los mapas de campos que escribe {@link ProfileRepository} (F1-u3). Cada uno tiene exactamente las
 * claves que acepta {@code firebase/firestore.rules} (F1-u1); {@code ProfileWritesTest} lo fija.
 *
 * <ul>
 *   <li>Los timestamps ({@code createdAt}, {@code termsAcceptedAt}, {@code handleChangedAt}) van con
 *       {@code FieldValue.serverTimestamp()}: las reglas los comparan con {@code request.time}.</li>
 *   <li>El perfil se crea sin {@code handleChangedAt} (D-022): el primer renombrado es libre.</li>
 *   <li>La tarjeta {@code handles/{h}} copia {@code displayName} y {@code avatarColor} del perfil final,
 *       así que se escribe en el mismo lote que el perfil.</li>
 * </ul>
 * Puro: sin {@code Context} ni llamadas de red; solo construye mapas.
 */
public final class ProfileWrites {

    public static final String USERS = "users";
    public static final String PROFILES = "profiles";
    public static final String HANDLES = "handles";

    // users/{uid}
    public static final String CREATED_AT = "createdAt";
    public static final String AGE_BAND = "ageBand";
    public static final String TERMS_VERSION = "termsVersion";
    public static final String TERMS_ACCEPTED_AT = "termsAcceptedAt";
    // profiles/{uid}
    public static final String DISPLAY_NAME = "displayName";
    public static final String HANDLE = "handle";
    public static final String AVATAR_COLOR = "avatarColor";
    public static final String HANDLE_CHANGED_AT = "handleChangedAt";
    // handles/{h}
    public static final String UID = "uid";

    private ProfileWrites() {
    }

    /** {@code users/{uid}} al crear el perfil. */
    @NonNull
    public static Map<String, Object> userCreate(@NonNull String ageBand, int termsVersion) {
        Map<String, Object> m = new HashMap<>();
        m.put(CREATED_AT, FieldValue.serverTimestamp());
        m.put(AGE_BAND, ageBand);
        m.put(TERMS_VERSION, termsVersion);
        m.put(TERMS_ACCEPTED_AT, FieldValue.serverTimestamp());
        return m;
    }

    /** {@code users/{uid}} al volver a aceptar los términos (update: el resto de campos no cambia). */
    @NonNull
    public static Map<String, Object> termsAccept(int termsVersion) {
        Map<String, Object> m = new HashMap<>();
        m.put(TERMS_VERSION, termsVersion);
        m.put(TERMS_ACCEPTED_AT, FieldValue.serverTimestamp());
        return m;
    }

    /** {@code profiles/{uid}} al crearlo: sin {@code handleChangedAt} (D-022). */
    @NonNull
    public static Map<String, Object> profileCreate(@NonNull String displayName, @NonNull String handle,
                                                    int avatarColor) {
        Map<String, Object> m = new HashMap<>();
        m.put(DISPLAY_NAME, displayName);
        m.put(HANDLE, handle);
        m.put(AVATAR_COLOR, avatarColor);
        m.put(CREATED_AT, FieldValue.serverTimestamp());
        return m;
    }

    /** {@code profiles/{uid}} al editar nombre y color (update: ni el handle ni sus fechas cambian). */
    @NonNull
    public static Map<String, Object> profileUpdate(@NonNull String displayName, int avatarColor) {
        Map<String, Object> m = new HashMap<>();
        m.put(DISPLAY_NAME, displayName);
        m.put(AVATAR_COLOR, avatarColor);
        return m;
    }

    /** {@code profiles/{uid}} al renombrar (update, D-019): el handle nuevo y la hora del servidor. */
    @NonNull
    public static Map<String, Object> profileRename(@NonNull String newHandle) {
        Map<String, Object> m = new HashMap<>();
        m.put(HANDLE, newHandle);
        m.put(HANDLE_CHANGED_AT, FieldValue.serverTimestamp());
        return m;
    }

    /** {@code handles/{h}} al reservarlo (crear perfil o renombrar): la tarjeta pública mínima. */
    @NonNull
    public static Map<String, Object> handleCreate(@NonNull String uid, @NonNull String displayName,
                                                   int avatarColor) {
        Map<String, Object> m = new HashMap<>();
        m.put(UID, uid);
        m.put(DISPLAY_NAME, displayName);
        m.put(AVATAR_COLOR, avatarColor);
        return m;
    }

    /** {@code handles/{h}} al editar el perfil (update): solo los campos desnormalizados. */
    @NonNull
    public static Map<String, Object> handleUpdate(@NonNull String displayName, int avatarColor) {
        Map<String, Object> m = new HashMap<>();
        m.put(DISPLAY_NAME, displayName);
        m.put(AVATAR_COLOR, avatarColor);
        return m;
    }
}
