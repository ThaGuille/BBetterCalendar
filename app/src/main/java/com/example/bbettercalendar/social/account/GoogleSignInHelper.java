package com.example.bbettercalendar.social.account;

import android.app.Activity;
import android.content.Context;
import android.os.CancellationSignal;

import androidx.annotation.NonNull;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialException;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.database.IoExecutor;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.GoogleAuthProvider;

import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Credential Manager (F1-u2): selector de cuentas de Google -> credencial de Firebase. Java 8: se
 * usan las variantes {@code *Async} con CancellationSignal, Executor y CredentialManagerCallback.
 * Los callbacks llegan en el {@code @IoExecutor}.
 */
@Singleton
public class GoogleSignInHelper {

    public interface Listener {
        void onCredential(@NonNull AuthCredential credential);

        void onError(@NonNull Throwable error);
    }

    private final Context appContext;
    private final ExecutorService io;

    @Inject
    public GoogleSignInHelper(@ApplicationContext Context context, @IoExecutor ExecutorService io) {
        this.appContext = context;
        this.io = io;
    }

    public void requestCredential(@NonNull Activity activity, @NonNull Listener listener) {
        GetGoogleIdOption option = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(appContext.getString(R.string.default_web_client_id))
                .build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build();
        CredentialManager.create(activity).getCredentialAsync(activity, request,
                new CancellationSignal(), io,
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        Credential credential = result.getCredential();
                        if (credential instanceof CustomCredential
                                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                .equals(credential.getType())) {
                            try {
                                GoogleIdTokenCredential google = GoogleIdTokenCredential
                                        .createFrom(((CustomCredential) credential).getData());
                                listener.onCredential(
                                        GoogleAuthProvider.getCredential(google.getIdToken(), null));
                            } catch (RuntimeException e) {
                                listener.onError(e);
                            }
                        } else {
                            listener.onError(new IllegalStateException(
                                    "Unexpected credential type: " + credential.getType()));
                        }
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        listener.onError(e);
                    }
                });
    }

    /** Olvida la cuenta elegida en Credential Manager (al cerrar sesión). */
    public void clearCredentialState() {
        CredentialManager.create(appContext).clearCredentialStateAsync(
                new ClearCredentialStateRequest(), new CancellationSignal(), io,
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override
                    public void onResult(Void result) { }

                    @Override
                    public void onError(ClearCredentialException e) { }
                });
    }
}
