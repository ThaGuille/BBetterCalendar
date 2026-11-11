package com.example.bbettercalendar.ui.focus;

import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.example.bbettercalendar.social.Friend;
import com.example.bbettercalendar.social.SocialFeature;
import com.example.bbettercalendar.social.SocialSnapshot;
import com.example.bbettercalendar.stats.StreakCalculator;
import com.example.bbettercalendar.ui.calendar.binders.DayDetailAdapter;
import com.example.bbettercalendar.ui.common.motion.Motion;
import com.example.bbettercalendar.ui.common.share.ShareCardRenderer;
import com.example.bbettercalendar.ui.common.widget.AvatarView;
import com.example.bbettercalendar.ui.common.widget.CelebrationView;
import com.example.bbettercalendar.ui.home.FocusTarget;
import com.example.bbettercalendar.ui.social.AvatarPalette;
import com.example.bbettercalendar.ui.social.StudyingSummary;

import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * u15: Cycle complete (html 02-celebration). Opened by {@link FocusSessionEventPresenter} with the
 * arguments {@link #ARG_CYCLES} and {@link #ARG_FOCUS_MILLIS}. "Continue" (also the close pill and
 * system back) resets the session idle display and goes back to Home.
 *
 * <p>Social blocks (friends strip, Best tile) are {@link SocialFeature}-gated and GONE without a
 * gap when the feature is off.
 */
@AndroidEntryPoint
public class CycleCompleteFragment extends Fragment {

    public static final String ARG_CYCLES = "cycles";
    public static final String ARG_FOCUS_MILLIS = "focusMillis";

    private FocusSessionViewModel session;
    private CycleCompleteViewModel viewModel;

    private final ShareCardRenderer shareRenderer = new ShareCardRenderer();
    // What the screen shows, kept for the share card.
    private int shareCycles;
    private long shareFocusMillis;
    @Nullable private StreakCalculator.Result shareStreak;

    private TextView streakValue;
    private TextView streakSub;
    private TextView focusValue;
    private TextView focusSub;
    private long shownFocusMinutes;
    private int shownWeeks;
    private long sessionFloorMinutes;
    @Nullable private StreakCalculator.Result latestStreak;
    private View friendsStrip;
    private TextView friendsText;
    private AvatarView[] avatars;

    public CycleCompleteFragment() {
        super(R.layout.fragment_cycle_complete);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        session = new ViewModelProvider(requireActivity()).get(FocusSessionViewModel.class);
        viewModel = new ViewModelProvider(this).get(CycleCompleteViewModel.class);

        streakValue = view.findViewById(R.id.cycleStreakValue);
        streakSub = view.findViewById(R.id.cycleStreakSub);
        focusValue = view.findViewById(R.id.cycleFocusValue);
        focusSub = view.findViewById(R.id.cycleFocusSub);
        // u13: the ring completes once per entry; after a rotation it is drawn whole, without the burst.
        CelebrationView celebration = view.findViewById(R.id.cycleCelebration);
        if (savedInstanceState == null) {
            celebration.play();
        } else {
            celebration.showComplete();
        }
        friendsStrip = view.findViewById(R.id.cycleFriendsStrip);
        friendsText = view.findViewById(R.id.cycleFriendsText);
        avatars = new AvatarView[] {
                view.findViewById(R.id.cycleAvatar1),
                view.findViewById(R.id.cycleAvatar2),
                view.findViewById(R.id.cycleAvatar3)};

        bindSession(view);

        view.findViewById(R.id.cycleContinueButton).setOnClickListener(v -> finish());
        view.findViewById(R.id.cycleCloseButton).setOnClickListener(v -> finish());
        // F0-u5: image card of this session + text-only ACTION_SEND (Q-06, no link).
        View shareButton = view.findViewById(R.id.cycleShareButton);
        shareRenderer.attach(this, shareButton);
        shareButton.setOnClickListener(v -> shareRenderer.share(requireActivity(), buildShareCard()));
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        finish();
                    }
                });

        // Social block: GONE (no gap) with the feature off.
        SocialFeature.applyVisibility(friendsStrip);

        viewModel.getStreak().observe(getViewLifecycleOwner(), this::renderStreak);
        viewModel.getDayTotals().observe(getViewLifecycleOwner(), this::renderToday);
        if (SocialFeature.isEnabled()) {
            viewModel.getSocial().observe(getViewLifecycleOwner(), this::renderSocial);
        }
    }

    /** "You finished 4 Pomodoros [on task]" + Focus tile (cycles x configured length). */
    private void bindSession(@NonNull View view) {
        Bundle args = getArguments();
        int cycles = args == null ? 0 : args.getInt(ARG_CYCLES, 0);
        long focusMillis = args == null ? 0L : args.getLong(ARG_FOCUS_MILLIS, 0L);
        if (cycles <= 0) {
            FocusUiState state = session.getUiState().getValue();
            cycles = state == null ? 0 : state.getCyclesCompleted();
        }

        shareCycles = cycles;
        shareFocusMillis = focusMillis;

        String count = getResources().getQuantityString(R.plurals.cycle_pomodoros, cycles, cycles);
        String title = FocusTarget.isSet() ? FocusTarget.getTitle() : null;
        boolean hasTask = !TextUtils.isEmpty(title);
        long sessionMinutes = cycles * focusMillis / 60000L;
        String line = getString(R.string.cycle_summary_format, count,
                DayDetailAdapter.formatDuration(requireContext(), sessionMinutes));
        ((TextView) view.findViewById(R.id.cycleSummary)).setText(line);

        TextView chip = view.findViewById(R.id.cycleTaskChip);
        chip.setText(title);
        chip.setVisibility(hasTask ? View.VISIBLE : View.GONE);
        sessionFloorMinutes = sessionMinutes;
    }

    /** "Hoy": minutes of focus today (never below this cycle) and the best day ever. */
    private void renderToday(@Nullable CycleCompleteViewModel.DayTotals totals) {
        if (focusValue == null || totals == null) {
            return;
        }
        long today = Math.max(totals.todayMinutes, sessionFloorMinutes);
        long best = Math.max(totals.bestMinutes, today);
        Motion.countUp(focusValue, shownFocusMinutes, today,
                v -> DayDetailAdapter.formatDuration(requireContext(), v));
        shownFocusMinutes = today;
        focusSub.setText(today >= best
                ? getString(R.string.cycle_today_best_now)
                : getString(R.string.cycle_today_best_format,
                        DayDetailAdapter.formatDuration(requireContext(), best)
                                .replace(' ', ' ')));
    }

    private void renderStreak(@Nullable StreakCalculator.Result streak) {
        if (streakValue == null) {
            return;
        }
        shareStreak = streak;
        latestStreak = streak;
        // Weekly streak (plan racha-semanal-y-constancia): weeks in a row meeting the goal.
        int weeks = streak == null ? 0 : streak.weekStreak;
        Motion.countUp(streakValue, shownWeeks, weeks,
                v -> getResources().getQuantityString(R.plurals.streak_weeks, (int) v, (int) v));
        shownWeeks = weeks;
        if (streak == null) {
            streakSub.setText("");
        } else {
            streakSub.setText(getString(streak.goalMetThisWeek
                            ? R.string.cycle_streak_goal_met : R.string.cycle_streak_goal_progress,
                    streak.daysThisWeek, streak.goal));
        }
    }

    /** F4-u6: strip "Laia and N others still studying · Live", only while somebody studies. */
    private void renderSocial(@Nullable SocialSnapshot snapshot) {
        if (snapshot == null || friendsText == null) {
            return;
        }
        StudyingSummary summary = StudyingSummary.of(snapshot, avatars.length);
        if (summary.isNobodyStudying()) {
            friendsStrip.setVisibility(View.GONE);
            return;
        }
        friendsStrip.setVisibility(View.VISIBLE);
        List<Friend> shown = summary.getShown();
        for (int i = 0; i < avatars.length; i++) {
            if (i < shown.size()) {
                Friend friend = shown.get(i);
                avatars[i].setVisibility(View.VISIBLE);
                avatars[i].setName(friend.getName());
                String initials = friend.getInitials();
                avatars[i].setInitials(initials.isEmpty() ? "" : initials.substring(0, 1));
                avatars[i].setFillColor(AvatarPalette.color(requireContext(), friend.getAvatarColor()));
                avatars[i].setInitialsColor(AvatarPalette.onColor(requireContext()));
            } else {
                avatars[i].setVisibility(View.GONE);
            }
        }
        String first = summary.getLeadName();
        int others = summary.getOthersCount();
        String text = others == 0
                ? getString(R.string.cycle_friend_one_format, first)
                : getResources().getQuantityString(R.plurals.cycle_friends_format, others, first, others);
        SpannableStringBuilder styled = new SpannableStringBuilder(text);
        int at = text.indexOf(first);
        if (at >= 0) {
            styled.setSpan(new ForegroundColorSpan(
                            ContextCompat.getColor(requireContext(), R.color.bb_sanct_on_surface)),
                    at, at + first.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        friendsText.setText(styled);
    }

    /** Same values the screen shows: focus tile (cycles x length), cycles and the weekly streak. */
    private ShareCardRenderer.Card buildShareCard() {
        String focus = FormatHelper.formatDuration(shareCycles * shareFocusMillis);
        int weeks = shareStreak == null ? 0 : shareStreak.weekStreak;
        return new ShareCardRenderer.Card(
                getString(R.string.share_cycle_eyebrow),
                focus,
                getString(R.string.share_cycle_hero_sub),
                getResources().getQuantityString(R.plurals.cycle_pomodoros, shareCycles, shareCycles),
                getString(R.string.share_cycle_stat_cycles_label),
                getResources().getQuantityString(R.plurals.streak_weeks, weeks, weeks),
                getString(R.string.share_cycle_stat_streak_label),
                getString(R.string.share_cycle_text, focus),
                "bbetter-cycle-card.png");
    }

    /** Continue / close / back: the session shows idle again and Home is next on the stack. */
    private void finish() {
        session.resetIdleDisplay();
        // u13 (d3): the short goal celebration, once per ISO week, over Home. Claimed before leaving.
        boolean goalMet = latestStreak != null && latestStreak.goalMetThisWeek;
        if (CelebrationPrefs.claimWeeklyGoal(requireContext(), goalMet)) {
            CelebrationSheet.show(requireActivity().getSupportFragmentManager(),
                    getString(R.string.celebration_week_title),
                    getString(R.string.celebration_week_message));
        }
        if (!NavHostFragment.findNavController(this).navigateUp()) {
            requireActivity().finish();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        streakValue = null;
        streakSub = null;
        focusValue = null;
        focusSub = null;
        friendsStrip = null;
        friendsText = null;
        avatars = null;
    }
}
