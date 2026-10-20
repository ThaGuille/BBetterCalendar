package com.example.bbettercalendar.ui.social;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.bbettercalendar.social.account.AccountRepository;
import com.example.bbettercalendar.social.account.AccountState;
import com.example.bbettercalendar.social.account.AgeGate;
import com.example.bbettercalendar.social.account.SocialEntryRouter;
import com.example.bbettercalendar.social.account.SocialPrefs;
import com.example.bbettercalendar.social.account.SocialTerms;
import com.example.bbettercalendar.social.profile.ProfileRepository;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

import java.util.Calendar;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * F1-u4: lógica de la entrada social para las pantallas de {@code ui/social} y los bloques
 * «sin cuenta» de Focus y Home. Une {@link SocialPrefs} (año guardado), {@link AccountRepository}
 * (sesión) y {@link SocialEntryRouter} (siguiente paso). Los callbacks de Firebase y Credential
 * Manager pueden llegar fuera del hilo principal, así que todo resultado sale con postValue.
 * Un ViewModel por pantalla (scope del Fragment).
 */
@HiltViewModel
public class SocialEntryViewModel extends ViewModel {

    /** Qué falló, sin texto: la pantalla elige la cadena. */
    public enum Failure {
        INVALID_EMAIL, WEAK_PASSWORD, EMAIL_IN_USE, BAD_CREDENTIALS, NETWORK, GOOGLE, GENERIC
    }

    /** Resultado de una operación de cuenta, de un solo uso (ver {@link #consumeOutcome()}). */
    public static final class Outcome {
        public enum Kind { SIGNED_IN, RESET_SENT, FAILED }

        public final Kind kind;
        @Nullable public final Failure failure;

        private Outcome(Kind kind, @Nullable Failure failure) {
            this.kind = kind;
            this.failure = failure;
        }
    }

    private final AccountRepository account;
    private final SocialPrefs prefs;
    private final ProfileRepository profiles;

    private final MutableLiveData<SocialEntryRouter.Step> entryStep = new MutableLiveData<>();
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<Outcome> outcome = new MutableLiveData<>();
    private volatile boolean entryPending;

    @Inject
    public SocialEntryViewModel(AccountRepository account, SocialPrefs prefs, ProfileRepository profiles) {
        this.account = account;
        this.prefs = prefs;
        this.profiles = profiles;
    }

    public LiveData<AccountState> getAccountState() {
        return account.getState();
    }

    /** Siguiente paso del router tras tocar un bloque social; de un solo uso. */
    public LiveData<SocialEntryRouter.Step> getEntryStep() {
        return entryStep;
    }

    public LiveData<Boolean> isBusy() {
        return busy;
    }

    public LiveData<Outcome> getOutcome() {
        return outcome;
    }

    /** Lee el año guardado (E/S) y publica el paso del router. Ignora toques mientras espera. */
    public void requestEntry() {
        if (entryPending) {
            return;
        }
        entryPending = true;
        prefs.readBirthYear(birthYear -> {
            // La sesión se mira en FirebaseAuth (síncrono), no en el LiveData: justo tras iniciar
            // sesión el LiveData aún puede decir SIGNED_OUT.
            if (birthYear == null || !AgeGate.isAllowed(birthYear, currentYear())
                    || account.getUid() == null) {
                entryStep.postValue(SocialEntryRouter.route(birthYear, currentYear(),
                        signedIn() ? AccountState.SIGNED_IN : AccountState.SIGNED_OUT, null,
                        SocialTerms.TERMS_VERSION));
                return;
            }
            // Con sesión: la versión de términos aceptada dice si hay perfil (F1-u5).
            profiles.readTermsVersion(new ProfileRepository.TermsCallback() {
                @Override
                public void onResult(@Nullable Integer termsVersion) {
                    entryStep.postValue(SocialEntryRouter.route(birthYear, currentYear(),
                            AccountState.SIGNED_IN, termsVersion, SocialTerms.TERMS_VERSION));
                }

                @Override
                public void onError(@NonNull Throwable error) {
                    // Sin saber si hay perfil no se manda a ninguna parte; otro toque lo reintenta.
                    entryPending = false;
                }
            });
        });
    }

    /** Llamar al atender el paso (hilo principal). */
    public void consumeEntryStep() {
        entryPending = false;
        entryStep.setValue(null);
    }

    /** Llamar al atender el resultado (hilo principal). */
    public void consumeOutcome() {
        outcome.setValue(null);
    }

    public static int currentYear() {
        return Calendar.getInstance().get(Calendar.YEAR);
    }

    /** Guarda el año (solo la primera vez) y devuelve el paso que le toca con ese año. */
    public SocialEntryRouter.Step saveBirthYear(int birthYear) {
        prefs.saveBirthYearOnce(birthYear);
        return SocialEntryRouter.route(birthYear, currentYear(),
                signedIn() ? AccountState.SIGNED_IN : AccountState.SIGNED_OUT, null,
                SocialTerms.TERMS_VERSION);
    }

    private boolean signedIn() {
        return account.getUid() != null;
    }

    public void signInWithGoogle(@NonNull Activity activity) {
        begin();
        account.signInWithGoogle(activity, callback(true));
    }

    public void signUp(@NonNull String email, @NonNull String password) {
        begin();
        account.signUp(email, password, callback(false));
    }

    public void signIn(@NonNull String email, @NonNull String password) {
        begin();
        account.signInWithEmail(email, password, callback(false));
    }

    public void sendPasswordReset(@NonNull String email) {
        busy.setValue(true);
        account.sendPasswordReset(email, new AccountRepository.Callback() {
            @Override
            public void onSuccess() {
                busy.postValue(false);
                outcome.postValue(new Outcome(Outcome.Kind.RESET_SENT, null));
            }

            @Override
            public void onError(@NonNull Throwable error) {
                busy.postValue(false);
                outcome.postValue(new Outcome(Outcome.Kind.FAILED, map(error, false)));
            }
        });
    }

    private void begin() {
        busy.setValue(true);
    }

    private AccountRepository.Callback callback(boolean google) {
        return new AccountRepository.Callback() {
            @Override
            public void onSuccess() {
                busy.postValue(false);
                outcome.postValue(new Outcome(Outcome.Kind.SIGNED_IN, null));
            }

            @Override
            public void onError(@NonNull Throwable error) {
                busy.postValue(false);
                // Cerrar el selector de Google no es un error: nada que mostrar.
                if (error instanceof GetCredentialCancellationException) {
                    return;
                }
                outcome.postValue(new Outcome(Outcome.Kind.FAILED, map(error, google)));
            }
        };
    }

    /** Pura: traduce la excepción de Firebase / Credential Manager a un {@link Failure}. */
    @NonNull
    static Failure map(@NonNull Throwable error, boolean google) {
        if (error instanceof FirebaseNetworkException) {
            return Failure.NETWORK;
        }
        if (error instanceof FirebaseAuthUserCollisionException) {
            return Failure.EMAIL_IN_USE;
        }
        if (error instanceof FirebaseAuthWeakPasswordException) {
            return Failure.WEAK_PASSWORD;
        }
        if (error instanceof FirebaseAuthInvalidUserException) {
            return Failure.BAD_CREDENTIALS;
        }
        if (error instanceof FirebaseAuthInvalidCredentialsException) {
            String code = ((FirebaseAuthInvalidCredentialsException) error).getErrorCode();
            return "ERROR_INVALID_EMAIL".equals(code) ? Failure.INVALID_EMAIL : Failure.BAD_CREDENTIALS;
        }
        if (error instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) error).getErrorCode();
            if ("ERROR_INVALID_EMAIL".equals(code)) {
                return Failure.INVALID_EMAIL;
            }
        }
        return google ? Failure.GOOGLE : Failure.GENERIC;
    }
}
