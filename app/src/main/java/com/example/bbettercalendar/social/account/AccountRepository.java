package com.example.bbettercalendar.social.account;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.functions.FirebaseFunctions;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Cuenta social (F1-u2): sesión de Firebase Auth + Google (Credential Manager) + email (D-023).
 * El estado sale de un AuthStateListener y se publica con postValue. Los resultados de las
 * operaciones llegan por {@link Callback}, que puede invocarse en el hilo principal (tareas de
 * Firebase) o en el de E/S (Credential Manager): quien lo reciba debe usar postValue.
 */
@Singleton
public class AccountRepository {

    public interface Callback {
        void onSuccess();

        void onError(@NonNull Throwable error);
    }

    private final FirebaseAuth auth;
    private final GoogleSignInHelper google;
    private final FirebaseFunctions functions;
    private final MutableLiveData<AccountState> state;

    @Inject
    public AccountRepository(FirebaseAuth auth, GoogleSignInHelper google, FirebaseFunctions functions) {
        this.auth = auth;
        this.google = google;
        this.functions = functions;
        this.state = new MutableLiveData<>(toState(auth.getCurrentUser()));
        auth.addAuthStateListener(firebaseAuth ->
                state.postValue(toState(firebaseAuth.getCurrentUser())));
    }

    private static AccountState toState(@Nullable FirebaseUser user) {
        return user == null ? AccountState.SIGNED_OUT : AccountState.SIGNED_IN;
    }

    public LiveData<AccountState> getState() {
        return state;
    }

    /** uid de la sesión actual, o null. */
    @Nullable
    public String getUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    public void signInWithGoogle(@NonNull Activity activity, @NonNull Callback callback) {
        google.requestCredential(activity, new GoogleSignInHelper.Listener() {
            @Override
            public void onCredential(@NonNull AuthCredential credential) {
                auth.signInWithCredential(credential)
                        .addOnSuccessListener(r -> callback.onSuccess())
                        .addOnFailureListener(callback::onError);
            }

            @Override
            public void onError(@NonNull Throwable error) {
                callback.onError(error);
            }
        });
    }

    public void signUp(@NonNull String email, @NonNull String password, @NonNull Callback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(r -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    public void signInWithEmail(@NonNull String email, @NonNull String password,
                                @NonNull Callback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(r -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    public void sendPasswordReset(@NonNull String email, @NonNull Callback callback) {
        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(r -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    public void signOut() {
        auth.signOut();
        google.clearCredentialState();
    }

    /** true si la cuenta entra con email y contraseña (re-autenticar pide la contraseña); si no, Google. */
    public boolean usesPassword() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            return false;
        }
        for (UserInfo info : user.getProviderData()) {
            if (EmailAuthProvider.PROVIDER_ID.equals(info.getProviderId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * F1-u7: vuelve a autenticar a la persona con sesión (borrar cuenta exige un login de menos de
     * 5 minutos). Con email: usa {@code password} ({@code activity} puede ser null). Con Google: abre
     * el selector otra vez ({@code activity} obligatoria, {@code password} null).
     */
    public void reauthenticate(@Nullable Activity activity, @Nullable String password,
                               @NonNull Callback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError(new IllegalStateException("No session"));
            return;
        }
        if (usesPassword()) {
            String email = user.getEmail();
            if (email == null || password == null) {
                callback.onError(new IllegalArgumentException("Missing email or password"));
                return;
            }
            user.reauthenticate(EmailAuthProvider.getCredential(email, password))
                    .addOnSuccessListener(r -> callback.onSuccess())
                    .addOnFailureListener(callback::onError);
        } else if (activity == null) {
            callback.onError(new IllegalArgumentException("Missing activity"));
        } else {
            google.requestCredential(activity, new GoogleSignInHelper.Listener() {
                @Override
                public void onCredential(@NonNull AuthCredential credential) {
                    user.reauthenticate(credential)
                            .addOnSuccessListener(r -> callback.onSuccess())
                            .addOnFailureListener(callback::onError);
                }

                @Override
                public void onError(@NonNull Throwable error) {
                    callback.onError(error);
                }
            });
        }
    }

    /**
     * F1-u7: llama a la callable {@code deleteAccount} (europe-west1). Con un login de más de 5
     * minutos el servidor responde FAILED_PRECONDITION ({@code failed-precondition}): quien llama
     * re-autentica y repite. Antes de llamar se refresca el token para que lleve el {@code auth_time}
     * nuevo. Si va bien, cierra la sesión local; Room, el año y el nombre local no se tocan.
     */
    public void deleteAccount(@NonNull Callback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError(new IllegalStateException("No session"));
            return;
        }
        user.getIdToken(true)
                .continueWithTask(token -> functions.getHttpsCallable("deleteAccount").call())
                .addOnSuccessListener(result -> {
                    signOut();
                    callback.onSuccess();
                })
                .addOnFailureListener(callback::onError);
    }
}
