package com.example.bbettercalendar.ui.social;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.bbettercalendar.social.interaction.InteractionRepository;
import com.example.bbettercalendar.social.interaction.InteractionResult;
import com.example.bbettercalendar.social.interaction.InteractionType;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * F5-u8: animo y empujon desde el perfil de un amigo. Lee del registro local si hoy ya se envio cada tipo y
 * envia con {@link InteractionRepository}; los callbacks llegan fuera del hilo principal, asi que publica con
 * {@code postValue} (regla #3). No guarda ni expone contadores.
 */
@HiltViewModel
public class FriendInteractionViewModel extends ViewModel {

    /** Lo que sabe la pantalla del envio: registro de hoy y el ultimo resultado de esta visita. */
    static final class State {
        final boolean cheerSent;
        final boolean nudgeSent;
        @Nullable
        final InteractionType lastType;
        @Nullable
        final InteractionResult lastResult;

        State(boolean cheerSent, boolean nudgeSent, @Nullable InteractionType lastType,
              @Nullable InteractionResult lastResult) {
            this.cheerSent = cheerSent;
            this.nudgeSent = nudgeSent;
            this.lastType = lastType;
            this.lastResult = lastResult;
        }
    }

    private final InteractionRepository repository;
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(false, false, null, null));
    private String loadedUid;
    private volatile boolean busy;

    @Inject
    public FriendInteractionViewModel(InteractionRepository repository) {
        this.repository = repository;
    }

    @NonNull
    LiveData<State> getState() {
        return state;
    }

    /** Consulta el registro local de hoy para ese amigo (una vez por uid). */
    void load(@NonNull String uid) {
        if (uid.equals(loadedUid)) {
            return;
        }
        loadedUid = uid;
        repository.sentToday(InteractionType.CHEER, uid, cheer ->
                repository.sentToday(InteractionType.NUDGE, uid, nudge -> {
                    State s = current();
                    state.postValue(new State(cheer || s.cheerSent, nudge || s.nudgeSent, s.lastType,
                            s.lastResult));
                }));
    }

    /** Envia; un segundo toque mientras el primero vuela se ignora. */
    void send(@NonNull InteractionType type, @NonNull String uid) {
        if (busy) {
            return;
        }
        busy = true;
        repository.send(type, uid, result -> {
            State s = current();
            boolean delivered = result == InteractionResult.SENT || result == InteractionResult.ALREADY_SENT;
            state.postValue(new State(
                    s.cheerSent || (delivered && type == InteractionType.CHEER),
                    s.nudgeSent || (delivered && type == InteractionType.NUDGE),
                    type, result));
            busy = false;
        });
    }

    @NonNull
    private State current() {
        State s = state.getValue();
        return s == null ? new State(false, false, null, null) : s;
    }
}
