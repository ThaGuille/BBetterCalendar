package com.example.bbettercalendar.popups;

import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.feedback.HapticFeedback;
import com.example.bbettercalendar.feedback.SoundFeedback;

/**
 * Recompensa por el primer pomodoro del día (spec focus-mode-and-streak).
 *
 * <p>Deliberadamente NO es un {@code MessagePopup}: aquí el punto es que se sienta premio y no
 * cartel. Por eso ventana transparente ({@code R.style.RewardDialog}), entrada con escala +
 * overshoot, la llama con un latido y el contador de días subiendo desde 0. El número que muestra
 * lo calcula {@code HomeViewModel} en el mismo executor que inserta el FocusEvent.
 */
public class FirstFocusCelebrationPopup extends DialogFragment {

    public static final String POPUP_TAG = "first_focus_popup";
    private static final String ARG_DAYS = "days_this_month";

    public static FirstFocusCelebrationPopup newInstance(int daysThisMonth) {
        FirstFocusCelebrationPopup popup = new FirstFocusCelebrationPopup();
        Bundle args = new Bundle();
        args.putInt(ARG_DAYS, daysThisMonth);
        popup.setArguments(args);
        return popup;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_TITLE, R.style.RewardDialog);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.popup_first_focus, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        int days = getArguments() != null ? getArguments().getInt(ARG_DAYS, 1) : 1;
        TextView daysNumber = view.findViewById(R.id.firstFocusDaysNumber);
        TextView daysLabel = view.findViewById(R.id.firstFocusDaysLabel);
        View card = view.findViewById(R.id.firstFocusCard);
        View badge = view.findViewById(R.id.firstFocusBadge);

        daysNumber.setText(String.valueOf(days));
        daysLabel.setText(getResources()
                .getQuantityString(R.plurals.first_focus_days_this_month, days));
        view.findViewById(R.id.firstFocusButton).setOnClickListener(v -> dismiss());

        // Sólo en la primera aparición: tras una rotación el premio ya se "cobró" y repetir la
        // animación y el sonido lo convertiría justo en el cartel pesado que queríamos evitar.
        if (savedInstanceState == null) {
            animateIn(card, badge, daysNumber, days);
            HapticFeedback.confirm(card);
            SoundFeedback.get(requireContext()).playSuccess();
        }
    }

    private void animateIn(View card, View badge, TextView daysNumber, int days) {
        card.setAlpha(0f);
        card.setScaleX(0.8f);
        card.setScaleY(0.8f);
        card.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(360)
                .setInterpolator(new OvershootInterpolator(2.2f))
                .start();

        // Latido de la llama, encadenado al final de la entrada.
        badge.animate().setStartDelay(320).scaleX(1.25f).scaleY(1.25f).setDuration(180)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> badge.animate().scaleX(1f).scaleY(1f).setDuration(220).start())
                .start();

        if (days <= 1) {
            return; // contar de 0 a 1 no es una animación, es un parpadeo
        }
        ValueAnimator counter = ValueAnimator.ofInt(0, days);
        counter.setDuration(700);
        counter.setStartDelay(200);
        counter.setInterpolator(new DecelerateInterpolator());
        counter.addUpdateListener(animation -> {
            if (isAdded()) {
                daysNumber.setText(String.valueOf(animation.getAnimatedValue()));
            }
        });
        counter.start();
    }

    @Override
    public void onStart() {
        super.onStart();
        // La ventana es transparente: sin esto el diálogo se encogería al ancho del texto y la
        // tarjeta perdería sus márgenes simétricos.
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}
