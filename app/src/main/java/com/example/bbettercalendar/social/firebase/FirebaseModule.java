package com.example.bbettercalendar.social.firebase;

import com.example.bbettercalendar.BuildConfig;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

/**
 * F0-u4: los cuatro clientes de Firebase del sistema social (docs/social/03-arquitectura.md §10).
 *
 * <p>Con {@code BuildConfig.USE_FIREBASE_EMULATOR} (debug, por defecto) cada cliente apunta al Emulator
 * Suite del PC por {@code 10.0.2.2} (puertos de {@code firebase/firebase.json}). {@code useEmulator}
 * solo puede llamarse antes del primer uso y una vez por instancia: por eso son singletons y no se
 * toca ningún cliente fuera de este módulo.
 *
 * <p>Nada de esto se usa todavía en la app: {@code FEATURE_SOCIAL} no tiene efecto hasta que exista el
 * repositorio real (F4).
 */
@Module
@InstallIn(SingletonComponent.class)
public class FirebaseModule {

    /** El emulador de Android ve el localhost del PC en esta dirección. */
    static final String EMULATOR_HOST = "10.0.2.2";
    static final int AUTH_PORT = 9099;
    static final int FIRESTORE_PORT = 8080;
    static final int DATABASE_PORT = 9000;
    static final int FUNCTIONS_PORT = 5001;

    /** RTDB en europe-west1 (D-013): fuera de us-central1 hay que dar la URL explícita. */
    static final String DATABASE_URL =
            "https://bbetter-dbcda-default-rtdb.europe-west1.firebasedatabase.app";
    static final String FUNCTIONS_REGION = "europe-west1";

    @Provides
    @Singleton
    public static FirebaseAuth provideFirebaseAuth() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            auth.useEmulator(EMULATOR_HOST, AUTH_PORT);
        }
        return auth;
    }

    @Provides
    @Singleton
    public static FirebaseFirestore provideFirebaseFirestore() {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            firestore.useEmulator(EMULATOR_HOST, FIRESTORE_PORT);
        }
        return firestore;
    }

    @Provides
    @Singleton
    public static FirebaseDatabase provideFirebaseDatabase() {
        FirebaseDatabase database = FirebaseDatabase.getInstance(DATABASE_URL);
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            database.useEmulator(EMULATOR_HOST, DATABASE_PORT);
        }
        return database;
    }

    @Provides
    @Singleton
    public static FirebaseFunctions provideFirebaseFunctions() {
        FirebaseFunctions functions = FirebaseFunctions.getInstance(FUNCTIONS_REGION);
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            functions.useEmulator(EMULATOR_HOST, FUNCTIONS_PORT);
        }
        return functions;
    }
}
