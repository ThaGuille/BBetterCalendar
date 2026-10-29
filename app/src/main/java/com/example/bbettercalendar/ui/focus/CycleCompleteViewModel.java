package com.example.bbettercalendar.ui.focus;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.example.bbettercalendar.social.SocialRepository;
import com.example.bbettercalendar.social.SocialRank;
import com.example.bbettercalendar.social.SocialSnapshot;
import com.example.bbettercalendar.social.stats.RankingRepository;
import com.example.bbettercalendar.stats.FocusInsightsRepository;
import com.example.bbettercalendar.stats.StreakCalculator;

import javax.inject.Inject;

import dagger.Lazy;
import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * u15: data of the Cycle complete screen that is not in the navigation arguments: the streak
 * (current + best, u3) and the social mock for the strip / Best tile. Fragment-scoped; the session
 * itself (cycles, length) arrives as arguments from {@link FocusSessionEventPresenter}.
 */
@HiltViewModel
public class CycleCompleteViewModel extends ViewModel {

    private final LiveData<StreakCalculator.Result> streak;
    private final SocialRepository socialRepository;
    private final Lazy<RankingRepository> rankingRepository;

    @Inject
    public CycleCompleteViewModel(FocusInsightsRepository focusInsightsRepository,
                                  SocialRepository socialRepository,
                                  Lazy<RankingRepository> rankingRepository) {
        this.streak = focusInsightsRepository.observeStreak();
        this.socialRepository = socialRepository;
        this.rankingRepository = rankingRepository;
    }

    public LiveData<StreakCalculator.Result> getStreak() {
        return streak;
    }

    /**
     * F3-u8: my real position in this week's Minutos ({@link CycleRank}); null when there is none (d5).
     * Only call with {@code SocialFeature.isAccountEnabled()}: release never touches Firebase from here.
     */
    public LiveData<SocialRank> getRank() {
        return Transformations.map(rankingRepository.get().observeRanking(), CycleRank::from);
    }

    // PLACEHOLDER(ui-placeholders#social-backend): maqueta (MockSocialRepository).
    public LiveData<SocialSnapshot> getSocial() {
        return socialRepository.getCelebrationSnapshot();
    }
}
