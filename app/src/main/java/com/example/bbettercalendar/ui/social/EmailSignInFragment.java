package com.example.bbettercalendar.ui.social;

import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.TopBarHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * F1-u4: alta e inicio de sesión con email y contraseña, y «olvidé mi contraseña» (D-023). Un solo
 * formulario con dos modos. Al iniciar sesión vuelve al origen (la sesión la refleja
 * AccountRepository); el perfil llega con F1-u5.
 */
@AndroidEntryPoint
public class EmailSignInFragment extends Fragment {

    private static final String STATE_SIGN_UP = "sign_up";

    private SocialEntryViewModel viewModel;
    private boolean signUpMode = true;

    private MaterialToolbar toolbar;
    private TextView title;
    private EditText emailInput;
    private EditText passwordInput;
    private TextView message;
    private MaterialButton submit;
    private MaterialButton switchMode;
    private MaterialButton forgot;

    public EmailSignInFragment() {
        super(R.layout.fragment_email_sign_in);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SocialEntryViewModel.class);
        if (savedInstanceState != null) {
            signUpMode = savedInstanceState.getBoolean(STATE_SIGN_UP, true);
        }

        toolbar = TopBarHelper.bind(this, view, R.string.email_top_title_sign_up);
        title = view.findViewById(R.id.emailTitle);
        emailInput = view.findViewById(R.id.emailInput);
        passwordInput = view.findViewById(R.id.passwordInput);
        message = view.findViewById(R.id.emailMessage);
        submit = view.findViewById(R.id.emailSubmitButton);
        switchMode = view.findViewById(R.id.emailSwitchButton);
        forgot = view.findViewById(R.id.emailForgotButton);

        submit.setOnClickListener(v -> submit());
        switchMode.setOnClickListener(v -> {
            signUpMode = !signUpMode;
            hideMessage();
            renderMode();
        });
        forgot.setOnClickListener(v -> forgotPassword());
        passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        renderMode();

        viewModel.isBusy().observe(getViewLifecycleOwner(), busy -> {
            boolean enabled = !Boolean.TRUE.equals(busy);
            submit.setEnabled(enabled);
            switchMode.setEnabled(enabled);
            forgot.setEnabled(enabled);
        });
        viewModel.getOutcome().observe(getViewLifecycleOwner(), outcome -> {
            if (outcome == null) {
                return;
            }
            viewModel.consumeOutcome();
            switch (outcome.kind) {
                case SIGNED_IN:
                    NavHostFragment.findNavController(this).popBackStack();
                    break;
                case RESET_SENT:
                    showMessage(R.string.email_reset_sent);
                    break;
                default:
                    showMessage(errorText(outcome.failure));
                    break;
            }
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_SIGN_UP, signUpMode);
    }

    private void renderMode() {
        if (toolbar != null) {
            toolbar.setTitle(signUpMode
                    ? R.string.email_top_title_sign_up : R.string.email_top_title_sign_in);
        }
        title.setText(signUpMode ? R.string.email_title_sign_up : R.string.email_title_sign_in);
        submit.setText(signUpMode ? R.string.email_submit_sign_up : R.string.email_submit_sign_in);
        switchMode.setText(signUpMode
                ? R.string.email_switch_to_sign_in : R.string.email_switch_to_sign_up);
        forgot.setVisibility(signUpMode ? View.GONE : View.VISIBLE);
    }

    private void submit() {
        hideMessage();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showMessage(R.string.email_error_invalid_email);
            return;
        }
        if (signUpMode && password.length() < 6) {
            showMessage(R.string.email_error_weak_password);
            return;
        }
        if (password.isEmpty()) {
            showMessage(R.string.email_error_bad_credentials);
            return;
        }
        if (signUpMode) {
            viewModel.signUp(email, password);
        } else {
            viewModel.signIn(email, password);
        }
    }

    private void forgotPassword() {
        hideMessage();
        String email = emailInput.getText().toString().trim();
        if (email.isEmpty()) {
            showMessage(R.string.email_reset_needs_email);
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showMessage(R.string.email_error_invalid_email);
            return;
        }
        viewModel.sendPasswordReset(email);
    }

    @StringRes
    private static int errorText(@Nullable SocialEntryViewModel.Failure failure) {
        if (failure == null) {
            return R.string.email_error_generic;
        }
        switch (failure) {
            case INVALID_EMAIL:
                return R.string.email_error_invalid_email;
            case WEAK_PASSWORD:
                return R.string.email_error_weak_password;
            case EMAIL_IN_USE:
                return R.string.email_error_in_use;
            case BAD_CREDENTIALS:
                return R.string.email_error_bad_credentials;
            case NETWORK:
                return R.string.join_error_network;
            default:
                return R.string.email_error_generic;
        }
    }

    private void showMessage(@StringRes int text) {
        message.setText(text);
        message.setVisibility(View.VISIBLE);
    }

    private void hideMessage() {
        message.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        toolbar = null;
    }
}
