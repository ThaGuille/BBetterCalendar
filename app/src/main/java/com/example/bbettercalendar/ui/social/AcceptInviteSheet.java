package com.example.bbettercalendar.ui.social;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.social.friends.FriendCard;
import com.example.bbettercalendar.ui.common.InsetsHelper;
import com.example.bbettercalendar.ui.common.widget.AvatarView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * F2-u8: hoja «Añadir a Alice, @alice» al llegar con una invitación (App Link o referrer de Play), con
 * Enviar solicitud; o su callejón sin salida: «Ya sois amigos», «Es tu propio enlace», «Enlace caducado»,
 * «Solicitud ya enviada» y «X ya te ha escrito» (que lleva a Solicitudes). La abre {@code MainActivity} con el
 * resultado de {@link PendingInviteViewModel}, de ámbito de actividad, del que lee qué enseñar.
 */
@AndroidEntryPoint
public class AcceptInviteSheet extends BottomSheetDialogFragment {

    public static final String TAG = "accept_invite_sheet";

    private PendingInviteViewModel viewModel;
    private AvatarView avatar;
    private TextView title;
    private TextView body;
    private TextView message;
    private com.google.android.material.button.MaterialButton sendButton;
    private com.google.android.material.button.MaterialButton closeButton;
    @Nullable private PendingInviteViewModel.Ui ui;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_accept_invite, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsHelper.apply(view, InsetsHelper.BOTTOM);

        viewModel = new ViewModelProvider(requireActivity()).get(PendingInviteViewModel.class);
        avatar = view.findViewById(R.id.acceptAvatar);
        title = view.findViewById(R.id.acceptTitle);
        body = view.findViewById(R.id.acceptBody);
        message = view.findViewById(R.id.acceptMessage);
        sendButton = view.findViewById(R.id.acceptSendButton);
        closeButton = view.findViewById(R.id.acceptCloseButton);

        ui = viewModel.getCurrent();
        if (ui == null) {
            // El proceso murió con la hoja abierta: no queda qué enseñar.
            dismissAllowingStateLoss();
            return;
        }
        closeButton.setOnClickListener(v -> dismiss());
        paint(ui);
        viewModel.getSendState().observe(getViewLifecycleOwner(), this::renderSend);
    }

    private void paint(@NonNull PendingInviteViewModel.Ui ui) {
        FriendCard card = ui.card;
        String name = card == null ? "" : card.displayName;
        avatar.setVisibility(card == null ? View.GONE : View.VISIBLE);
        if (card != null) {
            ProfileUi.paintAvatar(avatar, card.displayName, card.avatarColor);
        }
        sendButton.setVisibility(View.GONE);
        closeButton.setText(R.string.accept_invite_close);
        switch (ui.kind) {
            case ACCEPT:
                if (card != null) {
                    title.setText(getString(R.string.accept_invite_title, name, card.handle));
                }
                body.setText(R.string.accept_invite_body);
                sendButton.setText(R.string.accept_invite_send);
                sendButton.setVisibility(View.VISIBLE);
                sendButton.setOnClickListener(v -> {
                    if (card != null) {
                        viewModel.send(card);
                    }
                });
                closeButton.setText(R.string.accept_invite_not_now);
                break;
            case ALREADY_FRIENDS:
                title.setText(R.string.accept_invite_already_friends_title);
                body.setText(getString(R.string.accept_invite_already_friends_body, name));
                break;
            case SELF:
                title.setText(R.string.accept_invite_self_title);
                body.setText(R.string.accept_invite_self_body);
                break;
            case ALREADY_SENT:
                title.setText(R.string.accept_invite_already_sent_title);
                body.setText(getString(R.string.accept_invite_already_sent_body, name));
                break;
            case THEY_SENT:
                title.setText(getString(R.string.accept_invite_they_sent_title, name));
                body.setText(R.string.accept_invite_they_sent_body);
                sendButton.setText(R.string.accept_invite_see_requests);
                sendButton.setVisibility(View.VISIBLE);
                sendButton.setOnClickListener(v -> openRequests());
                break;
            case EXPIRED:
            default:
                title.setText(R.string.accept_invite_expired_title);
                body.setText(R.string.accept_invite_expired_body);
                break;
        }
    }

    private void renderSend(@Nullable PendingInviteViewModel.SendState state) {
        if (state == null || ui == null || ui.kind != PendingInviteViewModel.Ui.Kind.ACCEPT) {
            return;
        }
        switch (state.step) {
            case SENDING:
                sendButton.setEnabled(false);
                message.setVisibility(View.GONE);
                break;
            case SENT:
                if (ui.card != null) {
                    Toast.makeText(requireContext().getApplicationContext(),
                            getString(R.string.friends_toast_sent, ui.card.displayName),
                            Toast.LENGTH_SHORT).show();
                }
                dismissAllowingStateLoss();
                break;
            case ERROR:
                sendButton.setEnabled(true);
                message.setText(state.message);
                message.setVisibility(View.VISIBLE);
                break;
            case IDLE:
            default:
                sendButton.setEnabled(true);
                message.setVisibility(View.GONE);
                break;
        }
    }

    /** «X ya te ha escrito»: lleva a Amigos, subido hasta las solicitudes. */
    private void openRequests() {
        Bundle args = new Bundle();
        args.putString("section", "requests");
        Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_activity_main)
                .navigate(R.id.navigation_friends, args);
        dismissAllowingStateLoss();
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        super.onDismiss(dialog);
        if (viewModel != null && getActivity() != null && !getActivity().isChangingConfigurations()) {
            viewModel.dismissCurrent();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetBehavior<FrameLayout> behavior = ((BottomSheetDialog) getDialog()).getBehavior();
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }
}
