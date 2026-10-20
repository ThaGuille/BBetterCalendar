package com.example.bbettercalendar.ui.social;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.bbettercalendar.social.account.AccountRepository;
import com.example.bbettercalendar.social.account.AccountState;
import com.example.bbettercalendar.social.account.AgeGate;
import com.example.bbettercalendar.social.account.SocialPrefs;
import com.example.bbettercalendar.social.account.SocialTerms;
import com.example.bbettercalendar.social.profile.HandleValidator;
import com.example.bbettercalendar.social.profile.Profile;
import com.example.bbettercalendar.social.profile.ProfileRepository;
import com.example.bbettercalendar.social.profile.ProfileState;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.util.Date;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * F1-u5: lógica de Crear perfil y Mi perfil. Valida el {@code @usuario} en vivo con
 * {@link HandleValidator} y, pasado el retardo de {@link #DEBOUNCE_MS}, comprueba que esté libre con
 * {@link ProfileRepository#isHandleFree}. Los callbacks de Firestore llegan en el hilo de E/S, así
 * que todo resultado asíncrono sale con postValue (regla #3). Un ViewModel por pantalla.
 */
@HiltViewModel
public class ProfileViewModel extends ViewModel {

    /** Retardo entre la última tecla y la consulta de disponibilidad. */
    static final long DEBOUNCE_MS = 400L;

    /** Estado de la comprobación del {@code @usuario} tecleado. */
    public static final class HandleStatus {
        public enum Kind { IDLE, INVALID, CHECKING, FREE, TAKEN, ERROR }

        private static final HandleStatus IDLE = new HandleStatus(Kind.IDLE, null);
        private static final HandleStatus CHECKING = new HandleStatus(Kind.CHECKING, null);
        private static final HandleStatus FREE = new HandleStatus(Kind.FREE, null);
        private static final HandleStatus TAKEN = new HandleStatus(Kind.TAKEN, null);
        private static final HandleStatus ERROR = new HandleStatus(Kind.ERROR, null);

        @NonNull public final Kind kind;
        /** Solo con {@link Kind#INVALID}. */
        @Nullable public final HandleValidator.HandleResult invalid;

        private HandleStatus(@NonNull Kind kind, @Nullable HandleValidator.HandleResult invalid) {
            this.kind = kind;
            this.invalid = invalid;
        }
    }

    /** Qué falló al guardar, sin texto: la pantalla elige la cadena. */
    public enum Failure { HANDLE_TAKEN, RENAME_TOO_SOON, NETWORK, INVALID, GENERIC }

    /** Resultado de guardar, de un solo uso (ver {@link #consumeOutcome()}). */
    public static final class Outcome {
        public enum Kind { CREATED, UPDATED, RENAMED, FAILED }

        @NonNull public final Kind kind;
        @Nullable public final Failure failure;

        private Outcome(@NonNull Kind kind, @Nullable Failure failure) {
            this.kind = kind;
            this.failure = failure;
        }
    }

    private final ProfileRepository profiles;
    private final AccountRepository account;
    private final SocialPrefs prefs;
    private final Handler main = new Handler(Looper.getMainLooper());

    private final MutableLiveData<HandleStatus> handleStatus = new MutableLiveData<>(HandleStatus.IDLE);
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<Outcome> outcome = new MutableLiveData<>();

    // Cada tecla invalida lo anterior: una respuesta tardía de otra consulta se descarta.
    private volatile long inputSeq;
    @Nullable private Runnable pendingCheck;

    @Inject
    public ProfileViewModel(ProfileRepository profiles, AccountRepository account, SocialPrefs prefs) {
        this.profiles = profiles;
        this.account = account;
        this.prefs = prefs;
    }

    public LiveData<ProfileState> getProfile() {
        return profiles.observeMine();
    }

    public LiveData<AccountState> getAccountState() {
        return account.getState();
    }

    public LiveData<HandleStatus> getHandleStatus() {
        return handleStatus;
    }

    public LiveData<Boolean> isBusy() {
        return busy;
    }

    public LiveData<Outcome> getOutcome() {
        return outcome;
    }

    /** Llamar al atender el resultado (hilo principal). */
    public void consumeOutcome() {
        outcome.setValue(null);
    }

    /** Desde cuándo puede renombrar este perfil; ahora si nunca lo ha hecho (D-019, D-022). */
    @NonNull
    public Date canRenameAt(@NonNull Profile profile) {
        return profiles.canRenameAt(profile);
    }

    // ---- @usuario en vivo ------------------------------------------------------------------------------

    /**
     * Llamar en cada cambio del campo (hilo principal). Valida al instante; si el formato es bueno,
     * espera {@link #DEBOUNCE_MS} sin teclas y pregunta al servidor si está libre.
     *
     * @param ownHandle el {@code @usuario} actual del perfil (renombrar) o null; no se consulta
     */
    public void onHandleInput(@Nullable String raw, @Nullable String ownHandle) {
        cancelPending();
        long seq = ++inputSeq;
        String handle = HandleValidator.normalizeHandle(raw);
        HandleValidator.HandleResult result = HandleValidator.checkHandle(handle);
        if (result == HandleValidator.HandleResult.EMPTY || handle.equals(ownHandle)) {
            handleStatus.setValue(HandleStatus.IDLE);
            return;
        }
        if (result != HandleValidator.HandleResult.OK) {
            handleStatus.setValue(new HandleStatus(HandleStatus.Kind.INVALID, result));
            return;
        }
        handleStatus.setValue(HandleStatus.CHECKING);
        pendingCheck = () -> {
            pendingCheck = null;
            profiles.isHandleFree(handle, new ProfileRepository.HandleFreeCallback() {
                @Override
                public void onResult(boolean free) {
                    if (seq == inputSeq) {
                        handleStatus.postValue(free ? HandleStatus.FREE : HandleStatus.TAKEN);
                    }
                }

                @Override
                public void onError(@NonNull Throwable error) {
                    if (seq == inputSeq) {
                        handleStatus.postValue(HandleStatus.ERROR);
                    }
                }
            });
        };
        main.postDelayed(pendingCheck, DEBOUNCE_MS);
    }

    private void cancelPending() {
        if (pendingCheck != null) {
            main.removeCallbacks(pendingCheck);
            pendingCheck = null;
        }
    }

    // ---- escribir --------------------------------------------------------------------------------------

    /** Crea users/, profiles/ y handles/ en un lote (año guardado → franja de edad, términos vigentes). */
    public void create(@NonNull String name, @NonNull String handle, int avatarColor) {
        busy.setValue(true);
        prefs.readBirthYear(birthYear -> {
            if (birthYear == null) {
                finish(new Outcome(Outcome.Kind.FAILED, Failure.GENERIC));
                return;
            }
            String band = AgeGate.ageBand(birthYear, SocialEntryViewModel.currentYear());
            profiles.create(name, handle, avatarColor, band, SocialTerms.TERMS_VERSION,
                    callback(Outcome.Kind.CREATED));
        });
    }

    public void update(@NonNull String name, int avatarColor) {
        busy.setValue(true);
        profiles.update(name, avatarColor, callback(Outcome.Kind.UPDATED));
    }

    public void rename(@NonNull String newHandle) {
        busy.setValue(true);
        profiles.rename(newHandle, callback(Outcome.Kind.RENAMED));
    }

    public void signOut() {
        account.signOut();
    }

    private ProfileRepository.Callback callback(@NonNull Outcome.Kind success) {
        return new ProfileRepository.Callback() {
            @Override
            public void onSuccess() {
                finish(new Outcome(success, null));
            }

            @Override
            public void onError(@NonNull Throwable error) {
                finish(new Outcome(Outcome.Kind.FAILED, map(error)));
            }
        };
    }

    private void finish(@NonNull Outcome result) {
        busy.postValue(false);
        outcome.postValue(result);
    }

    /**
     * Pura: traduce el fallo del repositorio a un {@link Failure}. Un lote que las reglas rechazan
     * (PERMISSION_DENIED) al crear o renombrar es casi siempre un {@code @usuario} que otra persona
     * se llevó mientras tanto (u1 y u3 lo dejaron como PERMISSION_DENIED crudo).
     */
    @NonNull
    static Failure map(@NonNull Throwable error) {
        if (error instanceof ProfileRepository.ProfileException) {
            switch (((ProfileRepository.ProfileException) error).code) {
                case RENAME_TOO_SOON:
                    return Failure.RENAME_TOO_SOON;
                case INVALID_NAME:
                case INVALID_HANDLE:
                case INVALID_COLOR:
                case SAME_HANDLE:
                    return Failure.INVALID;
                default:
                    return Failure.GENERIC;
            }
        }
        if (error instanceof FirebaseNetworkException) {
            return Failure.NETWORK;
        }
        if (error instanceof FirebaseFirestoreException) {
            switch (((FirebaseFirestoreException) error).getCode()) {
                case PERMISSION_DENIED:
                case ALREADY_EXISTS:
                    return Failure.HANDLE_TAKEN;
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED:
                    return Failure.NETWORK;
                default:
                    return Failure.GENERIC;
            }
        }
        return Failure.GENERIC;
    }

    @Override
    protected void onCleared() {
        cancelPending();
    }
}
