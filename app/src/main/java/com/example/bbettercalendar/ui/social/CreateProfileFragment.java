package com.example.bbettercalendar.ui.social;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.social.account.AccountState;
import com.example.bbettercalendar.social.profile.HandleValidator;
import com.example.bbettercalendar.social.profile.ProfileState;
import com.example.bbettercalendar.ui.common.InsetsHelper;
import com.example.bbettercalendar.ui.common.TopBarHelper;
import com.example.bbettercalendar.ui.common.widget.AvatarView;
import com.google.android.material.button.MaterialButton;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * F1-u5: Crear perfil. Nombre, {@code @usuario} con validación en vivo y comprobación de
 * disponibilidad (con retardo), color de avatar y casilla de términos. «Crear perfil» escribe
 * users/, profiles/ y handles/ en un solo lote y termina en {@link MyProfileFragment}.
 */
@AndroidEntryPoint
public class CreateProfileFragment extends Fragment {

    private static final String STATE_COLOR = "color";

    private ProfileViewModel viewModel;
    private AvatarView avatar;
    private EditText nameInput;
    private EditText handleInput;
    private TextView handleStatus;
    private AvatarColorPickerView picker;
    private CheckBox termsCheck;
    private TextView error;
    private MaterialButton save;

    private boolean busy;
    @Nullable private ProfileViewModel.HandleStatus lastStatus;
    private int saveErrorText;
    private boolean navigated;

    public CreateProfileFragment() {
        super(R.layout.fragment_create_profile);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        TopBarHelper.bind(this, view, R.string.create_profile_top_title);
        // Edge-to-edge: el contenido llega hasta el final de la pantalla y deja sitio a la barra de navegación.
        InsetsHelper.apply(view.findViewById(R.id.cpScroll), InsetsHelper.BOTTOM);

        avatar = view.findViewById(R.id.cpAvatarPreview);
        nameInput = view.findViewById(R.id.cpNameInput);
        handleInput = view.findViewById(R.id.cpHandleInput);
        handleStatus = view.findViewById(R.id.cpHandleStatus);
        picker = view.findViewById(R.id.cpColorPicker);
        termsCheck = view.findViewById(R.id.cpTermsCheck);
        error = view.findViewById(R.id.cpError);
        save = view.findViewById(R.id.cpSaveButton);

        if (savedInstanceState != null) {
            picker.setSelectedColor(savedInstanceState.getInt(STATE_COLOR, 0));
        }
        bindTermsText(view.findViewById(R.id.cpTermsText));

        nameInput.addTextChangedListener(new ProfileUi.OnTextChanged() {
            @Override
            void onChanged(@NonNull String text) {
                saveErrorText = 0;
                refresh();
            }
        });
        handleInput.addTextChangedListener(new ProfileUi.OnTextChanged() {
            @Override
            void onChanged(@NonNull String text) {
                saveErrorText = 0;
                viewModel.onHandleInput(text, null);
            }
        });
        picker.setOnColorSelectedListener(index -> refresh());
        termsCheck.setOnCheckedChangeListener((b, checked) -> refresh());
        save.setOnClickListener(v -> {
            saveErrorText = 0;
            viewModel.create(nameInput.getText().toString(),
                    HandleValidator.normalizeHandle(handleInput.getText().toString()),
                    picker.getSelectedColor());
        });

        viewModel.getHandleStatus().observe(getViewLifecycleOwner(), status -> {
            lastStatus = status;
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
            if (outcome.kind == ProfileViewModel.Outcome.Kind.FAILED) {
                saveErrorText = ProfileUi.failureText(outcome.failure);
                if (outcome.failure == ProfileViewModel.Failure.HANDLE_TAKEN) {
                    // Que la línea de estado también lo diga: vuelve a mirar el servidor.
                    viewModel.onHandleInput(handleInput.getText().toString(), null);
                }
                refresh();
            } else {
                openMyProfile();
            }
        });
        // El perfil ya existe (carrera con otro dispositivo, o el listener llega antes que el callback).
        viewModel.getProfile().observe(getViewLifecycleOwner(), state -> {
            if (state != null && state.status == ProfileState.Status.READY) {
                openMyProfile();
            }
        });
        viewModel.getAccountState().observe(getViewLifecycleOwner(), state -> {
            if (state == AccountState.SIGNED_OUT && !navigated) {
                navigated = true;
                NavHostFragment.findNavController(this).popBackStack();
            }
        });
        refresh();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (picker != null) {
            outState.putInt(STATE_COLOR, picker.getSelectedColor());
        }
    }

    private void openMyProfile() {
        if (navigated) {
            return;
        }
        navigated = true;
        NavController nav = NavHostFragment.findNavController(this);
        nav.navigate(R.id.navigation_my_profile, null, new NavOptions.Builder()
                .setPopUpTo(R.id.navigation_create_profile, true).build());
    }

    /** Vuelve a pintar todo lo que depende de lo tecleado: avatar, estado del usuario, error y botón. */
    private void refresh() {
        if (avatar == null) {
            return;
        }
        String name = nameInput.getText().toString();
        ProfileUi.paintAvatar(avatar, name, picker.getSelectedColor());
        if (lastStatus != null) {
            ProfileUi.renderHandleStatus(handleStatus, lastStatus, handleInput.getText().toString(),
                    R.string.create_profile_handle_help);
        }
        HandleValidator.NameResult nameResult = HandleValidator.checkDisplayName(name);
        int errorText = saveErrorText != 0 ? saveErrorText
                : nameResult == HandleValidator.NameResult.BANNED ? R.string.name_status_banned : 0;
        if (errorText == 0) {
            error.setVisibility(View.GONE);
        } else {
            error.setText(errorText);
            error.setVisibility(View.VISIBLE);
        }
        boolean handleFree = lastStatus != null && lastStatus.kind == ProfileViewModel.HandleStatus.Kind.FREE;
        save.setEnabled(!busy && handleFree && nameResult == HandleValidator.NameResult.OK
                && termsCheck.isChecked());
        nameInput.setEnabled(!busy);
        handleInput.setEnabled(!busy);
        picker.setEnabled(!busy);
    }

    /** «Acepto los Términos de uso»: solo las dos últimas palabras abren la URL. */
    private void bindTermsText(@NonNull TextView textView) {
        String prefix = getString(R.string.create_profile_terms_prefix);
        String link = getString(R.string.create_profile_terms_link);
        SpannableString text = new SpannableString(prefix + link);
        text.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse(getString(R.string.create_profile_terms_url))));
                } catch (ActivityNotFoundException ignored) {
                    // Sin navegador: la casilla sigue funcionando.
                }
            }
        }, prefix.length(), text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        textView.setText(text);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setLinkTextColor(ContextCompat.getColor(requireContext(), R.color.bb_primary_bright));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        avatar = null;
    }
}
