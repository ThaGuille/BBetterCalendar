package com.example.bbettercalendar.social.firebase;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

/** L-u3: fabricas de App Check de la build debug (la de release vive en app/src/release). */
final class AppCheckProviders {

    private AppCheckProviders() {}

    static void install(Context context, AppCheckPolicy.Provider provider) {
        FirebaseApp.initializeApp(context);
        FirebaseAppCheck appCheck = FirebaseAppCheck.getInstance();
        if (provider == AppCheckPolicy.Provider.DEBUG) {
            // El secreto de depuracion se imprime en logcat ("DebugAppCheckProvider ... debug secret");
            // hay que registrarlo en la consola de Firebase (App Check > Apps > Administrar tokens de
            // depuracion) para que el proyecto real acepte esta instalacion (guia L, paso del usuario).
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance());
        } else {
            appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance());
        }
        appCheck.setTokenAutoRefreshEnabled(true);
    }
}
