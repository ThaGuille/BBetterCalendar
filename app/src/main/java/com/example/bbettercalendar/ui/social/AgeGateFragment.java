package com.example.bbettercalendar.ui.social;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.social.account.SocialEntryRouter;
import com.example.bbettercalendar.ui.common.TopBarHelper;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * F1-u4: puerta de edad neutral (04 §1.1). Pide solo el año de nacimiento: sin valor
 * preseleccionado y sin pistas de qué edad desbloquea qué. El año se guarda una vez (SocialPrefs)
 * y el router decide el siguiente paso: pantalla amable por debajo del umbral, hoja «Únete» por
 * encima.
 */
@AndroidEntryPoint
public class AgeGateFragment extends Fragment {

    private static final int MIN_YEAR = 1900;

    private SocialEntryViewModel viewModel;
    private EditText yearInput;
    private TextView error;

    public AgeGateFragment() {
        super(R.layout.fragment_age_gate);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SocialEntryViewModel.class);
        TopBarHelper.bind(this, view, R.string.age_gate_top_title);

        yearInput = view.findViewById(R.id.ageGateYearInput);
        error = view.findViewById(R.id.ageGateError);
        view.findViewById(R.id.ageGateContinueButton).setOnClickListener(v -> submit());
        yearInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
    }

    private void submit() {
        int year = parseYear(yearInput.getText().toString());
        if (year < 0) {
            error.setVisibility(View.VISIBLE);
            return;
        }
        error.setVisibility(View.GONE);
        SocialEntryRouter.Step step = viewModel.saveBirthYear(year);
        SocialEntryNavigator.goReplacing(this, step, R.id.navigation_age_gate);
    }

    /** El año tecleado si es plausible (1900..año actual); si no, -1. */
    static int parseYear(@NonNull String raw) {
        String text = raw.trim();
        if (text.length() != 4) {
            return -1;
        }
        try {
            int year = Integer.parseInt(text);
            return year >= MIN_YEAR && year <= SocialEntryViewModel.currentYear() ? year : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
