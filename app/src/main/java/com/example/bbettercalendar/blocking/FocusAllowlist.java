package com.example.bbettercalendar.blocking;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

// "Apps siempre permitidas" (mejora-visual u10): paquetes que el escudo de enfoque (modo bloqueo
// total del pomodoro, FocusBlockState) NUNCA cubre. Solo afecta a ese modo: una app de la lista que
// supera su límite diario sigue bloqueada por su límite (D4; ver BlockDecisionEngine.decide()).
// Vive en SharedPreferences, en el mismo fichero que BlockingSettings: no es por-app en el sentido
// de AppRule y no justifica una columna nueva (regla #6). Lista vacía = comportamiento de antes
// (solo se libran el launcher y el asistente).
//
// La API pública recibe un Context; las sobrecargas con SharedPreferences son el núcleo y lo que
// prueban los tests JVM (sin Robolectric).
public final class FocusAllowlist {

    static final String KEY_PACKAGES = "focus_allowlist";
    // La sugerencia inicial (marcador + mapas) se ofrece hasta la primera vez que el usuario guarda
    // la lista; después nunca más.
    static final String KEY_SUGGESTION_DONE = "focus_allowlist_suggestion_done";

    /** Recibe la lista nueva (copia inmutable) cada vez que cambia. Hilo principal. */
    public interface Listener {
        void onAllowlistChanged(@NonNull Set<String> packages);
    }

    /** Baja de {@link #observe}. */
    public interface Subscription {
        void cancel();
    }

    private FocusAllowlist() { }

    // ---------------------------------------------------------------- Context

    @NonNull
    public static Set<String> get(@NonNull Context context) {
        return get(BlockingSettings.prefs(context));
    }

    public static void set(@NonNull Context context, @Nullable Collection<String> packages) {
        set(BlockingSettings.prefs(context), packages);
    }

    /**
     * Escucha cambios de la lista. SharedPreferences guarda sus listeners con referencias débiles:
     * la Subscription devuelta retiene el listener, así que hay que guardarla mientras se escucha y
     * llamar a {@link Subscription#cancel()} al terminar.
     */
    @NonNull
    public static Subscription observe(@NonNull Context context, @NonNull Listener listener) {
        return observe(BlockingSettings.prefs(context), listener);
    }

    public static boolean isSuggestionDone(@NonNull Context context) {
        return isSuggestionDone(BlockingSettings.prefs(context));
    }

    public static void markSuggestionDone(@NonNull Context context) {
        markSuggestionDone(BlockingSettings.prefs(context));
    }

    // ---------------------------------------------------------------- núcleo

    @NonNull
    static Set<String> get(@NonNull SharedPreferences prefs) {
        Set<String> stored = prefs.getStringSet(KEY_PACKAGES, null);
        // getStringSet devuelve el conjunto interno de las preferencias: nunca se modifica, se copia.
        if (stored == null || stored.isEmpty()) return Collections.emptySet();
        return Collections.unmodifiableSet(new HashSet<>(stored));
    }

    static void set(@NonNull SharedPreferences prefs, @Nullable Collection<String> packages) {
        prefs.edit().putStringSet(KEY_PACKAGES, clean(packages)).apply();
    }

    @NonNull
    static Subscription observe(@NonNull SharedPreferences prefs, @NonNull Listener listener) {
        // key == null: clear() en API 30+ (afecta también a la lista).
        final SharedPreferences.OnSharedPreferenceChangeListener inner = (p, key) -> {
            if (key == null || KEY_PACKAGES.equals(key)) {
                listener.onAllowlistChanged(get(p));
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(inner);
        return () -> prefs.unregisterOnSharedPreferenceChangeListener(inner);
    }

    static boolean isSuggestionDone(@NonNull SharedPreferences prefs) {
        return prefs.getBoolean(KEY_SUGGESTION_DONE, false);
    }

    static void markSuggestionDone(@NonNull SharedPreferences prefs) {
        prefs.edit().putBoolean(KEY_SUGGESTION_DONE, true).apply();
    }

    // ---------------------------------------------------------------- cálculo puro

    /**
     * Conjunto exento del modo bloqueo total: los paquetes del sistema que ya se libraban (launcher +
     * asistente, resueltos por el servicio) ∪ la lista del usuario. Nunca null; ignora nulos y vacíos.
     */
    @NonNull
    public static Set<String> focusExemptPackages(@Nullable Collection<String> systemExempt,
                                                  @Nullable Collection<String> allowlist) {
        Set<String> out = clean(systemExempt);
        out.addAll(clean(allowlist));
        return Collections.unmodifiableSet(out);
    }

    @NonNull
    private static Set<String> clean(@Nullable Collection<String> packages) {
        Set<String> out = new HashSet<>();
        if (packages == null) return out;
        for (String pkg : packages) {
            if (pkg != null && !pkg.trim().isEmpty()) out.add(pkg.trim());
        }
        return out;
    }
}
