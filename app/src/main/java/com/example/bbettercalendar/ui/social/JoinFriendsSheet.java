package com.example.bbettercalendar.ui.social;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.InsetsHelper;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * F1-u4: hoja «Únete a tus amigos» (patrón de QuickAddTaskSheet). Google abre el selector de
 * Credential Manager; email lleva a {@link EmailSignInFragment}. Es un destino {@code <dialog>} del
 * grafo, así que la abre el router con el NavController.
 */
@AndroidEntryPoint
public class JoinFriendsSheet extends BottomSheetDialogFragment {

    private SocialEntryViewModel viewModel;
    private View googleButton;
    private TextView error;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_join_friends, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Edge-to-edge (F0-u2): solo la barra de navegación.
        InsetsHelper.apply(view, InsetsHelper.BOTTOM);

        viewModel = new ViewModelProvider(this).get(SocialEntryViewModel.class);
        googleButton = view.findViewById(R.id.joinGoogleButton);
        error = view.findViewById(R.id.joinError);

        // F2-u8: una invitación (App Link / referrer) guardada espera a que haya cuenta y perfil.
        View waiting = view.findViewById(R.id.joinInviteWaiting);
        new ViewModelProvider(requireActivity()).get(PendingInviteViewModel.class).getInviteWaiting()
                .observe(getViewLifecycleOwner(), w ->
                        waiting.setVisibility(Boolean.TRUE.equals(w) ? View.VISIBLE : View.GONE));

        googleButton.setOnClickListener(v -> {
            error.setVisibility(View.GONE);
            viewModel.signInWithGoogle(requireActivity());
        });
        view.findViewById(R.id.joinEmailButton).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_join_to_email));

        viewModel.isBusy().observe(getViewLifecycleOwner(), busy ->
                googleButton.setEnabled(!Boolean.TRUE.equals(busy)));
        viewModel.getEntryStep().observe(getViewLifecycleOwner(), step -> {
            if (step == null) {
                return;
            }
            viewModel.consumeEntryStep();
            SocialEntryNavigator.goReplacing(this, step, R.id.navigation_join_friends);
        });
        viewModel.getOutcome().observe(getViewLifecycleOwner(), outcome -> {
            if (outcome == null) {
                return;
            }
            viewModel.consumeOutcome();
            if (outcome.kind == SocialEntryViewModel.Outcome.Kind.SIGNED_IN) {
                // F1-u5: el router decide (Crear perfil si aún no tiene, Mi perfil si ya existe).
                viewModel.requestEntry();
            } else {
                error.setText(outcome.failure == SocialEntryViewModel.Failure.NETWORK
                        ? R.string.join_error_network : R.string.join_error_google);
                error.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        // Abierta ya del todo: sin estado colapsado, los dos botones quedan a la vista.
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetBehavior<FrameLayout> behavior = ((BottomSheetDialog) getDialog()).getBehavior();
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }
}
