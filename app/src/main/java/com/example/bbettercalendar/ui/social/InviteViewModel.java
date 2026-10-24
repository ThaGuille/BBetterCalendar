package com.example.bbettercalendar.ui.social;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.example.bbettercalendar.social.invite.InviteLinkFactory;
import com.example.bbettercalendar.social.invite.InviteRepository;
import com.example.bbettercalendar.social.invite.QrMatrix;
import com.example.bbettercalendar.social.profile.ProfileRepository;
import com.example.bbettercalendar.social.profile.ProfileState;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * F2-u8: lógica de la hoja Invitar. Al crearse pide {@code InviteRepository.ensureMyCode()} (lee o crea el
 * código propio) y, con él, monta el enlace y la matriz del QR. El callback llega en el hilo de E/S, así que
 * el resultado sale con postValue (regla #3); el QR se codifica allí mismo, fuera del hilo principal.
 * Un ViewModel por hoja (ámbito del fragmento): cada vez que se abre la hoja se vuelve a pedir el código.
 */
@HiltViewModel
public class InviteViewModel extends ViewModel {

    /** Lo que pinta la hoja. */
    public static final class State {
        public enum Status { LOADING, READY, ERROR }

        @NonNull public final Status status;
        @Nullable public final String link;
        @Nullable public final QrMatrix qr;

        private State(@NonNull Status status, @Nullable String link, @Nullable QrMatrix qr) {
            this.status = status;
            this.link = link;
            this.qr = qr;
        }

        static State loading() {
            return new State(Status.LOADING, null, null);
        }

        static State ready(@NonNull String link, @NonNull QrMatrix qr) {
            return new State(Status.READY, link, qr);
        }

        static State error() {
            return new State(Status.ERROR, null, null);
        }
    }

    private final InviteRepository invites;
    private final MutableLiveData<State> state = new MutableLiveData<>(State.loading());
    private final LiveData<String> handle;

    @Inject
    public InviteViewModel(InviteRepository invites, ProfileRepository profiles) {
        this.invites = invites;
        this.handle = Transformations.map(profiles.observeMine(),
                s -> s != null && s.status == ProfileState.Status.READY && s.profile != null
                        ? s.profile.handle : null);
        load();
    }

    public LiveData<State> getState() {
        return state;
    }

    /** El {@code @usuario} propio sin la arroba; null mientras no hay perfil listo. */
    public LiveData<String> getHandle() {
        return handle;
    }

    /** Pide (o repite) el código propio. */
    public final void load() {
        state.setValue(State.loading());
        invites.ensureMyCode(new InviteRepository.CodeCallback() {
            @Override
            public void onCode(@NonNull String code) {
                try {
                    String link = InviteLinkFactory.link(code);
                    state.postValue(State.ready(link, QrMatrix.encode(link)));
                } catch (IllegalArgumentException e) {
                    state.postValue(State.error());
                }
            }

            @Override
            public void onError(@NonNull Throwable error) {
                state.postValue(State.error());
            }
        });
    }
}
