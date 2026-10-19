package com.example.bbettercalendar.social.profile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validación de {@code @usuario} y nombre visible en el cliente (F1-u3, 02 §3, 04 §6). Funciones puras.
 *
 * <ul>
 *   <li>{@code @usuario}: se recorta, se quita una {@code @} inicial y se pasa a minúsculas; luego
 *       {@code ^[a-z0-9_.]{3,20}$} (lo mismo que exige {@code firestore.rules}), sin reservados y sin
 *       palabras de {@link BannedWords} dentro.</li>
 *   <li>Nombre visible: se recorta; 1–30 caracteres (unidades UTF-16 de Java, que nunca son menos que
 *       los caracteres que cuenta {@code size()} en las reglas) y sin palabras prohibidas.</li>
 * </ul>
 * Las reglas de Firestore repiten el formato y las longitudes; los reservados y las palabras prohibidas
 * solo viven aquí (moderación de verdad en L.4).
 */
public final class HandleValidator {

    public static final int HANDLE_MIN = 3;
    public static final int HANDLE_MAX = 20;
    public static final int NAME_MIN = 1;
    public static final int NAME_MAX = 30;

    private static final Pattern HANDLE_CHARS = Pattern.compile("^[a-z0-9_.]+$");
    /** El formato completo de 02 §3, igual que en firestore.rules. */
    static final Pattern HANDLE_FORMAT = Pattern.compile("^[a-z0-9_.]{3,20}$");

    /** Marca de la app: ningún {@code @usuario} puede contenerla (suplantación). */
    private static final String BRAND = "bbetter";

    /** {@code @usuario} reservados: coincidencia exacta, tras pasar a minúsculas. */
    static final Set<String> RESERVED = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "admin", "administrator", "administrador", "root", "system", "sistema",
            "bbetter", "bbettercalendar", "soporte", "support", "suport", "ayuda", "ajuda", "help",
            "moderator", "moderador", "moderadora", "mod", "mods", "staff", "team", "equipo", "equip",
            "official", "oficial", "security", "seguridad", "seguretat", "info", "contact", "contacto",
            "null", "undefined", "anonymous", "anonimo", "anonim", "everyone", "todos", "tots")));

    public enum HandleResult {
        OK, EMPTY, TOO_SHORT, TOO_LONG, INVALID_CHARS, RESERVED, BANNED
    }

    public enum NameResult {
        OK, EMPTY, TOO_LONG, BANNED
    }

    private HandleValidator() {
    }

    /** Forma canónica del {@code @usuario}: sin espacios alrededor, sin {@code @} inicial, en minúsculas. */
    @NonNull
    public static String normalizeHandle(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        String h = raw.trim();
        if (h.startsWith("@")) {
            h = h.substring(1);
        }
        return h.toLowerCase(Locale.ROOT);
    }

    /** Normaliza con {@link #normalizeHandle} y valida; el primer fallo encontrado es el que se devuelve. */
    @NonNull
    public static HandleResult checkHandle(@Nullable String raw) {
        String h = normalizeHandle(raw);
        if (h.isEmpty()) {
            return HandleResult.EMPTY;
        }
        if (!HANDLE_CHARS.matcher(h).matches()) {
            return HandleResult.INVALID_CHARS;
        }
        if (h.length() < HANDLE_MIN) {
            return HandleResult.TOO_SHORT;
        }
        if (h.length() > HANDLE_MAX) {
            return HandleResult.TOO_LONG;
        }
        if (RESERVED.contains(h) || h.contains(BRAND)) {
            return HandleResult.RESERVED;
        }
        if (BannedWords.containsBanned(h)) {
            return HandleResult.BANNED;
        }
        // Red de seguridad: lo que pasa aquí es exactamente lo que acepta firestore.rules.
        return HANDLE_FORMAT.matcher(h).matches() ? HandleResult.OK : HandleResult.INVALID_CHARS;
    }

    public static boolean isValidHandle(@Nullable String raw) {
        return checkHandle(raw) == HandleResult.OK;
    }

    /** Forma que se guarda del nombre visible: recortado. */
    @NonNull
    public static String normalizeDisplayName(@Nullable String raw) {
        return raw == null ? "" : raw.trim();
    }

    @NonNull
    public static NameResult checkDisplayName(@Nullable String raw) {
        String n = normalizeDisplayName(raw);
        if (n.length() < NAME_MIN) {
            return NameResult.EMPTY;
        }
        if (n.length() > NAME_MAX) {
            return NameResult.TOO_LONG;
        }
        if (BannedWords.containsBanned(n)) {
            return NameResult.BANNED;
        }
        return NameResult.OK;
    }

    public static boolean isValidDisplayName(@Nullable String raw) {
        return checkDisplayName(raw) == NameResult.OK;
    }
}
