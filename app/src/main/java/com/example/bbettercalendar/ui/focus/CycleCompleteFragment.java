package com.example.bbettercalendar.ui.focus;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

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
import com.example.bbettercalendar.social.SocialRank;
import com.example.bbettercalendar.social.SocialSnapshot;
import com.example.bbettercalendar.stats.StreakCalculator;
import com.example.bbettercalendar.ui.common.widget.AvatarView;
import com.example.bbettercalendar.ui.home.FocusTarget;

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

    private TextView streakValue;
    private TextView streakSub;
    private TextView bestValue;
    private TextView bestSub;
    private View streakBadge;
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
        bestValue = view.findViewById(R.id.cycleBestValue);
        bestSub = view.findViewById(R.id.cycleBestSub);
        streakBadge = view.findViewById(R.id.cycleStreakBadge);
        friendsStrip = view.findViewById(R.id.cycleFriendsStrip);
        friendsText = view.findViewById(R.id.cycleFriendsText);
        avatars = new AvatarView[] {
                view.findViewById(R.id.cycleAvatar1),
                view.findViewById(R.id.cycleAvatar2),
                view.findViewById(R.id.cycleAvatar3)};

        bindSession(view);

        view.findViewById(R.id.cycleContinueButton).setOnClickListener(v -> finish());
        view.findViewById(R.id.cycleCloseButton).setOnClickListener(v -> finish());
        // PLACEHOLDER(ui-placeholders#cycle-share): no share flow yet.
        view.findViewById(R.id.cycleShareButton).setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.cycle_share_soon, Toast.LENGTH_SHORT).show());
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        finish();
                    }
                });

        // Social blocks: GONE (no gap) with the feature off; the Best tile is the third weighted
        // tile, its gap goes with it.
        SocialFeature.applyVisibility(view.findViewById(R.id.cycleBestTile),
                view.findViewById(R.id.cycleBestGap), friendsStrip);

        viewModel.getStreak().observe(getViewLifecycleOwner(), this::renderStreak);
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

        String count = getResources().getQuantityString(R.plurals.cycle_pomodoros, cycles, cycles);
        String title = FocusTarget.isSet() ? FocusTarget.getTitle() : null;
        boolean hasTask = !TextUtils.isEmpty(title);
        String line = getString(hasTask
                ? R.string.cycle_finished_on_format : R.string.cycle_finished_format, count);
        SpannableStringBuilder summary = new SpannableStringBuilder(line);
        int at = line.indexOf(count);
        if (at >= 0) {
            int end = at + count.length();
            summary.setSpan(new ForegroundColorSpan(
                            ContextCompat.getColor(requireContext(), R.color.bb_on_surface)),
                    at, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            summary.setSpan(new StyleSpan(Typeface.BOLD), at, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        ((TextView) view.findViewById(R.id.cycleSummary)).setText(summary);

        TextView chip = view.findViewById(R.id.cycleTaskChip);
        chip.setText(title);
        chip.setVisibility(hasTask ? View.VISIBLE : View.GONE);

        ((TextView) view.findViewById(R.id.cycleFocusValue)).setText(
                FormatHelper.formatDuration(cycles * focusMillis));
        ((TextView) view.findViewById(R.id.cycleFocusSub)).setText(R.string.cycle_tile_focus_sub);
    }

    private void renderStreak(@Nullable StreakCalculator.Result streak) {
        if (streakValue == null) {
            return;
        }
        int days = streak == null ? 0 : streak.currentStreak;
        int best = streak == null ? 0 : streak.bestStreak;
        streakValue.setText(getResources().getQuantityString(R.plurals.focus_streak_days, days, days));
        if (days >= best) {
            streakSub.setText(R.string.cycle_streak_personal_best);
        } else {
            streakSub.setText(getString(R.string.cycle_streak_best_format,
                    getResources().getQuantityString(R.plurals.focus_streak_days, best, best)));
        }
        // +1 = the session that just ended counted today (assumed; see notes u15).
        streakBadge.setVisibility(streak != null && streak.activeToday ? View.VISIBLE : View.GONE);
    }

    // PLACEHOLDER(ui-placeholders#social-backend): maqueta (MockSocialRepository).
    private void renderSocial(@Nullable SocialSnapshot snapshot) {
        if (snapshot == null || friendsText == null) {
            return;
        }
        SocialRank rank = snapshot.getUserRank();
        bestValue.setText(rank == null ? "-" : ordinal(rank.getPosition()));
        bestSub.setText(R.string.cycle_tile_best_sub);

        List<Friend> studying = snapshot.getStudying();
        if (studying.isEmpty()) {
            friendsStrip.setVisibility(View.GONE);
            return;
        }
        friendsStrip.setVisibility(View.VISIBLE);
        for (int i = 0; i < avatars.length; i++) {
            if (i < studying.size()) {
                avatars[i].setVisibility(View.VISIBLE);
                avatars[i].setName(studying.get(i).getName());
                String initials = studying.get(i).getInitials();
                avatars[i].setInitials(initials == null || initials.isEmpty() ? "" : initials.substring(0, 1));
            } else {
                avatars[i].setVisibility(View.GONE);
            }
        }
        String first = studying.get(0).getName();
        int others = studying.size() - 1;
        String text = others == 0
                ? getString(R.string.cycle_friend_one_format, first)
                : getResources().getQuantityString(R.plurals.cycle_friends_format, others, first, others);
        SpannableStringBuilder styled = new SpannableStringBuilder(text);
        int at = text.indexOf(first);
        if (at >= 0) {
            styled.setSpan(new ForegroundColorSpan(
                            ContextCompat.getColor(requireContext(), R.color.bb_on_surface)),
                    at, at + first.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        friendsText.setText(styled);
    }

    private String ordinal(int n) {
        int mod100 = n % 100;
        int mod10 = n % 10;
        int res = R.string.cycle_rank_th;
        if (mod100 < 11 || mod100 > 13) {
            if (mod10 == 1) {
                res = R.string.cycle_rank_st;
            } else if (mod10 == 2) {
                res = R.string.cycle_rank_nd;
            } else if (mod10 == 3) {
                res = R.string.cycle_rank_rd;
            }
        }
        return getString(res, n);
    }

    /** Continue / close / back: the session shows idle again and Home is next on the stack. */
    private void finish() {
        session.resetIdleDisplay();
        if (!NavHostFragment.findNavController(this).navigateUp()) {
            requireActivity().finish();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        streakValue = null;
        streakSub = null;
        bestValue = null;
        bestSub = null;
        streakBadge = null;
        friendsStrip = null;
        friendsText = null;
        avatars = null;
    }
}
