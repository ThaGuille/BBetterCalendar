package com.example.bbettercalendar.ui.social;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.social.account.AccountState;
import com.example.bbettercalendar.social.friends.FriendCard;
import com.example.bbettercalendar.social.friends.ReportReason;
import com.example.bbettercalendar.social.interaction.InteractionType;
import com.example.bbettercalendar.social.stats.RankingState;
import com.example.bbettercalendar.ui.common.InsetsHelper;
import com.example.bbettercalendar.ui.common.TopBarHelper;
import com.example.bbettercalendar.ui.common.widget.AvatarView;

import com.google.android.material.button.MaterialButton;

import java.time.LocalDate;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * F3-u6: el perfil de un amigo (02 §9): avatar, nombre y {@code @usuario}; racha semanal, días de esta semana
 * frente a su objetivo, minutos de la semana, mejor racha y total, de {@code RankingRepository.observeFriendStats}.
 * Sin stats todavía: una frase, no ceros. Eliminar (con la misma confirmación que la lista) y Reportar (con su
 * «¿Eliminar también?») usan {@link FriendsViewModel}; tras eliminar vuelve a Amigos. Con ánimo y empujón (F5-u8).
 * Argumentos: {@code uid} y, para pintar la cabecera al instante, {@code handle}, {@code name} y {@code color}.
 */
@AndroidEntryPoint
public class FriendProfileFragment extends Fragment {

    public static final String ARG_UID = "uid";
    public static final String ARG_HANDLE = "handle";
    public static final String ARG_NAME = "name";
    public static final String ARG_COLOR = "color";

    private FriendsViewModel friendsViewModel;
    private FriendCard card;
    private boolean leaving;

    private AvatarView avatar;
    private TextView nameView;
    private TextView handleView;
    private TextView status;
    private TextView empty;
    private View stats;
    private TextView streak;
    private TextView days;
    private LinearLayout dots;
    private TextView minutes;
    private TextView best;
    private TextView total;
    private View interaction;
    private MaterialButton cheerButton;
    private MaterialButton nudgeButton;
    private TextView interactionMessage;
    private FriendInteractionViewModel interactionViewModel;
    private RankingState.FriendStatsState lastState;
    private FriendInteractionViewModel.State interactionState;

    public FriendProfileFragment() {
        super(R.layout.fragment_friend_profile);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = requireArguments();
        String uid = args.getString(ARG_UID, "");
        card = new FriendCard(uid, args.getString(ARG_HANDLE, ""), args.getString(ARG_NAME, ""),
                args.getInt(ARG_COLOR, 0));
        friendsViewModel = new ViewModelProvider(this).get(FriendsViewModel.class);
        FriendProfileViewModel viewModel = new ViewModelProvider(this).get(FriendProfileViewModel.class);

        TopBarHelper.bind(this, view, R.string.friend_profile_top_title);
        InsetsHelper.apply(view.findViewById(R.id.fpScroll), InsetsHelper.BOTTOM);
        avatar = view.findViewById(R.id.fpAvatar);
        nameView = view.findViewById(R.id.fpName);
        handleView = view.findViewById(R.id.fpHandle);
        status = view.findViewById(R.id.fpStatus);
        empty = view.findViewById(R.id.fpEmpty);
        stats = view.findViewById(R.id.fpStats);
        streak = view.findViewById(R.id.fpStreak);
        days = view.findViewById(R.id.fpDays);
        dots = view.findViewById(R.id.fpDots);
        minutes = view.findViewById(R.id.fpMinutes);
        best = view.findViewById(R.id.fpBest);
        total = view.findViewById(R.id.fpTotal);
        interaction = view.findViewById(R.id.fpInteraction);
        cheerButton = view.findViewById(R.id.fpCheerButton);
        nudgeButton = view.findViewById(R.id.fpNudgeButton);
        interactionMessage = view.findViewById(R.id.fpInteractionMessage);
        paintHeader();

        view.findViewById(R.id.fpRemoveButton).setOnClickListener(v -> confirmRemove());
        view.findViewById(R.id.fpReportButton).setOnClickListener(v -> showReportSheet());
        BlockViewModel blockViewModel = new ViewModelProvider(this).get(BlockViewModel.class);
        view.findViewById(R.id.fpBlockButton).setOnClickListener(v ->
                BlockUi.confirm(requireContext(), card, reason ->
                        blockViewModel.block(card, reason, ReportReason.Context.FRIEND)));
        blockViewModel.getEvent().observe(getViewLifecycleOwner(), event -> {
            if (event == null) {
                return;
            }
            blockViewModel.consumeEvent();
            Toast.makeText(requireContext().getApplicationContext(),
                    event.arg == null ? getString(event.message) : getString(event.message, event.arg),
                    Toast.LENGTH_SHORT).show();
            if (event.blocked) {
                goBack();
            }
        });

        if (uid.isEmpty()) {
            goBack();
            return;
        }
        interactionViewModel = new ViewModelProvider(this).get(FriendInteractionViewModel.class);
        cheerButton.setOnClickListener(v -> interactionViewModel.send(InteractionType.CHEER, uid));
        nudgeButton.setOnClickListener(v -> interactionViewModel.send(InteractionType.NUDGE, uid));
        interactionViewModel.load(uid);
        interactionViewModel.getState().observe(getViewLifecycleOwner(), st -> {
            interactionState = st;
            renderInteraction();
        });
        viewModel.observe(uid).observe(getViewLifecycleOwner(), this::render);
        friendsViewModel.getEvent().observe(getViewLifecycleOwner(), event -> {
            if (event == null) {
                return;
            }
            friendsViewModel.consumeEvent();
            Toast.makeText(requireContext().getApplicationContext(),
                    event.arg == null ? getString(event.message) : getString(event.message, event.arg),
                    Toast.LENGTH_SHORT).show();
            if (event.offerRemove != null) {
                offerRemoveAfterReport(event.offerRemove);
            }
            if (event.message == R.string.friends_toast_removed) {
                goBack();
            }
        });
        new ViewModelProvider(this).get(ProfileViewModel.class).getAccountState()
                .observe(getViewLifecycleOwner(), state -> {
                    if (state == AccountState.SIGNED_OUT) {
                        goBack();
                    }
                });
    }

    private void paintHeader() {
        ProfileUi.paintAvatar(avatar, card.displayName, card.avatarColor);
        nameView.setText(card.displayName);
        handleView.setText("@" + card.handle);
    }

    private void render(@Nullable RankingState.FriendStatsState state) {
        lastState = state;
        renderInteraction();
        if (state != null && state.card != null) {
            card = FriendProfileViewModel.cardOrFallback(state, card);
            paintHeader();
        }
        FriendProfileUi ui = FriendProfileUi.from(state);
        if (ui.isGone()) {
            goBack();
            return;
        }
        stats.setVisibility(ui.showStats() ? View.VISIBLE : View.GONE);
        empty.setVisibility(ui.showEmpty() ? View.VISIBLE : View.GONE);
        boolean message = ui.showLoading() || ui.showError();
        status.setVisibility(message ? View.VISIBLE : View.GONE);
        status.setText(ui.showError() ? R.string.friend_profile_error : R.string.friend_profile_loading);
        if (ui.showStats()) {
            bindStats(ui);
        }
    }

    /** F5-u8: ánimo siempre, empujón solo si no ha estudiado hoy; «enviado hoy» deshabilitado. Sin contadores. */
    private void renderInteraction() {
        FriendInteractionViewModel.State st = interactionState;
        if (interaction == null || st == null) {
            return;
        }
        FriendInteractionUi ui = FriendInteractionUi.from(lastState, LocalDate.now(), st.cheerSent, st.nudgeSent,
                st.lastType, st.lastResult);
        interaction.setVisibility(ui.cheerVisible() ? View.VISIBLE : View.GONE);
        cheerButton.setText(ui.cheerText());
        cheerButton.setEnabled(ui.cheer == FriendInteractionUi.Button.ENABLED);
        nudgeButton.setVisibility(ui.nudgeVisible() ? View.VISIBLE : View.GONE);
        nudgeButton.setText(ui.nudgeText());
        nudgeButton.setEnabled(ui.nudge == FriendInteractionUi.Button.ENABLED);
        interactionMessage.setVisibility(ui.hasMessage() ? View.VISIBLE : View.GONE);
        if (ui.hasMessage()) {
            interactionMessage.setText(ui.message);
        }
    }

    private void bindStats(@NonNull FriendProfileUi ui) {
        streak.setText(getResources().getQuantityString(R.plurals.friend_profile_weeks, ui.weekStreak,
                ui.weekStreak));
        days.setText(getResources().getQuantityString(R.plurals.friend_profile_days_of, ui.goal,
                ui.daysDone, ui.goal));
        minutes.setText(getString(R.string.friend_profile_minutes_week, ui.minutesWeek));
        best.setText(getResources().getQuantityString(R.plurals.friend_profile_weeks, ui.bestStreak,
                ui.bestStreak));
        if (ui.totalHasHours() && ui.totalHasMinutes()) {
            total.setText(getString(R.string.friend_profile_total_hm, ui.totalHours, ui.totalMinutesRest));
        } else if (ui.totalHasHours()) {
            total.setText(getString(R.string.friend_profile_total_h, ui.totalHours));
        } else {
            total.setText(getString(R.string.friend_profile_total_m, ui.totalMinutesRest));
        }
        bindDots(ui);
    }

    /** Un punto por día del objetivo; los hechos, llenos. Decorativos: el texto de arriba dice lo mismo. */
    private void bindDots(@NonNull FriendProfileUi ui) {
        dots.removeAllViews();
        int size = getResources().getDimensionPixelSize(R.dimen.social_goal_dot);
        int gap = getResources().getDimensionPixelSize(R.dimen.social_goal_dot_gap);
        for (int i = 0; i < ui.dotsTotal; i++) {
            View dot = new View(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            if (i > 0) {
                lp.setMarginStart(gap);
            }
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(i < ui.dotsFilled ? R.drawable.bg_streak_dot_done
                    : R.drawable.bg_streak_dot_pending);
            dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            dots.addView(dot);
        }
        dots.setVisibility(ui.dotsTotal > 0 ? View.VISIBLE : View.GONE);
    }

    private void showReportSheet() {
        if (getChildFragmentManager().findFragmentByTag(FriendsFragment.TAG_REPORT) == null) {
            friendsViewModel.resetReport();
            ReportSheet.newInstance(card, ReportReason.Context.FRIEND)
                    .show(getChildFragmentManager(), FriendsFragment.TAG_REPORT);
        }
    }

    private void confirmRemove() {
        FriendCard friend = card;
        new AlertDialog.Builder(requireContext(), R.style.RoundedDialog)
                .setTitle(getString(R.string.friends_remove_title, friend.displayName))
                .setMessage(R.string.friends_remove_message)
                .setNegativeButton(R.string.friends_cancel, null)
                .setPositiveButton(R.string.friends_remove_confirm, (d, which) -> friendsViewModel.remove(friend))
                .show();
    }

    private void offerRemoveAfterReport(@NonNull FriendCard friend) {
        new AlertDialog.Builder(requireContext(), R.style.RoundedDialog)
                .setTitle(getString(R.string.report_remove_title, friend.displayName))
                .setMessage(R.string.report_remove_message)
                .setNegativeButton(R.string.report_remove_no, null)
                .setPositiveButton(R.string.report_remove_yes, (d, which) -> friendsViewModel.remove(friend))
                .show();
    }

    /** Vuelve a Amigos una sola vez, lleguen la eliminación, el estado «ya no es amigo» o el cierre de sesión. */
    private void goBack() {
        if (leaving) {
            return;
        }
        leaving = true;
        NavHostFragment.findNavController(this).popBackStack();
    }
}
