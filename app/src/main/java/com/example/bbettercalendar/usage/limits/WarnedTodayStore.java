package com.example.bbettercalendar.usage.limits;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

// Marca "ya avisado / ya notificado hoy" por paquete, sin columna nueva en app_rule (regla #6):
// cada marca guarda la fecha ISO en que se disparó y sólo cuenta si coincide con hoy, así que las
// marcas de días anteriores quedan ignoradas solas. clearIfNewDay() además las barre físicamente
// para que el fichero de preferencias no crezca sin límite.
@Singleton
public class WarnedTodayStore {

    private static final String PREFS_NAME = "usage_limit_warned";
    private static final String WARN_PREFIX = "warn_";
    private static final String REACHED_PREFIX = "reached_";

    private final SharedPreferences prefs;

    @Inject
    public WarnedTodayStore(@ApplicationContext Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean hasWarned(String packageName) {
        return today().equals(prefs.getString(WARN_PREFIX + packageName, null));
    }

    public void markWarned(String packageName) {
        prefs.edit().putString(WARN_PREFIX + packageName, today()).apply();
    }

    public boolean hasReached(String packageName) {
        return today().equals(prefs.getString(REACHED_PREFIX + packageName, null));
    }

    public void markReached(String packageName) {
        prefs.edit().putString(REACHED_PREFIX + packageName, today()).apply();
    }

    // Elimina las marcas de días anteriores a hoy (llamarlo antes de cada chequeo).
    public void clearIfNewDay() {
        String today = today();
        Set<String> stale = new HashSet<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String && !today.equals(value)) {
                stale.add(entry.getKey());
            }
        }
        if (stale.isEmpty()) return;
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : stale) {
            editor.remove(key);
        }
        editor.apply();
    }

    private static String today() {
        return LocalDate.now().toString();
    }
}
