package com.example.bbettercalendar.ui.focus;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.social.SocialRepository;
import com.example.bbettercalendar.social.SocialRank;
import com.example.bbettercalendar.social.SocialSnapshot;
import com.example.bbettercalendar.social.stats.RankingRepository;
import com.example.bbettercalendar.stats.DayMinutes;
import com.example.bbettercalendar.stats.FocusEventDAO;
import com.example.bbettercalendar.stats.FocusInsightsRepository;
import com.example.bbettercalendar.stats.StreakCalculator;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.Lazy;
import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * u15: data of the Cycle complete screen that is not in the navigation arguments: the streak
 * (current + best, u3) and the social snapshot for the strip / Best tile. Fragment-scoped; the session
 * itself (cycles, length) arrives as arguments from {@link FocusSessionEventPresenter}.
 */
@HiltViewModel
public class CycleCompleteViewModel extends ViewModel {

    private final LiveData<StreakCalculator.Result> streak;
    private final SocialRepository socialRepository;
    private final Lazy<RankingRepository> rankingRepository;
    private final MutableLiveData<DayTotals> dayTotals = new MutableLiveData<>();

    /** u13: minutes of focus today and the best day ever (today included), for the "Hoy" cell. */
    public static final class DayTotals {
        public final int todayMinutes;
        public final int bestMinutes;

        DayTotals(int todayMinutes, int bestMinutes) {
            this.todayMinutes = todayMinutes;
            this.bestMinutes = bestMinutes;
        }
    }

    @Inject
    public CycleCompleteViewModel(FocusInsightsRepository focusInsightsRepository,
                                  SocialRepository socialRepository,
                                  Lazy<RankingRepository> rankingRepository,
                                  FocusEventDAO focusEventDao,
                                  @IoExecutor ExecutorService ioExecutor) {
        this.streak = focusInsightsRepository.observeStreak();
        this.socialRepository = socialRepository;
        this.rankingRepository = rankingRepository;
        // Rule #3: Room work on the executor, result through postValue.
        ioExecutor.execute(() -> {
            Calendar start = Calendar.getInstance();
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
            int today = 0;
            for (DayMinutes d : focusEventDao.getFocusMinutesPerDayBetween(
                    start.getTimeInMillis(), Long.MAX_VALUE / 2)) {
                today += d.minutes;
            }
            List<DayMinutes> all = focusEventDao.getFocusMinutesPerDayBetween(0L, Long.MAX_VALUE / 2);
            int best = 0;
            for (DayMinutes d : all) {
                best = Math.max(best, d.minutes);
            }
            dayTotals.postValue(new DayTotals(today, Math.max(best, today)));
        });
    }

    public LiveData<DayTotals> getDayTotals() {
        return dayTotals;
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

    /** F4-u4: the single social snapshot, for the "still studying" strip — 03 §10. */
    public LiveData<SocialSnapshot> getSocial() {
        return socialRepository.getSnapshot();
    }
}
