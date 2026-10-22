package com.example.bbettercalendar.social.invite;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Códigos de invitación (F2-u6, D-028 §3): 8 caracteres de un alfabeto sin {@code I}, {@code L}, {@code O},
 * {@code 0} ni {@code 1}, que se confunden al leerlos o dictarlos. Es el mismo formato que exigen las reglas
 * de {@code invites/{code}} ({@code ^[A-HJKMNP-Z2-9]{8}$}, F2-u3).
 *
 * <p>Puro (sin Android): genera, normaliza a mayúsculas y saca el código de la ruta de un App Link
 * ({@code /i/<código>}) o del referrer de Play ({@code invite=<código>}).
 */
public final class InviteCodes {

    /** 31 símbolos: A-H, J, K, M, N, P-Z y 2-9. */
    public static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    public static final int LENGTH = 8;
    /** La ruta de los enlaces: {@code https://bbetter-dbcda.web.app/i/<código>}. */
    public static final String PATH_PREFIX = "/i/";
    /** La clave del referrer de Play: {@code referrer=invite%3D<código>} llega como {@code invite=<código>}. */
    public static final String REFERRER_KEY = "invite";

    private static final Pattern VALID = Pattern.compile("^[A-HJKMNP-Z2-9]{8}$");
    /** {@code /i/<código>} con una barra final opcional; nada más en la ruta. */
    private static final Pattern LINK_PATH = Pattern.compile("^/i/([^/]+)/?$");

    private InviteCodes() {
    }

    /** Un código nuevo al azar (31^8 ≈ 8,5·10^11 combinaciones). */
    @NonNull
    public static String generate(@NonNull SecureRandom random) {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** true si {@code code} ya está normalizado y tiene el formato de las reglas. */
    public static boolean isValid(@Nullable String code) {
        return code != null && VALID.matcher(code).matches();
    }

    /** Recorta y pasa a mayúsculas; devuelve el código si es válido, o null. */
    @Nullable
    public static String normalize(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String code = raw.trim().toUpperCase(Locale.ROOT);
        return isValid(code) ? code : null;
    }

    /**
     * El código de la ruta de un enlace ({@code Uri.getPath()}): {@code /i/abcd2345} → {@code ABCD2345}.
     * Null si la ruta no es {@code /i/<código>} o el código no es válido.
     */
    @Nullable
    public static String fromLink(@Nullable String path) {
        if (path == null) {
            return null;
        }
        Matcher m = LINK_PATH.matcher(path.trim());
        return m.matches() ? normalize(m.group(1)) : null;
    }

    /**
     * El código del referrer de Install Referrer. Play entrega el parámetro {@code referrer} ya decodificado
     * una vez ({@code invite=ABCD2345}, quizá junto a otros pares {@code utm_*=…} separados por {@code &});
     * si llega sin decodificar ({@code invite%3DABCD2345}), se decodifica primero. Null si no hay invitación.
     */
    @Nullable
    public static String fromReferrer(@Nullable String referrer) {
        if (referrer == null) {
            return null;
        }
        String r = referrer.trim();
        if (r.indexOf('=') < 0 && r.indexOf('%') >= 0) {
            r = decode(r);
        }
        for (String pair : r.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = decode(pair.substring(0, eq)).trim();
            if (REFERRER_KEY.equalsIgnoreCase(key)) {
                String code = normalize(decode(pair.substring(eq + 1)));
                if (code != null) {
                    return code;
                }
            }
        }
        return null;
    }

    @NonNull
    private static String decode(@NonNull String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return s; // UTF-8 siempre existe; un % suelto deja el texto tal cual
        }
    }
}
