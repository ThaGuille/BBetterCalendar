package com.example.bbettercalendar.social.account;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.example.bbettercalendar.database.IoExecutor;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Preferencias locales del sistema social (F1-u2): año de nacimiento (se guarda una sola vez) y
 * nombre local de Home. SharedPreferences, nada de Room. Lecturas y escrituras van en el
 * {@code @IoExecutor}; las escrituras usan {@code apply()}.
 */
@Singleton
public class SocialPrefs {

    public static final String FILE_NAME = "bb_social_prefs";
    private static final String KEY_BIRTH_YEAR = "birth_year";
    private static final String KEY_LOCAL_NAME = "local_name";

    private final Context appContext;
    private final ExecutorService io;

    @Inject
    public SocialPrefs(@ApplicationContext Context context, @IoExecutor ExecutorService io) {
        this.appContext = context;
        this.io = io;
    }

    private SharedPreferences prefs() {
        return appContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    /** Entrega el año guardado (o null) en el hilo de E/S; el receptor debe usar postValue. */
    public void readBirthYear(Consumer<Integer> callback) {
        io.execute(() -> {
            SharedPreferences p = prefs();
            callback.accept(p.contains(KEY_BIRTH_YEAR) ? Integer.valueOf(p.getInt(KEY_BIRTH_YEAR, 0)) : null);
        });
    }

    /** Guarda el año solo si aún no hay uno: no se puede editar desde la UI. */
    public void saveBirthYearOnce(int birthYear) {
        io.execute(() -> {
            SharedPreferences p = prefs();
            if (!p.contains(KEY_BIRTH_YEAR)) {
                p.edit().putInt(KEY_BIRTH_YEAR, birthYear).apply();
            }
        });
    }

    /** Entrega el nombre local (o null) en el hilo de E/S. */
    public void readLocalName(Consumer<String> callback) {
        io.execute(() -> callback.accept(prefs().getString(KEY_LOCAL_NAME, null)));
    }

    /** Guarda el nombre local; null o vacío lo borra. */
    public void saveLocalName(@Nullable String name) {
        io.execute(() -> {
            SharedPreferences.Editor e = prefs().edit();
            if (name == null || name.trim().isEmpty()) {
                e.remove(KEY_LOCAL_NAME);
            } else {
                e.putString(KEY_LOCAL_NAME, name.trim());
            }
            e.apply();
        });
    }
}
