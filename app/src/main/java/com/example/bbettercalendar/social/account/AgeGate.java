package com.example.bbettercalendar.social.account;

/**
 * Puerta de edad (D-010, D-024). Funciones puras, sin Context.
 *
 * <p>Solo se conoce el año de nacimiento, así que la edad se toma por el lado prudente:
 * {@code edad = añoActual - añoNacimiento - 1} (nunca sobreestima la edad).
 */
public final class AgeGate {

    // Futuro: mover el umbral a Remote Config para poder cambiarlo sin publicar versión.
    // Remote Config no está montado todavía; hasta entonces es una constante (04 §1).
    public static final int SOCIAL_MIN_AGE = 14;

    /** Desde esta edad la banda es 18+; entre el mínimo y esta, 14-17. */
    public static final int ADULT_AGE = 18;

    public static final String BAND_TEEN = "14-17";
    public static final String BAND_ADULT = "18+";

    private AgeGate() { }

    /** Edad prudente a partir del año de nacimiento. */
    public static int age(int birthYear, int currentYear) {
        return currentYear - birthYear - 1;
    }

    public static boolean isAllowed(int birthYear, int currentYear) {
        return age(birthYear, currentYear) >= SOCIAL_MIN_AGE;
    }

    /** {@code "14-17"} o {@code "18+"}; mismo cálculo que {@link #isAllowed}. */
    public static String ageBand(int birthYear, int currentYear) {
        return age(birthYear, currentYear) >= ADULT_AGE ? BAND_ADULT : BAND_TEEN;
    }
}
