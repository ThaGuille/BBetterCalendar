package com.example.bbettercalendar.social.firebase;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

/** L-u3: en release solo Play Integrity; la fabrica de debug no existe en este APK. */
final class AppCheckProviders {

    private AppCheckProviders() {}

    static void install(Context context, AppCheckPolicy.Provider provider) {
        FirebaseApp.initializeApp(context);
        FirebaseAppCheck appCheck = FirebaseAppCheck.getInstance();
        appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance());
        appCheck.setTokenAutoRefreshEnabled(true);
    }
}
