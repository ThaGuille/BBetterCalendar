package com.example.bbettercalendar.social.account;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

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
    private final MutableLiveData<AccountState> state;

    @Inject
    public AccountRepository(FirebaseAuth auth, GoogleSignInHelper google) {
        this.auth = auth;
        this.google = google;
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
}
