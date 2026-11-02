package com.example.bbettercalendar.social.push;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.BuildConfig;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * {@link PushTokenBackend} sobre Auth, Firestore y FCM (F5-u6): el único sitio del registro de tokens que toca
 * clases de Firebase.
 *
 * <p><b>Token (decisión d8).</b> Con {@code USE_FIREBASE_EMULATOR} (las compilaciones del Emulator Suite) el token es
 * falso y estable, {@code emulator-<aleatorio>}, guardado en su propio fichero de preferencias: ninguna ejecución de
 * F5 registra una instalación en el proyecto real (D-021). El {@code getToken()} de FCM solo corre con
 * {@code -PfirebaseEmulator=false} (L.7). Sin {@code FEATURE_SOCIAL} (release) nadie llama a este adaptador.
 */
@Singleton
public class FirebasePushTokenBackend implements PushTokenBackend {

    static final String PREFS_FILE = "bb_push_prefs";
    private static final String KEY_EMULATOR_TOKEN = "emulator_token";
    private static final String USERS = "users";

    private final Context appContext;
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    @Inject
    public FirebasePushTokenBackend(@ApplicationContext Context appContext, FirebaseAuth auth,
                                    FirebaseFirestore db) {
        this.appContext = appContext;
        this.auth = auth;
        this.db = db;
    }

    @Nullable
    @Override
    public String currentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    @Override
    public void fetchToken(@NonNull Executor executor, @NonNull TokenCallback callback) {
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            executor.execute(() -> callback.onToken(emulatorToken()));
            return;
        }
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(executor, callback::onToken)
                .addOnFailureListener(executor, callback::onError);
    }

    @Override
    public void deleteToken(@NonNull Executor executor, @NonNull Callback callback) {
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            executor.execute(() -> {
                prefs().edit().remove(KEY_EMULATOR_TOKEN).apply();
                callback.onSuccess();
            });
            return;
        }
        FirebaseMessaging.getInstance().deleteToken()
                .addOnSuccessListener(executor, unused -> callback.onSuccess())
                .addOnFailureListener(executor, callback::onError);
    }

    @Override
    public void updateTokens(@NonNull String uid, @NonNull UnaryOperator<List<String>> change,
                             @NonNull Executor executor, @NonNull Callback callback) {
        DocumentReference ref = db.collection(USERS).document(uid);
        db.runTransaction(tx -> {
            DocumentSnapshot snap = tx.get(ref);
            if (!snap.exists()) {
                return null; // sin users/ (perfil a medio crear o cuenta borrada): nada que tocar
            }
            List<String> current = FcmTokens.clean(snap.get(FcmTokens.FIELD));
            List<String> next = change.apply(current);
            if (!next.equals(current)) {
                tx.update(ref, FcmTokens.FIELD, next);
            }
            return null;
        })
                .addOnSuccessListener(executor, unused -> callback.onSuccess())
                .addOnFailureListener(executor, callback::onError);
    }

    // ---- token falso del emulador (d8); solo en el hilo de E/S ----

    @NonNull
    private String emulatorToken() {
        SharedPreferences p = prefs();
        String token = p.getString(KEY_EMULATOR_TOKEN, null);
        if (token == null) {
            token = "emulator-" + UUID.randomUUID();
            p.edit().putString(KEY_EMULATOR_TOKEN, token).commit();
        }
        return token;
    }

    private SharedPreferences prefs() {
        return appContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
    }
}
