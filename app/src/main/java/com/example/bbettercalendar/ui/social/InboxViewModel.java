package com.example.bbettercalendar.ui.social;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.example.bbettercalendar.social.friends.FriendCard;
import com.example.bbettercalendar.social.friends.FriendRequest;
import com.example.bbettercalendar.social.friends.FriendsRepository;
import com.example.bbettercalendar.social.friends.FriendsState;
import com.example.bbettercalendar.social.inbox.InboxItem;
import com.example.bbettercalendar.social.inbox.InboxRepository;
import com.example.bbettercalendar.social.inbox.InboxState;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * F5-u7: la bandeja de la campana. Une {@link InboxRepository#observeInbox()} con la lista de amigos
 * ({@link FriendsRepository#observeFriends()}) para decidir a dónde lleva cada fila ({@link InboxRows}).
 * Abrirla marca todo leído (d1): los avisos que estaban sin leer (también los que llegan con ella abierta) guardan
 * el acento «nuevo» mientras dure la visita, y la escritura sale del propio repositorio (nada de LiveData.setValue
 * desde un hilo de fondo: aquí solo llegan estados ya publicados en el principal).
 */
@HiltViewModel
public class InboxViewModel extends ViewModel {

    /** Lo que pinta el fragmento. */
    public static final class Screen {
        @NonNull public final InboxState.Status status;
        /** Vacía salvo en READY. */
        @NonNull public final List<InboxRows.Row> rows;

        Screen(@NonNull InboxState.Status status, @NonNull List<InboxRows.Row> rows) {
            this.status = status;
            this.rows = rows;
        }
    }

    private final InboxRepository inbox;
    private final MediatorLiveData<Screen> screen = new MediatorLiveData<>();
    private final LiveData<InboxState> inboxState;
    private final LiveData<FriendsState<FriendCard>> friendsState;
    private final LiveData<FriendsState<FriendRequest>> incomingState;
    /** Sin leer en algún momento de esta visita. Solo en el hilo principal. */
    private final Set<String> newIds = new HashSet<>();
    private boolean openHandled;

    @Inject
    public InboxViewModel(InboxRepository inbox, FriendsRepository friends) {
        this.inbox = inbox;
        this.inboxState = inbox.observeInbox();
        this.friendsState = friends.observeFriends();
        this.incomingState = friends.observeIncoming();
        screen.addSource(inboxState, s -> recompute());
        screen.addSource(friendsState, s -> recompute());
        screen.addSource(incomingState, s -> recompute());
    }

    @NonNull
    public LiveData<Screen> getScreen() {
        return screen;
    }

    /** Tocó una notificación: ese aviso queda leído (una vez por visita). */
    public void openFromNotification(@Nullable String inboxId) {
        if (openHandled || inboxId == null || inboxId.isEmpty()) {
            return;
        }
        openHandled = true;
        newIds.add(inboxId);
        inbox.markRead(inboxId);
    }

    private void recompute() {
        InboxState state = inboxState.getValue();
        if (state == null) {
            return;
        }
        if (state.status != InboxState.Status.READY) {
            screen.setValue(new Screen(state.status, Collections.<InboxRows.Row>emptyList()));
            return;
        }
        Set<String> unread = InboxRows.unreadIds(state.items);
        if (!unread.isEmpty()) {
            newIds.addAll(unread);
            inbox.markAllRead();
        }
        FriendsState<FriendCard> friends = friendsState.getValue();
        Set<String> friendUids = new HashSet<>();
        if (friends != null && friends.status == FriendsState.Status.READY) {
            for (FriendCard card : friends.items) {
                friendUids.add(card.uid);
            }
        }
        // Las solicitudes pendientes (las que encienden el punto) siempre tienen fila, con o sin aviso propio.
        FriendsState<FriendRequest> incoming = incomingState.getValue();
        List<InboxItem> items = incoming != null && incoming.status == FriendsState.Status.READY
                ? InboxRows.withPendingRequests(state.items, incoming.items) : state.items;
        screen.setValue(new Screen(InboxState.Status.READY,
                InboxRows.build(items, friendUids, newIds, System.currentTimeMillis())));
    }
}
