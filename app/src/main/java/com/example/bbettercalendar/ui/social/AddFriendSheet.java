package com.example.bbettercalendar.ui.social;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.social.friends.FriendCard;
import com.example.bbettercalendar.ui.common.InsetsHelper;
import com.example.bbettercalendar.ui.common.widget.AvatarView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * F2-u7: añadir un amigo por {@code @usuario}: campo → Buscar → tarjeta → Enviar solicitud. Cada resultado de
 * {@code lookupHandle} (no existe, eres tú, ya sois amigos, ya enviada, te la envió él), el tope diario y el
 * de 50 tienen su mensaje. Comparte el {@link FriendsViewModel} de {@link FriendsFragment}.
 */
@AndroidEntryPoint
public class AddFriendSheet extends BottomSheetDialogFragment {

    private FriendsViewModel viewModel;
    private EditText input;
    private TextView message;
    private View preview;
    private AvatarView previewAvatar;
    private TextView previewName;
    private TextView previewHandle;
    private View findButton;
    private View sendButton;
    @Nullable private FriendCard previewCard;
    private boolean busy;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_add_friend, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsHelper.apply(view, InsetsHelper.BOTTOM);
        viewModel = new ViewModelProvider(requireParentFragment()).get(FriendsViewModel.class);
        input = view.findViewById(R.id.addHandleInput);
        message = view.findViewById(R.id.addMessage);
        preview = view.findViewById(R.id.addPreview);
        previewAvatar = view.findViewById(R.id.addPreviewAvatar);
        previewName = view.findViewById(R.id.addPreviewName);
        previewHandle = view.findViewById(R.id.addPreviewHandle);
        findButton = view.findViewById(R.id.addFindButton);
        sendButton = view.findViewById(R.id.addSendButton);

        input.addTextChangedListener(new ProfileUi.OnTextChanged() {
            @Override
            void onChanged(@NonNull String text) {
                // Cambiar el texto invalida la vista previa: hay que volver a buscar.
                FriendsViewModel.AddState current = viewModel.getAddState().getValue();
                if (current != null && (current.step == FriendsViewModel.AddState.Step.PREVIEW
                        || current.step == FriendsViewModel.AddState.Step.MESSAGE)) {
                    viewModel.resetAdd();
                }
                refresh();
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH && findButton.isEnabled()) {
                find();
                return true;
            }
            return false;
        });
        findButton.setOnClickListener(v -> find());
        sendButton.setOnClickListener(v -> {
            if (previewCard != null) {
                viewModel.send(previewCard);
            }
        });
        viewModel.getAddState().observe(getViewLifecycleOwner(), this::render);
    }

    private void find() {
        viewModel.lookup(input.getText().toString());
    }

    private void refresh() {
        findButton.setEnabled(!busy && !input.getText().toString().trim().isEmpty());
    }

    private void render(@Nullable FriendsViewModel.AddState state) {
        if (state == null) {
            return;
        }
        busy = state.step == FriendsViewModel.AddState.Step.LOOKING
                || state.step == FriendsViewModel.AddState.Step.SENDING;
        input.setEnabled(!busy);
        previewCard = state.step == FriendsViewModel.AddState.Step.PREVIEW
                || state.step == FriendsViewModel.AddState.Step.SENDING ? state.card : null;
        boolean showPreview = previewCard != null;
        preview.setVisibility(showPreview ? View.VISIBLE : View.GONE);
        sendButton.setVisibility(showPreview ? View.VISIBLE : View.GONE);
        sendButton.setEnabled(state.step == FriendsViewModel.AddState.Step.PREVIEW);
        findButton.setVisibility(showPreview ? View.GONE : View.VISIBLE);
        if (showPreview) {
            ProfileUi.paintAvatar(previewAvatar, previewCard.displayName, previewCard.avatarColor);
            previewName.setText(previewCard.displayName);
            previewHandle.setText("@" + previewCard.handle);
        }
        if (state.step == FriendsViewModel.AddState.Step.MESSAGE) {
            message.setText(state.message);
            message.setVisibility(View.VISIBLE);
        } else {
            message.setVisibility(View.GONE);
        }
        if (state.step == FriendsViewModel.AddState.Step.SENT) {
            // El fragmento padre enseña «Solicitud enviada a X» con el aviso del ViewModel.
            viewModel.resetAdd();
            dismissAllowingStateLoss();
            return;
        }
        refresh();
    }
}
