package com.example.bbettercalendar.social.interaction;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;

/** {@link SentLogStore} en su propio fichero de SharedPreferences (F5-u5; decisión D-035). Nada de Room. */
final class PrefsSentLogStore implements SentLogStore {

    static final String FILE_NAME = "bb_interaction_sent";
    private static final String KEY_ENTRIES = "entries";

    private final Context appContext;

    PrefsSentLogStore(@NonNull Context appContext) {
        this.appContext = appContext;
    }

    private SharedPreferences prefs() {
        return appContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    @Override
    public Set<String> read() {
        Set<String> stored = prefs().getStringSet(KEY_ENTRIES, null);
        // Una copia: el conjunto que devuelve SharedPreferences no se debe modificar.
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }

    @Override
    public void write(@NonNull Set<String> entries) {
        prefs().edit().putStringSet(KEY_ENTRIES, new HashSet<>(entries)).apply();
    }

    @Override
    public void clear() {
        // Quita el fichero entero (API 24+), no solo la clave.
        appContext.deleteSharedPreferences(FILE_NAME);
    }
}
