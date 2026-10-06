package com.example.bbettercalendar.ui.focus;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.bbettercalendar.social.SocialRepository;
import com.example.bbettercalendar.social.SocialSnapshot;
import com.example.bbettercalendar.stats.FocusInsightsRepository;
import com.example.bbettercalendar.stats.StreakCalculator;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * Screen data of the Focus screen (u14) that is NOT the session itself — the session (timer,
 * controls, today's fails/time, one-shot outcomes) is the activity-scoped
 * {@link FocusSessionViewModel}. Fragment-scoped.
 */
@HiltViewModel
public class FocusViewModel extends ViewModel {

    private final LiveData<StreakCalculator.Result> streak;
    private final SocialRepository socialRepository;

    @Inject
    public FocusViewModel(FocusInsightsRepository focusInsightsRepository,
                          SocialRepository socialRepository) {
        // Room observa focus_event en su executor; el map sólo recorre la lista en memoria (u3).
        this.streak = focusInsightsRepository.observeStreak();
        this.socialRepository = socialRepository;
    }

    /** Consecutive-days streak (StreakCalculator, u3) for the Today / Streak strip. */
    public LiveData<StreakCalculator.Result> getStreak() {
        return streak;
    }

    // PLACEHOLDER(ui-placeholders#social-focus-studying): maqueta (MockSocialRepository).
    public LiveData<SocialSnapshot> getSocialSnapshot() {
        return socialRepository.getSnapshot();
    }
}
