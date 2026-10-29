package com.example.bbettercalendar.ui.social;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.bbettercalendar.social.friends.FriendCard;
import com.example.bbettercalendar.social.stats.RankingRepository;
import com.example.bbettercalendar.social.stats.RankingState;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * F3-u6: el perfil de un amigo. Solo lee: {@link RankingRepository#observeFriendStats(String)} ya trae la
 * semántica del ranking aplicada y {@link FriendProfileUi} la pasa a lo que se ve. Eliminar y reportar los hace
 * {@link FriendsViewModel}, el mismo de la lista.
 */
@HiltViewModel
public class FriendProfileViewModel extends ViewModel {

    private final RankingRepository ranking;

    @Inject
    public FriendProfileViewModel(RankingRepository ranking) {
        this.ranking = ranking;
    }

    /** La misma LiveData por uid (el repositorio la reutiliza); solo hay listener mientras se observa. */
    @NonNull
    public LiveData<RankingState.FriendStatsState> observe(@NonNull String uid) {
        return ranking.observeFriendStats(uid);
    }

    /** La tarjeta con datos frescos si el repositorio ya la conoce; si no, la de los argumentos. */
    @NonNull
    static FriendCard cardOrFallback(RankingState.FriendStatsState state, @NonNull FriendCard fallback) {
        return state != null && state.card != null ? state.card : fallback;
    }
}
