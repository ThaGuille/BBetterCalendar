package com.example.bbettercalendar.ui.home;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * F1-u8: decide qué saludo y qué avatar pinta Home. Puro (sin Context ni vistas) para poder
 * probarlo en JVM; la cadena visible la elige el Fragment según {@link Greeting#kind}.
 *
 * <p>Precedencia: perfil de la cuenta, luego nombre local, luego saludo genérico sin iniciales.
 */
public final class GreetingResolver {

    /** De dónde sale el saludo. */
    public enum Kind {
        /** Nombre del perfil, con su color de avatar. */
        PROFILE,
        /** Nombre local guardado en el teléfono, con el color por defecto. */
        LOCAL,
        /** Nada que mostrar: «Hola» a secas y avatar sin iniciales. */
        GENERIC
    }

    /** Resultado: inmutable. {@code colorIndex} es 0-7 solo con {@link Kind#PROFILE}, si no -1. */
    public static final class Greeting {
        @NonNull public final Kind kind;
        /** Nombre a saludar; cadena vacía con {@link Kind#GENERIC}. */
        @NonNull public final String name;
        /** 0-2 letras en mayúscula; vacío con {@link Kind#GENERIC}. */
        @NonNull public final String initials;
        public final int colorIndex;

        Greeting(@NonNull Kind kind, @NonNull String name, @NonNull String initials, int colorIndex) {
            this.kind = kind;
            this.name = name;
            this.initials = initials;
            this.colorIndex = colorIndex;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Greeting)) {
                return false;
            }
            Greeting g = (Greeting) o;
            return kind == g.kind && colorIndex == g.colorIndex
                    && name.equals(g.name) && initials.equals(g.initials);
        }

        @Override
        public int hashCode() {
            return (((kind.hashCode() * 31) + name.hashCode()) * 31 + initials.hashCode()) * 31 + colorIndex;
        }
    }

    private static final Greeting GENERIC = new Greeting(Kind.GENERIC, "", "", -1);

    private GreetingResolver() { }

    /**
     * @param profileName nombre del perfil, o null sin cuenta / sin perfil todavía
     * @param profileColor índice 0-7 del color del perfil (se recorta); se ignora sin perfil
     * @param localName nombre local, o null si no hay
     */
    @NonNull
    public static Greeting resolve(@Nullable String profileName, int profileColor,
                                   @Nullable String localName) {
        String profile = clean(profileName);
        if (!profile.isEmpty()) {
            return new Greeting(Kind.PROFILE, profile, initialsOf(profile),
                    Math.max(0, Math.min(profileColor, 7)));
        }
        String local = clean(localName);
        if (!local.isEmpty()) {
            return new Greeting(Kind.LOCAL, local, initialsOf(local), -1);
        }
        return GENERIC;
    }

    @NonNull
    private static String clean(@Nullable String s) {
        return s == null ? "" : s.trim();
    }

    /** Primera letra de las dos primeras palabras, en mayúscula. */
    @NonNull
    static String initialsOf(@NonNull String name) {
        StringBuilder sb = new StringBuilder(2);
        for (String word : name.split("\\s+")) {
            if (!word.isEmpty()) {
                sb.appendCodePoint(word.codePointAt(0));
                if (sb.codePointCount(0, sb.length()) >= 2) {
                    break;
                }
            }
        }
        return sb.toString().toUpperCase(Locale.getDefault());
    }
}
