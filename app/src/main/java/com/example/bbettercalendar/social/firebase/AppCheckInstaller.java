package com.example.bbettercalendar.social.firebase;

import android.content.Context;

import com.example.bbettercalendar.BuildConfig;

/**
 * L-u3: instala App Check una sola vez, antes de que ningun cliente de Firebase se entregue
 * (se llama desde {@code DBMigration.onCreate}). Con {@link AppCheckPolicy.Provider#NONE} no toca nada:
 * el release por defecto (FEATURE_SOCIAL false) y el debug contra el emulador nunca tocan App Check.
 */
public final class AppCheckInstaller {

    private static boolean installed;

    private AppCheckInstaller() {}

    public static synchronized void installOnce(Context context) {
        if (installed) return;
        AppCheckPolicy.Provider provider = AppCheckPolicy.decide(
                BuildConfig.FEATURE_SOCIAL, BuildConfig.USE_FIREBASE_EMULATOR, BuildConfig.DEBUG);
        if (provider == AppCheckPolicy.Provider.NONE) return;
        AppCheckProviders.install(context, provider);
        installed = true;
    }
}
