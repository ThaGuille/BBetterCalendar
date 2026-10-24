package com.example.bbettercalendar.ui.social;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.example.bbettercalendar.social.SocialFeature;
import com.example.bbettercalendar.social.account.SocialPrefs;
import com.example.bbettercalendar.social.friends.FriendCard;
import com.example.bbettercalendar.social.friends.FriendsRepository;
import com.example.bbettercalendar.social.invite.InviteRepository;
import com.example.bbettercalendar.social.profile.ProfileRepository;
import com.example.bbettercalendar.social.profile.ProfileState;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * F2-u8: la invitación que dejó el App Link ({@code InviteLinkActivity}) o el referrer de Play en
 * {@link SocialPrefs}. Ámbito de la actividad: {@code MainActivity} llama a {@link #check()} en cada
 * {@code onResume} y observa {@link #getUi()}.
 *
 * <p>Combina dos fuentes, así que funciona antes o después de iniciar sesión: el código pendiente (se lee en
 * {@code check()}) y el {@link ProfileState}. Con perfil {@code READY} se resuelve el código
 * ({@link InviteRepository#resolve}), se mira la relación ({@code lookupHandle}) y sale un {@link Ui} para la
 * hoja «Añadir a X» o su callejón sin salida; el código se borra al resolverlo. Sin sesión el código se
 * conserva y {@link #getInviteWaiting()} deja que la hoja «Únete» diga que hay una invitación esperando; con
 * sesión pero sin perfil espera a que se cree. Los callbacks llegan en el hilo de E/S: todo sale con
 * postValue (regla #3).
 */
@HiltViewModel
public class PendingInviteViewModel extends ViewModel {

    /** Lo que enseña la hoja. */
    public static final class Ui {
        public enum Kind {
            /** Se le puede enviar una solicitud a {@link #card}. */
            ACCEPT,
            ALREADY_FRIENDS,
            SELF,
            EXPIRED,
            ALREADY_SENT,
            /** Esa persona ya te envió una solicitud: se acepta en Solicitudes. */
            THEY_SENT,
            /** No se pudo comprobar (sin red): solo un aviso, el código sigue guardado. */
            ERROR
        }

        @NonNull public final Kind kind;
        @Nullable public final FriendCard card;

        Ui(@NonNull Kind kind, @Nullable FriendCard card) {
            this.kind = kind;
            this.card = card;
        }
    }

    /** Estado de «Enviar solicitud» en la hoja. */
    public static final class SendState {
        public enum Step { IDLE, SENDING, SENT, ERROR }

        @NonNull public final Step step;
        public final int message;

        SendState(@NonNull Step step, int message) {
            this.step = step;
            this.message = message;
        }
    }

    private final SocialPrefs prefs;
    private final InviteRepository invites;
    private final FriendsRepository friends;
    @Nullable private final LiveData<ProfileState> profile;

    private final MutableLiveData<String> pendingCode = new MutableLiveData<>();
    private final MediatorLiveData<Ui> ui = new MediatorLiveData<>();
    private final MutableLiveData<SendState> sendState =
            new MutableLiveData<>(new SendState(SendState.Step.IDLE, 0));
    private final LiveData<Boolean> inviteWaiting = Transformations.map(pendingCode, c -> c != null);
    /** El código que se está resolviendo (hilo principal para escribir; null cuando no hay ninguno). */
    @Nullable private volatile String resolving;
    /** Lo que muestra la hoja ahora; sobrevive a una rotación (el ViewModel también). */
    @Nullable private volatile Ui current;

    @Inject
    public PendingInviteViewModel(SocialPrefs prefs, InviteRepository invites, FriendsRepository friends,
                                  ProfileRepository profiles) {
        this.prefs = prefs;
        this.invites = invites;
        this.friends = friends;
        // Sin cuenta habilitada (release) no se observa nada: nunca hay invitación pendiente.
        this.profile = SocialFeature.isAccountEnabled() ? profiles.observeMine() : null;
        if (profile != null) {
            ui.addSource(profile, s -> evaluate());
            ui.addSource(pendingCode, c -> evaluate());
        }
    }

    /** Un aviso de un solo uso para {@code MainActivity}; se consume con {@link #consumeUi()}. */
    public LiveData<Ui> getUi() {
        return ui;
    }

    /** true mientras hay una invitación guardada sin atender (la hoja «Únete» lo dice). */
    public LiveData<Boolean> getInviteWaiting() {
        return inviteWaiting;
    }

    public LiveData<SendState> getSendState() {
        return sendState;
    }

    /** Lo que debe pintar la hoja abierta; null si ya no hay nada (p. ej. tras morir el proceso). */
    @Nullable
    public Ui getCurrent() {
        return current;
    }

    /** Llamar al atender el aviso (hilo principal). */
    public void consumeUi() {
        ui.setValue(null);
    }

    /** La hoja se cerró: la próxima invitación empieza de cero. */
    public void dismissCurrent() {
        current = null;
        sendState.setValue(new SendState(SendState.Step.IDLE, 0));
    }

    /** Llamar en {@code onResume}: lee la invitación guardada y, si hay perfil, la resuelve. */
    public void check() {
        if (profile == null) {
            return;
        }
        prefs.readPendingInvite(code -> {
            if (code != null) {
                pendingCode.postValue(code);
            }
        });
    }

    /** Hilo principal: cambió el perfil o el código. */
    private void evaluate() {
        String code = pendingCode.getValue();
        ProfileState state = profile == null ? null : profile.getValue();
        if (code == null || state == null || state.status != ProfileState.Status.READY) {
            return;
        }
        if (code.equals(resolving)) {
            return;
        }
        resolving = code;
        invites.resolve(code, new InviteRepository.ResolveCallback() {
            @Override
            public void onResolved(@NonNull InviteRepository.Resolution resolution) {
                switch (resolution.kind) {
                    case SELF:
                        finish(Ui.Kind.SELF, null);
                        break;
                    case VALID:
                        relation(resolution.card);
                        break;
                    case EXPIRED:
                    default:
                        finish(Ui.Kind.EXPIRED, null);
                        break;
                }
            }

            @Override
            public void onError(@NonNull Throwable error) {
                failed(error);
            }
        });
    }

    /** Quien invita es otra persona vigente: ¿ya sois amigos, hay solicitud entre vosotros o se puede añadir? */
    private void relation(@Nullable FriendCard card) {
        if (card == null) {
            finish(Ui.Kind.EXPIRED, null);
            return;
        }
        friends.lookupHandle(card.handle, new FriendsRepository.LookupCallback() {
            @Override
            public void onResult(@NonNull FriendsRepository.LookupResult result) {
                switch (result.kind) {
                    case OK:
                        finish(Ui.Kind.ACCEPT, result.card != null ? result.card : card);
                        break;
                    case ALREADY_FRIENDS:
                        finish(Ui.Kind.ALREADY_FRIENDS, card);
                        break;
                    case ALREADY_SENT:
                        finish(Ui.Kind.ALREADY_SENT, card);
                        break;
                    case THEY_SENT_YOU:
                        finish(Ui.Kind.THEY_SENT, card);
                        break;
                    case SELF:
                        finish(Ui.Kind.SELF, null);
                        break;
                    case NOT_FOUND:
                    default:
                        finish(Ui.Kind.EXPIRED, null);
                        break;
                }
            }

            @Override
            public void onError(@NonNull Throwable error) {
                failed(error);
            }
        });
    }

    /** Resuelto: el código se borra (ya se va a enseñar) y sale el aviso. */
    private void finish(@NonNull Ui.Kind kind, @Nullable FriendCard card) {
        prefs.clearPendingInvite();
        resolving = null;
        pendingCode.postValue(null);
        Ui result = new Ui(kind, card);
        current = result;
        ui.postValue(result);
    }

    /** Sin poder comprobar: el código se queda guardado y se reintenta en el próximo onResume. */
    private void failed(@NonNull Throwable error) {
        resolving = null;
        if (error instanceof InviteRepository.InviteException
                && ((InviteRepository.InviteException) error).code
                == InviteRepository.InviteException.Code.NOT_SIGNED_IN) {
            return; // se cerró la sesión entre medias: «Únete» lo recogerá
        }
        ui.postValue(new Ui(Ui.Kind.ERROR, null));
    }

    // ---- Enviar solicitud ----------------------------------------------------------------------------------

    public void send(@NonNull FriendCard card) {
        sendState.setValue(new SendState(SendState.Step.SENDING, 0));
        friends.send(card, new FriendsRepository.Callback() {
            @Override
            public void onSuccess() {
                sendState.postValue(new SendState(SendState.Step.SENT, 0));
            }

            @Override
            public void onError(@NonNull Throwable error) {
                sendState.postValue(new SendState(SendState.Step.ERROR, FriendsViewModel.errorText(error)));
            }
        });
    }
}
