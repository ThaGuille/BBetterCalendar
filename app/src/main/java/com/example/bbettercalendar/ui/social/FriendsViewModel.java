package com.example.bbettercalendar.ui.social;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.social.common.ListenerException;
import com.example.bbettercalendar.social.friends.FriendCard;
import com.example.bbettercalendar.social.friends.FriendRequest;
import com.example.bbettercalendar.social.friends.FriendsException;
import com.example.bbettercalendar.social.friends.FriendsRepository;
import com.example.bbettercalendar.social.friends.FriendsState;
import com.example.bbettercalendar.social.friends.ReportReason;
import com.example.bbettercalendar.social.friends.ReportRepository;
import com.example.bbettercalendar.social.profile.ProfileRepository;
import com.example.bbettercalendar.social.profile.ProfileState;
import com.example.bbettercalendar.social.stats.RankingRepository;
import com.example.bbettercalendar.social.stats.RankingState;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * F2-u7: lógica de la pantalla Amigos y de sus dos hojas (añadir y reportar). Une las tres listas de
 * {@link FriendsRepository} en una sola ({@link Screen}) y convierte cada acción en un {@link Event}
 * con el texto que toca. Los callbacks llegan en el hilo de E/S, así que todo sale con postValue (regla #3).
 * El ViewModel es el del {@code FriendsFragment}; las hojas lo comparten a través de su fragmento padre.
 */
@HiltViewModel
public class FriendsViewModel extends ViewModel {

    /** Una fila de la lista: cabecera de sección o una persona. */
    public static final class Row {
        public enum Type { HEADER, REQUEST, FAILED, FRIEND, SENT }

        @NonNull public final Type type;
        /** Cabecera: texto con el número ya dentro. */
        @StringRes public final int titleRes;
        public final int count;
        /** Personas: la tarjeta de la otra persona. */
        @Nullable public final FriendCard card;
        /** Solicitudes (recibidas, fallidas y enviadas). */
        @Nullable public final FriendRequest request;

        private Row(@NonNull Type type, int titleRes, int count, @Nullable FriendCard card,
                    @Nullable FriendRequest request) {
            this.type = type;
            this.titleRes = titleRes;
            this.count = count;
            this.card = card;
            this.request = request;
        }

        static Row header(@StringRes int title, int count) {
            return new Row(Type.HEADER, title, count, null, null);
        }

        static Row friend(@NonNull FriendCard card) {
            return new Row(Type.FRIEND, 0, 0, card, null);
        }

        static Row request(@NonNull FriendRequest r, boolean failed) {
            return new Row(failed ? Type.FAILED : Type.REQUEST, 0, 0, r.from, r);
        }

        static Row sent(@NonNull FriendRequest r) {
            return new Row(Type.SENT, 0, 0, r.to, r);
        }
    }

    /** Todo lo que pinta la pantalla. */
    public static final class Screen {
        public enum Status { LOADING, READY, ERROR }

        @NonNull public final Status status;
        @NonNull public final List<Row> rows;
        public final int friendCount;
        public final boolean empty;

        Screen(@NonNull Status status, @NonNull List<Row> rows, int friendCount, boolean empty) {
            this.status = status;
            this.rows = rows;
            this.friendCount = friendCount;
            this.empty = empty;
        }
    }

    /** Un aviso para el fragmento: un texto, y a veces la oferta de eliminar a alguien. */
    public static final class Event {
        @StringRes public final int message;
        @Nullable public final String arg;
        /** Tras reportar desde la lista: ofrecer «¿Eliminar también a X?». */
        @Nullable public final FriendCard offerRemove;

        Event(@StringRes int message, @Nullable String arg, @Nullable FriendCard offerRemove) {
            this.message = message;
            this.arg = arg;
            this.offerRemove = offerRemove;
        }
    }

    /** Estado de la hoja Añadir. */
    public static final class AddState {
        public enum Step { IDLE, LOOKING, PREVIEW, MESSAGE, SENDING, SENT }

        @NonNull public final Step step;
        @Nullable public final FriendCard card;
        @StringRes public final int message;

        AddState(@NonNull Step step, @Nullable FriendCard card, @StringRes int message) {
            this.step = step;
            this.card = card;
            this.message = message;
        }
    }

    /** Estado de la hoja Reportar. */
    public static final class ReportState {
        public enum Step { IDLE, SENDING, DONE, ERROR }

        @NonNull public final Step step;
        @StringRes public final int message;

        ReportState(@NonNull Step step, @StringRes int message) {
            this.step = step;
            this.message = message;
        }
    }

    private final FriendsRepository friends;
    private final ReportRepository reports;

    private final MediatorLiveData<Screen> screen = new MediatorLiveData<>();
    private final LiveData<FriendsState<FriendCard>> friendsState;
    private final LiveData<FriendsState<FriendRequest>> incomingState;
    private final LiveData<FriendsState<FriendRequest>> outgoingState;
    private final MutableLiveData<Event> event = new MutableLiveData<>();
    private final MutableLiveData<AddState> addState =
            new MutableLiveData<>(new AddState(AddState.Step.IDLE, null, 0));
    private final MutableLiveData<ReportState> reportState =
            new MutableLiveData<>(new ReportState(ReportState.Step.IDLE, 0));
    private final LiveData<Integer> friendCount;
    /** F3-u7: pestaña Ranking. Las fuentes solo escuchan mientras el fragmento observa {@link #getRankingRows}. */
    private final RankingRepository ranking;
    private final MediatorLiveData<RankingRows> rankingRows = new MediatorLiveData<>();
    private final MutableLiveData<RankingRows.Category> category =
            new MutableLiveData<>(RankingRows.Category.MINUTES);
    private final MutableLiveData<Boolean> rankingTab = new MutableLiveData<>(false);

    @Inject
    public FriendsViewModel(FriendsRepository friends, ReportRepository reports, RankingRepository ranking,
                            ProfileRepository profiles) {
        this.friends = friends;
        this.reports = reports;
        this.ranking = ranking;
        LiveData<RankingState> rankingState = ranking.observeRanking();
        LiveData<ProfileState> mine = profiles.observeMine();
        rankingRows.addSource(rankingState, s -> recomputeRanking(rankingState, mine));
        rankingRows.addSource(mine, s -> recomputeRanking(rankingState, mine));
        rankingRows.addSource(category, c -> recomputeRanking(rankingState, mine));
        friendsState = friends.observeFriends();
        incomingState = friends.observeIncoming();
        outgoingState = friends.observeOutgoing();
        friendCount = Transformations.map(friendsState,
                s -> s != null && s.status == FriendsState.Status.READY ? s.items.size() : 0);
        screen.addSource(friendsState, s -> recompute());
        screen.addSource(incomingState, s -> recompute());
        screen.addSource(outgoingState, s -> recompute());
    }

    public LiveData<Screen> getScreen() {
        return screen;
    }

    // ---- ranking (F3-u7) --------------------------------------------------------------------------------

    public LiveData<RankingRows> getRankingRows() {
        return rankingRows;
    }

    public LiveData<RankingRows.Category> getCategory() {
        return category;
    }

    public void setCategory(@NonNull RankingRows.Category value) {
        category.setValue(value);
    }

    /** La pestaña elegida (false = Amigos, true = Ranking); sobrevive a un giro de pantalla. */
    public LiveData<Boolean> isRankingTab() {
        return rankingTab;
    }

    public void setRankingTab(boolean ranking) {
        rankingTab.setValue(ranking);
    }

    /** «Reintentar» del estado de error del ranking. */
    public void retryRanking() {
        ranking.refresh();
    }

    private void recomputeRanking(@NonNull LiveData<RankingState> state, @NonNull LiveData<ProfileState> mine) {
        ProfileState p = mine.getValue();
        String name = null;
        int color = 0;
        if (p != null && p.status == ProfileState.Status.READY && p.profile != null) {
            name = p.profile.displayName;
            color = p.profile.avatarColor;
        }
        RankingRows.Category c = category.getValue();
        rankingRows.setValue(RankingRows.from(state.getValue(), c == null ? RankingRows.Category.MINUTES : c,
                name, color));
    }

    /** Cuántos amigos hay (0 mientras carga): la fila «Amigos (N)» de Mi perfil. */
    public LiveData<Integer> getFriendCount() {
        return friendCount;
    }

    public LiveData<Event> getEvent() {
        return event;
    }

    public LiveData<AddState> getAddState() {
        return addState;
    }

    public LiveData<ReportState> getReportState() {
        return reportState;
    }

    /** Llamar al atender el aviso (hilo principal). */
    public void consumeEvent() {
        event.setValue(null);
    }

    // ---- lista ------------------------------------------------------------------------------------------

    private void recompute() {
        FriendsState<FriendCard> f = friendsState.getValue();
        FriendsState<FriendRequest> in = incomingState.getValue();
        FriendsState<FriendRequest> out = outgoingState.getValue();
        if (f == null || in == null || out == null) {
            return;
        }
        if (f.status == FriendsState.Status.ERROR || in.status == FriendsState.Status.ERROR
                || out.status == FriendsState.Status.ERROR) {
            screen.setValue(new Screen(Screen.Status.ERROR, new ArrayList<Row>(), 0, false));
            return;
        }
        if (f.status != FriendsState.Status.READY || in.status != FriendsState.Status.READY
                || out.status != FriendsState.Status.READY) {
            screen.setValue(new Screen(Screen.Status.LOADING, new ArrayList<Row>(), 0, false));
            return;
        }
        List<Row> rows = new ArrayList<>();
        if (!in.items.isEmpty()) {
            rows.add(Row.header(R.string.friends_section_requests, in.items.size()));
            for (FriendRequest r : in.items) {
                rows.add(Row.request(r, r.status == FriendRequest.Status.FAILED));
            }
        }
        if (!f.items.isEmpty()) {
            rows.add(Row.header(R.string.friends_section_friends, f.items.size()));
            for (FriendCard c : f.items) {
                rows.add(Row.friend(c));
            }
        }
        if (!out.items.isEmpty()) {
            rows.add(Row.header(R.string.friends_section_sent, out.items.size()));
            for (FriendRequest r : out.items) {
                rows.add(Row.sent(r));
            }
        }
        screen.setValue(new Screen(Screen.Status.READY, rows, f.items.size(), rows.isEmpty()));
    }

    // ---- acciones de la lista ------------------------------------------------------------------------

    public void accept(@NonNull FriendRequest request) {
        friends.accept(request, done(R.string.friends_toast_accepted, request.from.displayName));
    }

    /** Rechazar una recibida o descartar una fallida. */
    public void decline(@NonNull FriendRequest request) {
        friends.decline(request, done(R.string.friends_toast_declined, null));
    }

    public void cancel(@NonNull FriendRequest request) {
        friends.cancel(request, done(R.string.friends_toast_cancelled, null));
    }

    public void remove(@NonNull FriendCard friend) {
        friends.remove(friend.uid, done(R.string.friends_toast_removed, friend.displayName));
    }

    private FriendsRepository.Callback done(@StringRes int message, @Nullable String arg) {
        return new FriendsRepository.Callback() {
            @Override
            public void onSuccess() {
                event.postValue(new Event(message, arg, null));
            }

            @Override
            public void onError(@NonNull Throwable error) {
                event.postValue(new Event(errorText(error), null, null));
            }
        };
    }

    // ---- hoja Añadir ----------------------------------------------------------------------------------

    public void resetAdd() {
        addState.setValue(new AddState(AddState.Step.IDLE, null, 0));
    }

    public void lookup(@NonNull String raw) {
        addState.setValue(new AddState(AddState.Step.LOOKING, null, 0));
        friends.lookupHandle(raw, new FriendsRepository.LookupCallback() {
            @Override
            public void onResult(@NonNull FriendsRepository.LookupResult result) {
                int message;
                switch (result.kind) {
                    case OK:
                        addState.postValue(new AddState(AddState.Step.PREVIEW, result.card, 0));
                        return;
                    case SELF:
                        message = R.string.friends_add_self;
                        break;
                    case ALREADY_FRIENDS:
                        message = R.string.friends_add_already_friends;
                        break;
                    case ALREADY_SENT:
                        message = R.string.friends_add_already_sent;
                        break;
                    case THEY_SENT_YOU:
                        message = R.string.friends_add_they_sent;
                        break;
                    case NOT_FOUND:
                    default:
                        message = R.string.friends_add_not_found;
                        break;
                }
                addState.postValue(new AddState(AddState.Step.MESSAGE, null, message));
            }

            @Override
            public void onError(@NonNull Throwable error) {
                addState.postValue(new AddState(AddState.Step.MESSAGE, null, errorText(error)));
            }
        });
    }

    public void send(@NonNull FriendCard card) {
        addState.setValue(new AddState(AddState.Step.SENDING, card, 0));
        friends.send(card, new FriendsRepository.Callback() {
            @Override
            public void onSuccess() {
                addState.postValue(new AddState(AddState.Step.SENT, card, 0));
                event.postValue(new Event(R.string.friends_toast_sent, card.displayName, null));
            }

            @Override
            public void onError(@NonNull Throwable error) {
                addState.postValue(new AddState(AddState.Step.MESSAGE, null, errorText(error)));
            }
        });
    }

    // ---- hoja Reportar ---------------------------------------------------------------------------------

    public void resetReport() {
        reportState.setValue(new ReportState(ReportState.Step.IDLE, 0));
    }

    /**
     * Reporta a {@code target}. Desde la lista ({@code context == FRIEND}) ofrece después eliminarlo;
     * desde una solicitud, el propio reporte ya la rechaza (mismo lote).
     */
    public void report(@NonNull FriendCard target, @NonNull ReportReason reason,
                       @NonNull ReportReason.Context context) {
        reportState.setValue(new ReportState(ReportState.Step.SENDING, 0));
        reports.report(target.uid, target.handle, reason, context, new ReportRepository.Callback() {
            @Override
            public void onReported(boolean alreadyReported) {
                reportState.postValue(new ReportState(ReportState.Step.DONE, 0));
                FriendCard offer = context == ReportReason.Context.FRIEND ? target : null;
                event.postValue(new Event(alreadyReported ? R.string.friends_toast_already_reported
                        : R.string.friends_toast_reported, null, offer));
            }

            @Override
            public void onError(@NonNull Throwable error) {
                reportState.postValue(new ReportState(ReportState.Step.ERROR, errorText(error)));
            }
        });
    }

    // ---- errores -----------------------------------------------------------------------------------------

    @StringRes
    static int errorText(@NonNull Throwable error) {
        if (error instanceof FriendsException) {
            switch (((FriendsException) error).code) {
                case DAILY_LIMIT:
                    return R.string.friends_error_daily_limit;
                case FRIEND_CAP:
                    return R.string.friends_error_own_cap;
                case NO_PROFILE:
                    return R.string.friends_error_no_profile;
                default:
                    return R.string.friends_error_generic;
            }
        }
        Throwable cause = error instanceof ListenerException ? ((ListenerException) error).original() : error;
        if (cause instanceof FirebaseNetworkException
                || (cause instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) cause).getCode() == FirebaseFirestoreException.Code.UNAVAILABLE)
                || (error instanceof ListenerException && "UNAVAILABLE".equals(((ListenerException) error).code))) {
            return R.string.friends_error_network;
        }
        return R.string.friends_error_generic;
    }
}
