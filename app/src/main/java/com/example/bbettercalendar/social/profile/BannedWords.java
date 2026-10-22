package com.example.bbettercalendar.social.profile;

import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Lista básica de palabras prohibidas en ES, EN y CA (F1-u3, 04 §6). Se aplican como subcadena al
 * {@code @usuario} y al nombre visible; las reglas de Firestore no las miran (moderación de verdad en L.4).
 *
 * <p>Se compara contra el texto plegado: minúsculas, sin acentos (ñ → n, à → a) y sin los separadores
 * {@code . _ - *}, para que {@code f.u.c.k} o {@code Gilipóllas} también caigan. Las entradas evitan raíces
 * cortas que darían falsos positivos como subcadena: no hay {@code puta} (computadora, reputación,
 * disputa), {@code culo} (película, vehículo, cálculo), {@code polla} (pollastre), {@code cony}
 * (cónyuge), {@code cum} (document), {@code rape} (grape) ni {@code nazi} (Nazir). Los espacios se
 * conservan: quitarlos uniría palabras inocentes («Josh It» contendría {@code shit}). Funciones puras,
 * sin {@code Context}.
 */
public final class BannedWords {

    /** Ya plegadas: minúsculas ASCII, sin acentos ni separadores. */
    static final List<String> WORDS = Collections.unmodifiableList(Arrays.asList(
            // ES
            "hijoputa", "hijodeputa", "putero", "puton", "gilipollas", "soplapollas", "chupapollas",
            "cabron", "maricon", "mierda", "comemierda", "pendejo", "malparido", "follar", "follador",
            "joder", "cojones", "pajillero", "subnormal", "zorron", "violador", "sudaca", "mongolo",
            // CA
            "fillputa", "filldeputa", "fillsdeputa", "collons", "gilipolles", "malparit", "merda",
            "cagarro", "bandarra",
            // EN
            "fuck", "shit", "bitch", "bastard", "asshole", "whore", "slut", "nigger", "nigga",
            // F2-u1: la forma larga, no la raíz: en catalán «retard» es retraso y «retardat», retrasado.
            "faggot", "retarded", "wanker", "twat", "pussy", "dildo", "jizz", "porn", "rapist",
            "pedophile", "paedophile", "pedofilo",
            // Odio (todas las lenguas)
            "hitler", "nazis", "neonazi", "kkk"));

    /**
     * F2-u1: palabras inocentes que contienen una prohibida («therapist» contiene {@code rapist}). Se
     * quitan del texto plegado antes de buscar (cambiadas por un espacio, para no unir lo de los lados),
     * así que {@code therapist_rapist} sigue cayendo. Ya plegadas; las largas antes que sus prefijos.
     */
    static final List<String> EXCEPTIONS = Collections.unmodifiableList(Arrays.asList(
            "therapists", "therapist", "terapist"));

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SEPARATORS = Pattern.compile("[._*\\-]+");

    private BannedWords() {
    }

    /** Minúsculas, sin acentos y sin separadores {@code . _ - *}; los espacios se quedan. */
    static String fold(@Nullable String text) {
        if (text == null) {
            return "";
        }
        String noMarks = MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        return SEPARATORS.matcher(noMarks.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    /** true si el texto (sin sus {@link #EXCEPTIONS}) contiene, como subcadena, alguna palabra de la lista. */
    public static boolean containsBanned(@Nullable String text) {
        String folded = fold(text);
        for (String exception : EXCEPTIONS) {
            folded = folded.replace(exception, " ");
        }
        if (folded.trim().isEmpty()) {
            return false;
        }
        for (String word : WORDS) {
            if (folded.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
