package com.example.bbettercalendar.ui.social;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.social.account.AccountState;
import com.example.bbettercalendar.social.profile.HandleValidator;
import com.example.bbettercalendar.social.profile.Profile;
import com.example.bbettercalendar.social.profile.ProfileState;
import com.example.bbettercalendar.ui.common.InsetsHelper;
import com.example.bbettercalendar.ui.common.TopBarHelper;
import com.example.bbettercalendar.ui.common.widget.AvatarView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.text.DateFormat;
import java.util.Date;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * F1-u5: Mi perfil. Enseña el perfil propio (en vivo), deja editar nombre y color, renombrar el
 * {@code @usuario} (la primera vez libre y luego una vez cada 30 días, D-019 / D-022: mientras no
 * toque, el botón queda deshabilitado con la fecha), cerrar sesión y (F1-u7) borrar la cuenta: confirmación,
 * re-autenticación si el servidor la pide (contraseña o selector de Google) y llamada a la callable.
 */
@AndroidEntryPoint
public class MyProfileFragment extends Fragment {

    private static final String STATE_COLOR = "color";

    private ProfileViewModel viewModel;
    private TextView status;
    private View content;
    private AvatarView avatar;
    private TextView nameView;
    private TextView handleView;
    private EditText nameInput;
    private AvatarColorPickerView picker;
    private TextView message;
    private MaterialButton save;
    private TextView renameHint;
    private EditText renameInput;
    private TextView renameStatus;
    private MaterialButton renameButton;
    private MaterialButton deleteButton;
    private SwitchMaterial sharePresence;
    private boolean bindingShare;
    private boolean shareLoaded;

    @Nullable private Profile current;
    private boolean initialized;
    private boolean busy;
    private boolean renameAllowed;
    @Nullable private ProfileViewModel.HandleStatus renameCheck;
    private int restoredColor = -1;
    private boolean left;
    // Mientras se borra la cuenta, el perfil que desaparece no debe llevarnos a Crear perfil.
    private boolean deleting;

    public MyProfileFragment() {
        super(R.layout.fragment_my_profile);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        TopBarHelper.bind(this, view, R.string.my_profile_top_title);
        content = view.findViewById(R.id.mpContent);
        // Edge-to-edge: el contenido llega hasta el final de la pantalla y deja sitio a la barra de navegación.
        InsetsHelper.apply(content, InsetsHelper.BOTTOM);

        status = view.findViewById(R.id.mpStatus);
        avatar = view.findViewById(R.id.mpAvatar);
        nameView = view.findViewById(R.id.mpName);
        handleView = view.findViewById(R.id.mpHandle);
        nameInput = view.findViewById(R.id.mpNameInput);
        picker = view.findViewById(R.id.mpColorPicker);
        message = view.findViewById(R.id.mpMessage);
        save = view.findViewById(R.id.mpSaveButton);
        renameHint = view.findViewById(R.id.mpRenameHint);
        renameInput = view.findViewById(R.id.mpRenameInput);
        renameStatus = view.findViewById(R.id.mpRenameStatus);
        renameButton = view.findViewById(R.id.mpRenameButton);
        deleteButton = view.findViewById(R.id.mpDeleteButton);
        sharePresence = view.findViewById(R.id.mpSharePresence);

        if (savedInstanceState != null) {
            restoredColor = savedInstanceState.getInt(STATE_COLOR, -1);
        }

        nameInput.addTextChangedListener(new ProfileUi.OnTextChanged() {
            @Override
            void onChanged(@NonNull String text) {
                hideMessage();
                refresh();
            }
        });
        picker.setOnColorSelectedListener(index -> {
            hideMessage();
            refresh();
        });
        save.setOnClickListener(v -> {
            hideMessage();
            viewModel.update(nameInput.getText().toString(), picker.getSelectedColor());
        });
        renameInput.addTextChangedListener(new ProfileUi.OnTextChanged() {
            @Override
            void onChanged(@NonNull String text) {
                hideMessage();
                viewModel.onHandleInput(text, current == null ? null : current.handle);
            }
        });
        renameButton.setOnClickListener(v -> {
            hideMessage();
            viewModel.rename(renameInput.getText().toString());
        });
        view.findViewById(R.id.mpSignOutButton).setOnClickListener(v -> viewModel.signOut());
        // F2-u7: fila «Amigos (N)»; el número sale del mismo FriendsRepository que la pantalla Amigos.
        MaterialButton friendsRow = view.findViewById(R.id.mpFriendsRow);
        friendsRow.setText(getString(R.string.my_profile_friends_row, 0));
        friendsRow.setOnClickListener(v -> NavHostFragment.findNavController(this)
                .navigate(R.id.navigation_friends));
        new ViewModelProvider(this).get(FriendsViewModel.class).getFriendCount()
                .observe(getViewLifecycleOwner(), count -> friendsRow.setText(
                        getString(R.string.my_profile_friends_row, count == null ? 0 : count)));
        // F4-u8: «Compartir mi estado en directo»; el valor sale de PresenceSharing (lo elegido o el d4).
        sharePresence.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingShare && current != null) {
                viewModel.setPresenceSharing(current.uid, checked);
            }
        });
        deleteButton.setOnClickListener(v -> {
            hideMessage();
            showDeleteConfirmation();
        });

        viewModel.getProfile().observe(getViewLifecycleOwner(), this::onProfileState);
        viewModel.getHandleStatus().observe(getViewLifecycleOwner(), s -> {
            renameCheck = s;
            refresh();
        });
        viewModel.isBusy().observe(getViewLifecycleOwner(), isBusy -> {
            busy = Boolean.TRUE.equals(isBusy);
            refresh();
        });
        viewModel.getOutcome().observe(getViewLifecycleOwner(), outcome -> {
            if (outcome == null) {
                return;
            }
            viewModel.consumeOutcome();
            switch (outcome.kind) {
                case UPDATED:
                    showMessage(R.string.my_profile_saved, false);
                    break;
                case RENAMED:
                    renameInput.setText("");
                    showMessage(R.string.my_profile_renamed, false);
                    break;
                case DELETED:
                    // La sesión ya está cerrada: el observador de AccountState saca esta pantalla.
                    Toast.makeText(requireContext().getApplicationContext(), R.string.delete_done,
                            Toast.LENGTH_LONG).show();
                    break;
                case NEEDS_REAUTH:
                    showReauthentication();
                    break;
                default:
                    deleting = false;
                    showMessage(ProfileUi.failureText(outcome.failure), true);
                    break;
            }
        });
        viewModel.getAccountState().observe(getViewLifecycleOwner(), state -> {
            if (state == AccountState.SIGNED_OUT && !left) {
                left = true;
                NavHostFragment.findNavController(this).popBackStack();
            }
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (picker != null) {
            outState.putInt(STATE_COLOR, picker.getSelectedColor());
        }
    }

    private void onProfileState(@Nullable ProfileState state) {
        if (state == null) {
            return;
        }
        switch (state.status) {
            case READY:
                if (state.profile != null) {
                    render(state.profile);
                }
                break;
            case NONE:
                if (!left && !deleting) {
                    // Con sesión pero sin perfil: toca crearlo.
                    left = true;
                    NavHostFragment.findNavController(this).navigate(R.id.navigation_create_profile, null,
                            new NavOptions.Builder().setPopUpTo(R.id.navigation_my_profile, true).build());
                }
                break;
            case ERROR:
                if (deleting) {
                    break;
                }
                current = null;
                content.setVisibility(View.GONE);
                status.setText(R.string.my_profile_error);
                status.setVisibility(View.VISIBLE);
                break;
            case LOADING:
                if (current == null) {
                    content.setVisibility(View.GONE);
                    status.setText(R.string.my_profile_loading);
                    status.setVisibility(View.VISIBLE);
                }
                break;
            default:
                break; // SIGNED_OUT: lo atiende el observador de la sesión
        }
    }

    private void render(@NonNull Profile profile) {
        current = profile;
        status.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);
        nameView.setText(profile.displayName);
        handleView.setText("@" + profile.handle);
        if (!initialized) {
            // Solo la primera vez: los cambios del servidor no pisan lo que se está editando.
            initialized = true;
            nameInput.setText(profile.displayName);
            picker.setSelectedColor(restoredColor >= 0 ? restoredColor : profile.avatarColor);
        }
        if (!shareLoaded) {
            shareLoaded = true;
            final SwitchMaterial toggle = sharePresence;
            viewModel.readPresenceSharing(profile.uid, on -> toggle.post(() -> {
                bindingShare = true;
                toggle.setChecked(on);
                toggle.setEnabled(true);
                bindingShare = false;
            }));
        }
        Date now = new Date();
        Date at = viewModel.canRenameAt(profile);
        renameAllowed = !at.after(now);
        if (renameAllowed) {
            renameHint.setText(R.string.my_profile_rename_free);
        } else {
            renameHint.setText(getString(R.string.my_profile_rename_blocked,
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(at)));
        }
        refresh();
    }

    private void refresh() {
        if (current == null || avatar == null) {
            return;
        }
        String name = nameInput.getText().toString();
        ProfileUi.paintAvatar(avatar, name.trim().isEmpty() ? current.displayName : name,
                picker.getSelectedColor());

        boolean nameValid = HandleValidator.isValidDisplayName(name);
        boolean changed = !HandleValidator.normalizeDisplayName(name).equals(current.displayName)
                || picker.getSelectedColor() != current.avatarColor;
        save.setEnabled(!busy && nameValid && changed);
        nameInput.setEnabled(!busy);
        picker.setEnabled(!busy);
        deleteButton.setEnabled(!busy);

        renameInput.setEnabled(renameAllowed && !busy);
        String typed = renameInput.getText().toString();
        boolean same = !typed.trim().isEmpty()
                && HandleValidator.normalizeHandle(typed).equals(current.handle);
        if (!renameAllowed) {
            renameStatus.setVisibility(View.GONE);
        } else if (same) {
            renameStatus.setTextAppearance(R.style.TextAppearance_BBetter_Caption);
            renameStatus.setText(R.string.my_profile_rename_same);
            renameStatus.setVisibility(View.VISIBLE);
        } else if (renameCheck != null) {
            ProfileUi.renderHandleStatus(renameStatus, renameCheck, typed, 0);
        }
        renameButton.setEnabled(renameAllowed && !busy && !same && renameCheck != null
                && renameCheck.kind == ProfileViewModel.HandleStatus.Kind.FREE);
    }

    /** Paso 1 de Borrar cuenta: qué se pierde y qué se queda en el dispositivo. */
    private void showDeleteConfirmation() {
        new AlertDialog.Builder(requireContext(), R.style.RoundedDialog)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(R.string.delete_confirm_message)
                .setNegativeButton(R.string.delete_cancel, null)
                .setPositiveButton(R.string.delete_confirm_action, (d, which) -> {
                    deleting = true;
                    viewModel.deleteAccount();
                })
                .show();
    }

    /** Paso 2, solo si el servidor lo pide: la contraseña (email) o el selector de Google otra vez. */
    private void showReauthentication() {
        if (viewModel.needsPasswordToReauthenticate()) {
            View form = getLayoutInflater().inflate(R.layout.dialog_reauth_password, null);
            EditText password = form.findViewById(R.id.reauthPasswordInput);
            new AlertDialog.Builder(requireContext(), R.style.RoundedDialog)
                    .setTitle(R.string.delete_reauth_title)
                    .setMessage(R.string.delete_reauth_password_message)
                    .setView(form)
                    .setNegativeButton(R.string.delete_cancel, (d, which) -> deleting = false)
                    .setPositiveButton(R.string.delete_reauth_continue, (d, which) ->
                            viewModel.reauthenticateAndDelete(null, password.getText().toString()))
                    .setOnCancelListener(d -> deleting = false)
                    .show();
        } else {
            new AlertDialog.Builder(requireContext(), R.style.RoundedDialog)
                    .setTitle(R.string.delete_reauth_title)
                    .setMessage(R.string.delete_reauth_google_message)
                    .setNegativeButton(R.string.delete_cancel, (d, which) -> deleting = false)
                    .setPositiveButton(R.string.delete_reauth_continue, (d, which) ->
                            viewModel.reauthenticateAndDelete(requireActivity(), null))
                    .setOnCancelListener(d -> deleting = false)
                    .show();
        }
    }

    private void showMessage(@StringRes int text, boolean isError) {
        message.setTextAppearance(isError ? R.style.TextAppearance_BBetter_SocialError
                : R.style.TextAppearance_BBetter_SocialOk);
        message.setText(text);
        message.setVisibility(View.VISIBLE);
    }

    private void hideMessage() {
        if (message != null) {
            message.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        avatar = null;
        current = null;
        initialized = false;
        shareLoaded = false;
    }
}
