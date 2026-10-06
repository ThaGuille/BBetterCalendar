package com.example.bbettercalendar.ui.focus;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.MenuItemCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.blocking.AccessibilityAccess;
import com.example.bbettercalendar.blocking.AccessibilityDisclosureDialog;
import com.example.bbettercalendar.feedback.HapticFeedback;
import com.example.bbettercalendar.feedback.SoundFeedback;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.example.bbettercalendar.social.Friend;
import com.example.bbettercalendar.social.SocialFeature;
import com.example.bbettercalendar.social.SocialSnapshot;
import com.example.bbettercalendar.stats.StreakCalculator;
import com.example.bbettercalendar.ui.common.TopBarHelper;
import com.example.bbettercalendar.ui.common.widget.AvatarView;
import com.example.bbettercalendar.ui.common.widget.SectionHeaderView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.Date;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Focus screen (u14, {@code navigation_focus}; .stitch html/PNG 01): the running-session UI.
 *
 * <p>The session itself is the <b>activity-scoped</b> {@link FocusSessionViewModel} shared with
 * Home, so leaving this screen (back arrow, system back) returns to Home <b>without</b> cancelling
 * anything. One-shot outcomes (all cycles done → Cycle complete, fail alert, first pomodoro of the
 * day, auto-completed task) go through {@link FocusSessionEventPresenter}, the same one Home uses.
 * Screen-only data (streak, social mock) comes from the fragment-scoped {@link FocusViewModel}.
 *
 * <p>Controls: stop = {@code cancelSession()} (not a fail); centre = start / pause / resume;
 * skip = {@code skipRest()} only during a rest (the button stays drawn as in the design and
 * explains itself with a toast during a concentration). The 🚫 block-mode toggle is the top-bar
 * action (html 01 trailing {@code do_not_disturb_on}).
 */
@AndroidEntryPoint
public class FocusFragment extends Fragment
        implements AccessibilityDisclosureDialog.OnConsentGrantedListener {

    private static final String TAG = "FocusFragment";
    private static final String DISCLOSURE_TAG = "accessibility_disclosure";
    private static final int FRIEND_COLUMNS = 4;
    private static final int MAX_FRIENDS = 8;
    private static final int MAX_CYCLE_DOTS = 12;
    private static final float AWAY_CELL_ALPHA = 0.4f;
    private static final float DISABLED_ALPHA = 0.4f;

    private FocusSessionViewModel session;
    private FocusViewModel viewModel;
    private FocusSessionEventPresenter sessionEvents;

    @Nullable private MaterialToolbar toolbar;
    private TextView modeChip;
    private LinearLayout cycleDots;
    private CircularProgressIndicator ring;
    private TextView timerText;
    private View endsAtIcon;
    private TextView endsAtText;
    private ImageButton stopButton;
    private ImageButton playButton;
    private ImageButton skipButton;
    private TextView todayText;
    private TextView failsText;
    private TextView streakText;
    private SectionHeaderView studyingHeader;
    private LinearLayout friendsGrid;

    // Último estado pintado: los controles sólo se repintan cuando cambia algo más que el tiempo.
    @Nullable private FocusUiState lastRendered;

    public FocusFragment() {
        super(R.layout.fragment_focus);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        session = new ViewModelProvider(requireActivity()).get(FocusSessionViewModel.class);
        viewModel = new ViewModelProvider(this).get(FocusViewModel.class);

        bindViews(view);
        setUpTopBar(view);

        stopButton.setOnClickListener(this::onStopTapped);
        playButton.setOnClickListener(this::onPlayTapped);
        skipButton.setOnClickListener(this::onSkipTapped);

        lastRendered = null;
        session.getUiState().observe(getViewLifecycleOwner(), this::render);
        session.getTodayTimeStudiedMillis().observe(getViewLifecycleOwner(), this::renderToday);
        session.getTodayFails().observe(getViewLifecycleOwner(), this::renderFails);
        viewModel.getStreak().observe(getViewLifecycleOwner(), this::renderStreak);

        // Un solo sitio convierte los resultados de un solo uso en UI (compartido con Home): el
        // primero de los dos que esté visible los consume, así ninguno se pierde.
        sessionEvents = new FocusSessionEventPresenter(this, session, null);
        sessionEvents.observe(getViewLifecycleOwner());

        // Maqueta social tras FEATURE_SOCIAL_MOCK: con el flag apagado, bloque e invitación GONE.
        View socialBlock = view.findViewById(R.id.focusSocialBlock);
        View invite = view.findViewById(R.id.focusInviteButton);
        SocialFeature.applyVisibility(socialBlock, invite);
        if (SocialFeature.isEnabled()) {
            viewModel.getSocialSnapshot().observe(getViewLifecycleOwner(), this::renderFriends);
            // PLACEHOLDER(ui-placeholders#social-focus-invite): no hay backend social.
            invite.setOnClickListener(v -> Toast.makeText(requireContext(),
                    R.string.focus_invite_soon, Toast.LENGTH_SHORT).show());
        }

        SoundFeedback.get(requireContext()); // warm singleton so the first tap is silent-fast
    }

    private void bindViews(@NonNull View view) {
        modeChip = view.findViewById(R.id.focusModeChip);
        cycleDots = view.findViewById(R.id.focusCycleDots);
        ring = view.findViewById(R.id.focusRing);
        timerText = view.findViewById(R.id.focusTimerText);
        endsAtIcon = view.findViewById(R.id.focusEndsAtIcon);
        endsAtText = view.findViewById(R.id.focusEndsAtText);
        stopButton = view.findViewById(R.id.focusStopButton);
        playButton = view.findViewById(R.id.focusPlayButton);
        skipButton = view.findViewById(R.id.focusSkipButton);
        todayText = view.findViewById(R.id.focusTodayText);
        failsText = view.findViewById(R.id.focusFailsText);
        streakText = view.findViewById(R.id.focusStreakText);
        studyingHeader = view.findViewById(R.id.focusStudyingHeader);
        friendsGrid = view.findViewById(R.id.focusFriendsGrid);
    }

    /** html 01: centred "Focus", lichen back arrow (minimises, never cancels), 🚫 action. */
    private void setUpTopBar(@NonNull View view) {
        toolbar = TopBarHelper.bind(this, view, R.string.title_focus);
        if (toolbar == null) {
            return;
        }
        toolbar.setTitleCentered(true);
        toolbar.setNavigationIconTint(
                ContextCompat.getColor(requireContext(), R.color.bb_on_surface_muted));
        toolbar.inflateMenu(R.menu.menu_focus);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.focusBlockModeButton) {
                onBlockModeTapped();
                return true;
            }
            return false;
        });
        renderBlockMode();
    }

    // ================================================================== session render

    private void render(@Nullable FocusUiState state) {
        if (state == null || timerText == null) {
            return;
        }
        timerText.setText(FormatHelper.formatTime((int) state.getRemainingMillis(), "mm:ss"));
        // html 01: el arco es lo que QUEDA (se vacía en sentido horario desde arriba).
        int remaining = Math.round((1f - state.getProgress()) * ring.getMax());
        ring.setProgressCompat(state.isIdle() ? ring.getMax() : remaining, false);
        renderEndsAt(state);

        if (!state.controlsDifferFrom(lastRendered)) {
            lastRendered = state;
            return;
        }
        lastRendered = state;
        renderChip(state);
        renderCycleDots(state);
        renderControls(state);
        renderBlockMode();
    }

    /** "CONCENTRATION · 2 OF 4" (textAllCaps del appearance Chip). */
    private void renderChip(@NonNull FocusUiState state) {
        String mode = getString(state.isRest()
                ? R.string.home_mode_rest : R.string.home_mode_concentration);
        // Sin auto-ciclo, tras el descanso N la máquina queda en reposo con cyclesCompleted == N
        // (el aviso "ciclos completados" llega al acabar la concentración siguiente, quirk u2):
        // el chip no pasa de "N OF N".
        int cycle = state.isInfiniteCycles()
                ? state.getCurrentCycle()
                : Math.min(state.getCurrentCycle(), Math.max(1, state.getTotalCycles()));
        modeChip.setText(state.isInfiniteCycles()
                ? getString(R.string.focus_mode_chip_infinite_format, mode, cycle)
                : getString(R.string.focus_mode_chip_format, mode, cycle, state.getTotalCycles()));
    }

    /** Running: "Ends at 17:30". Paused: "Paused". Idle: "Ready when you are" (no clock). */
    private void renderEndsAt(@NonNull FocusUiState state) {
        String line;
        boolean clock;
        if (state.isRunning() && state.getEndsAtMillis() > 0) {
            line = getString(R.string.focus_ends_at,
                    DateFormat.getTimeFormat(requireContext()).format(new Date(state.getEndsAtMillis())));
            clock = true;
        } else if (state.isPaused()) {
            line = getString(R.string.focus_paused_line);
            clock = false;
        } else {
            line = getString(R.string.focus_idle_line);
            clock = false;
        }
        if (!line.contentEquals(endsAtText.getText())) {
            endsAtText.setText(line);
        }
        endsAtIcon.setVisibility(clock ? View.VISIBLE : View.GONE);
    }

    /**
     * html 01: one dot per configured cycle — done (sage), current (sage + halo), pending (card +
     * hairline). Infinite cycles have no N: no dots.
     */
    private void renderCycleDots(@NonNull FocusUiState state) {
        cycleDots.removeAllViews();
        if (state.isInfiniteCycles() || state.getTotalCycles() <= 0) {
            cycleDots.setVisibility(View.GONE);
            return;
        }
        cycleDots.setVisibility(View.VISIBLE);
        int total = Math.min(state.getTotalCycles(), MAX_CYCLE_DOTS);
        int done = state.getCyclesCompleted();
        int current = state.isSessionActive() ? state.getCurrentCycle() : 0;
        int box = getResources().getDimensionPixelSize(R.dimen.focus_cycle_dot_box);
        int gap = getResources().getDimensionPixelSize(R.dimen.focus_cycle_dot_gap);
        for (int i = 1; i <= total; i++) {
            View dot = new View(requireContext());
            @DrawableRes int bg;
            if (i == current) {
                bg = R.drawable.bg_focus_dot_current;
            } else if (i <= done) {
                bg = R.drawable.bg_focus_dot_done;
            } else {
                bg = R.drawable.bg_focus_dot_pending;
            }
            dot.setBackgroundResource(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(box, box);
            lp.setMarginStart(i == 1 ? 0 : gap);
            cycleDots.addView(dot, lp);
        }
        cycleDots.setContentDescription(getString(R.string.focus_cycles_desc,
                Math.min(Math.max(current, done), state.getTotalCycles()), state.getTotalCycles()));
    }

    private void renderControls(@NonNull FocusUiState state) {
        // Centro: play (parado o en pausa) / pausa (corriendo).
        boolean running = state.isRunning();
        playButton.setImageResource(running
                ? R.drawable.ic_pause_v2_filled_24 : R.drawable.ic_play_v2_filled_24);
        int playDesc;
        if (running) {
            playDesc = R.string.focus_pause;
        } else if (state.isPaused()) {
            playDesc = R.string.focus_resume;
        } else {
            playDesc = R.string.focus_start;
        }
        playButton.setContentDescription(getString(playDesc));

        // Stop: sólo hay algo que parar con una sesión viva.
        boolean active = state.isSessionActive();
        stopButton.setEnabled(active);
        stopButton.setAlpha(active ? 1f : DISABLED_ALPHA);

        // Skip: el diseño lo dibuja siempre; sólo actúa en descanso (como antes en Home).
        skipButton.setContentDescription(getString(state.canSkipRest()
                ? R.string.focus_skip_rest : R.string.focus_skip_rest_unavailable));
    }

    // ================================================================== controls

    private void onPlayTapped(View v) {
        HapticFeedback.lightTap(v);
        // toggle(): parado → concentración, corriendo → pausa, en pausa → reanudar.
        if (session.toggle()) {
            SoundFeedback.get(requireContext()).playStart();
        } else {
            SoundFeedback.get(requireContext()).playStop();
        }
    }

    private void onStopTapped(View v) {
        HapticFeedback.lightTap(v);
        if (session.cancelSession()) {
            Log.i(TAG, "Session cancelled from Focus");
            SoundFeedback.get(requireContext()).playStop();
        }
    }

    private void onSkipTapped(View v) {
        HapticFeedback.lightTap(v);
        FocusUiState state = session.getUiState().getValue();
        if (state == null || !state.canSkipRest()) {
            Toast.makeText(requireContext(), R.string.focus_skip_rest_unavailable,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (session.skipRest()) {
            SoundFeedback.get(requireContext()).playStop();
        }
    }

    // ================================================================== 🚫 block mode

    /**
     * Off → on needs the accessibility grant (same gate as before on Home): granted → arm now;
     * else the disclosure dialog, and {@link #onAccessibilityConsentGranted()} arms it. On → off
     * never needs anything. Always available, also mid-run (spec focus-mode-and-streak).
     */
    private void onBlockModeTapped() {
        if (session.isBlockModeArmed()) {
            session.setBlockModeArmed(false);
        } else if (AccessibilityAccess.isEnabled(requireContext())) {
            session.setBlockModeArmed(true);
        } else {
            AccessibilityDisclosureDialog.newInstance()
                    .show(getChildFragmentManager(), DISCLOSURE_TAG);
        }
        renderBlockMode();
    }

    @Override
    public void onAccessibilityConsentGranted() {
        session.setBlockModeArmed(true);
        renderBlockMode();
    }

    /**
     * Three states: off (outline, muted) · on (filled {@code do_not_disturb_on}, sage — html 01) ·
     * armed but the accessibility service is not on yet (filled, honey = "pending", as before).
     */
    private void renderBlockMode() {
        if (toolbar == null || session == null) {
            return;
        }
        MenuItem item = toolbar.getMenu().findItem(R.id.focusBlockModeButton);
        if (item == null) {
            return;
        }
        @DrawableRes int icon;
        @ColorRes int tint;
        int title;
        int desc;
        if (!session.isBlockModeArmed()) {
            icon = R.drawable.ic_do_not_disturb_24;
            tint = R.color.bb_on_surface_muted;
            title = R.string.home_block_mode_off;
            desc = R.string.focus_block_mode_desc_off;
        } else if (AccessibilityAccess.isEnabled(requireContext())) {
            icon = R.drawable.ic_do_not_disturb_on_24;
            tint = R.color.bb_primary;
            title = R.string.home_block_mode_on;
            desc = R.string.focus_block_mode_desc_on;
        } else {
            icon = R.drawable.ic_do_not_disturb_on_24;
            tint = R.color.bb_accent_reward;
            title = R.string.home_block_mode_pending;
            desc = R.string.focus_block_mode_desc_pending;
        }
        Drawable d = ContextCompat.getDrawable(requireContext(), icon);
        if (d != null) {
            d = DrawableCompat.wrap(d.mutate());
            DrawableCompat.setTint(d, ContextCompat.getColor(requireContext(), tint));
            item.setIcon(d);
        }
        item.setTitle(title);
        MenuItemCompat.setContentDescription(item, getString(title) + ". " + getString(desc));
    }

    // ================================================================== today / streak

    private void renderToday(@Nullable Integer millis) {
        if (todayText == null) {
            return;
        }
        todayText.setText(FormatHelper.formatDuration(millis == null ? 0 : millis));
    }

    private void renderFails(@Nullable Integer fails) {
        if (failsText == null) {
            return;
        }
        int n = fails == null ? 0 : fails;
        failsText.setText(getResources().getQuantityString(R.plurals.focus_fails_today, n, n));
    }

    private void renderStreak(@Nullable StreakCalculator.Result streak) {
        if (streakText == null) {
            return;
        }
        int days = streak == null ? 0 : streak.currentStreak;
        streakText.setText(getResources().getQuantityString(R.plurals.focus_streak_days, days, days));
    }

    // ================================================================== studying now (mock)

    // PLACEHOLDER(ui-placeholders#social-focus-studying): maqueta (MockSocialRepository).
    private void renderFriends(@Nullable SocialSnapshot snapshot) {
        if (friendsGrid == null || snapshot == null) {
            return;
        }
        studyingHeader.setMeta(getString(R.string.focus_friends_meta,
                snapshot.getStudyingCount(), snapshot.getFriendCount()), true);

        friendsGrid.removeAllViews();
        List<Friend> friends = snapshot.getFriends();
        Friend top = snapshot.getTopFriend();
        int shown = Math.min(MAX_FRIENDS, friends.size());
        int rowGap = getResources().getDimensionPixelSize(R.dimen.focus_friend_row_gap);
        int colGap = getResources().getDimensionPixelSize(R.dimen.focus_friend_col_gap);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        LinearLayout row = null;
        for (int i = 0; i < shown; i++) {
            if (i % FRIEND_COLUMNS == 0) {
                row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setBaselineAligned(false);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                rowLp.topMargin = i == 0 ? 0 : rowGap;
                friendsGrid.addView(row, rowLp);
            }
            View cell = inflater.inflate(R.layout.item_focus_friend, row, false);
            bindFriend(cell, friends.get(i), friends.get(i) == top);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) cell.getLayoutParams();
            lp.setMarginStart(i % FRIEND_COLUMNS == 0 ? 0 : colGap);
            row.addView(cell, lp);
        }
        // Completa la última fila con huecos para que las columnas no se ensanchen.
        if (row != null) {
            for (int i = shown % FRIEND_COLUMNS; i != 0 && i < FRIEND_COLUMNS; i++) {
                View filler = new View(requireContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMarginStart(colGap);
                row.addView(filler, lp);
            }
        }
    }

    private void bindFriend(@NonNull View cell, @NonNull Friend friend, boolean isTop) {
        AvatarView avatar = cell.findViewById(R.id.focusFriendAvatar);
        TextView name = cell.findViewById(R.id.focusFriendName);
        TextView time = cell.findViewById(R.id.focusFriendTime);
        avatar.setName(friend.getName());
        avatar.setInitials(friend.getInitials());
        name.setText(friend.getName().split(" ")[0]);
        if (friend.isStudying()) {
            avatar.setStatus(AvatarView.Status.STUDYING);
            avatar.setBadgeIcon(isTop ? R.drawable.ic_crown_24 : 0);
            long s = friend.getFocusSecondsToday();
            time.setText(getString(R.string.focus_friend_time_format,
                    (int) (s / 3600), (int) (s / 60 % 60), (int) (s % 60)));
            cell.setAlpha(1f);
        } else {
            // html 01: la CELDA entera al 40 % (avatar, nombre y "away"); AvatarView ya se pone a
            // 0,4 en AWAY, así que el avatar vuelve a 1 para no quedar al 16 %.
            avatar.setStatus(AvatarView.Status.AWAY);
            avatar.setAlpha(1f);
            avatar.setBadgeIcon(0);
            name.setTextColor(ContextCompat.getColor(requireContext(), R.color.bb_on_surface_muted));
            time.setTextColor(ContextCompat.getColor(requireContext(), R.color.bb_on_surface_muted));
            time.setText(R.string.focus_friend_away);
            cell.setAlpha(AWAY_CELL_ALPHA);
        }
    }

    // ================================================================== lifecycle

    @Override
    public void onResume() {
        super.onResume();
        // The accessibility service clears the live block flag when it reconnects, and there is no
        // callback from Settings: re-assert the flag and re-render the 🚫 state.
        session.syncBlockState();
        renderBlockMode();
        // Red de seguridad: un resultado que llegó con el estado guardado sigue pendiente.
        sessionEvents.recheck();
    }

    @Override
    public void onDestroyView() {
        lastRendered = null;
        toolbar = null;
        modeChip = null;
        cycleDots = null;
        ring = null;
        timerText = null;
        endsAtIcon = null;
        endsAtText = null;
        stopButton = null;
        playButton = null;
        skipButton = null;
        todayText = null;
        failsText = null;
        streakText = null;
        studyingHeader = null;
        friendsGrid = null;
        super.onDestroyView();
    }
}
