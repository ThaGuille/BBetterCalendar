package com.example.bbettercalendar.social.push;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * La lista {@code users/{uid}.fcmTokens} (F5-u6, 03 §5, D-035), pura. Orden: la más antigua primero. Las reglas
 * admiten como mucho {@link #MAX} entradas, así que añadir a una lista llena quita antes las más antiguas hasta
 * dejar {@code MAX - 1} (nunca un {@code arrayUnion} a 10, que las reglas rechazarían).
 */
final class FcmTokens {

    static final String FIELD = "fcmTokens";
    static final int MAX = 10;

    private FcmTokens() { }

    /** Lo leído de Firestore como lista de tokens: solo cadenas no vacías, sin repetidas, en su orden. */
    @NonNull
    static List<String> clean(@Nullable Object raw) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (o instanceof String && !((String) o).isEmpty()) {
                    out.add((String) o);
                }
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * Añade {@code token} al final. Si ya está, la lista no cambia (no se duplica); si no, primero quita las más
     * antiguas hasta que queden {@code MAX - 1}.
     */
    @NonNull
    static List<String> with(@NonNull List<String> current, @NonNull String token) {
        List<String> out = new ArrayList<>(current);
        if (out.contains(token)) {
            return out;
        }
        while (out.size() > MAX - 1) {
            out.remove(0);
        }
        out.add(token);
        return out;
    }

    /** La lista sin {@code token} (igual si no estaba). */
    @NonNull
    static List<String> without(@NonNull List<String> current, @Nullable String token) {
        List<String> out = new ArrayList<>(current);
        if (token != null) {
            out.remove(token);
        }
        return out;
    }
}
