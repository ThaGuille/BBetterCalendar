package com.example.bbettercalendar.popups;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.example.bbettercalendar.configuration.Configuration;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.example.bbettercalendar.ui.common.widget.StepperView;
import com.google.android.material.chip.Chip;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class TimerPopup extends BottomSheetDialogFragment implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    private static final int MIN_MINUTES = 1;
    private static final int MAX_MINUTES = 90;
    private static final int MAX_REST_MINUTES = 30;
    private static final int MIN_CYCLES = 1;
    private static final int MAX_CYCLES = 15;
    private static final int DEFAULT_TIMER_MS = 60000 * 20;
    private static final int DEFAULT_REST_MS = 60000 * 5;
    private static final int DEFAULT_CYCLES = 3;

    private final String TAG = "TimerPopupTag";
    private Configuration configuration;
    private int timerTime;
    private int restTime;
    private boolean isRestEnabled;
    private boolean isAutoCycleEnabled;
    private boolean isInfiniteCycles;
    private int cyclesNumber;

    private TextView timerTimeTextView;
    private TextView restTimeTextView;
    private Slider timerSlider;
    private Slider restSlider;
    private View restContent;
    private SwitchMaterial restEnabledSwitch;
    private SwitchMaterial autoCycleSwitch;
    private ToggleButton infiniteCyclesToggle;
    private StepperView cyclesStepper;
    private final Chip[] presetChips = new Chip[3];
    // Concentracion / descanso (minutos) de cada chip rapido: 25/5, 50/10, 90/15.
    private static final int[][] PRESETS = {{25, 5}, {50, 10}, {90, 15}};

    private OnPopupListener listener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {


        View view = inflater.inflate(R.layout.popup_home_timer_configuration, container, false);

        timerTimeTextView = view.findViewById(R.id.text_time_timer);
        restTimeTextView = view.findViewById(R.id.text_time_resting);
        timerSlider = view.findViewById(R.id.slider_timer);
        restSlider = view.findViewById(R.id.slider_rest);
        restContent = view.findViewById(R.id.rest_content);
        restEnabledSwitch = view.findViewById(R.id.switch_rest_enabled);
        autoCycleSwitch = view.findViewById(R.id.switch_auto_cycle);
        infiniteCyclesToggle = view.findViewById(R.id.toggle_infinite_cycles);
        cyclesStepper = view.findViewById(R.id.stepper_cycles);
        cyclesStepper.setRange(MIN_CYCLES, MAX_CYCLES, 1);
        cyclesStepper.setOnValueChangeListener((stepper, value) -> cyclesNumber = value);
        presetChips[0] = view.findViewById(R.id.chip_preset_25);
        presetChips[1] = view.findViewById(R.id.chip_preset_50);
        presetChips[2] = view.findViewById(R.id.chip_preset_90);
        for (int i = 0; i < presetChips.length; i++) {
            final int idx = i;
            presetChips[i].setOnClickListener(v -> applyPreset(PRESETS[idx][0], PRESETS[idx][1]));
        }
        view.findViewById(R.id.btn_restore).setOnClickListener(this);
        view.findViewById(R.id.btn_save).setOnClickListener(this);

        getInitialValues();
        bindValues();

        restEnabledSwitch.setOnCheckedChangeListener(this);
        autoCycleSwitch.setOnCheckedChangeListener(this);
        infiniteCyclesToggle.setOnCheckedChangeListener(this);

        timerSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser) return;
            int newMinutes = (int) value;
            timerTime = FormatHelper.minutesToMillis(newMinutes);
            timerTimeTextView.setText(formatTime(timerTime));
            syncPresetChips();
        });

        restSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser) return;
            int newMinutes = (int) value;
            restTime = FormatHelper.minutesToMillis(newMinutes);
            restTimeTextView.setText(formatTime(restTime));
            syncPresetChips();
        });

        return view;
    }

    public void setOnPopupListener(OnPopupListener listener) {
        this.listener = listener;
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    private void getInitialValues() {
        timerTime = configuration.getHomeTimerTime();
        restTime = configuration.getHomeRestTime();
        isRestEnabled = configuration.isHomeIsRestEnabled();
        isAutoCycleEnabled = configuration.isHomeIsAutoCycle();
        cyclesNumber = configuration.getHomeNumberOfCycles();
        isInfiniteCycles = configuration.isHomeIsInfiniteCycleEnabled() || cyclesNumber <= 0;
        if (cyclesNumber <= 0) {
            cyclesNumber = DEFAULT_CYCLES;
        }
    }

    private void getRestoredValues() {
        timerTime = DEFAULT_TIMER_MS;
        restTime = DEFAULT_REST_MS;
        isRestEnabled = true;
        isAutoCycleEnabled = false;
        isInfiniteCycles = false;
        cyclesNumber = DEFAULT_CYCLES;
    }

    private void bindValues() {
        int timerMinutes = clamp(timerTime / 60000, MIN_MINUTES, MAX_MINUTES);
        timerSlider.setValueFrom(MIN_MINUTES);
        timerSlider.setValueTo(MAX_MINUTES);
        timerSlider.setValue(timerMinutes);
        timerTimeTextView.setText(formatTime(timerTime));

        int restMinutes = clamp(restTime / 60000, MIN_MINUTES, MAX_REST_MINUTES);
        restTime = FormatHelper.minutesToMillis(restMinutes);
        restSlider.setValueFrom(MIN_MINUTES);
        restSlider.setValueTo(MAX_REST_MINUTES);
        restSlider.setValue(restMinutes);
        restTimeTextView.setText(formatTime(restTime));

        restEnabledSwitch.setChecked(isRestEnabled);
        autoCycleSwitch.setChecked(isAutoCycleEnabled);
        infiniteCyclesToggle.setChecked(isInfiniteCycles);

        applyRestEnabledState();
        applyInfiniteCyclesState();
        syncPresetChips();
    }

    /** Chip rapido: ajusta concentracion y descanso a la vez y activa el descanso. */
    private void applyPreset(int focusMinutes, int restMinutes) {
        timerTime = FormatHelper.minutesToMillis(focusMinutes);
        restTime = FormatHelper.minutesToMillis(restMinutes);
        timerSlider.setValue(focusMinutes);
        restSlider.setValue(restMinutes);
        timerTimeTextView.setText(formatTime(timerTime));
        restTimeTextView.setText(formatTime(restTime));
        if (!isRestEnabled) {
            isRestEnabled = true;
            restEnabledSwitch.setChecked(true);
        }
        syncPresetChips();
    }

    /** Un chip queda marcado solo mientras coincidan los dos valores actuales. */
    private void syncPresetChips() {
        for (int i = 0; i < presetChips.length; i++) {
            presetChips[i].setChecked(timerTime == FormatHelper.minutesToMillis(PRESETS[i][0])
                    && restTime == FormatHelper.minutesToMillis(PRESETS[i][1]));
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.btn_restore) {
            getRestoredValues();
            bindValues();
        } else if (id == R.id.btn_save) {
            saveValues();
            dismiss();
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView == restEnabledSwitch) {
            isRestEnabled = isChecked;
            applyRestEnabledState();
        } else if (buttonView == autoCycleSwitch) {
            isAutoCycleEnabled = isChecked;
        } else if (buttonView == infiniteCyclesToggle) {
            isInfiniteCycles = isChecked;
            applyInfiniteCyclesState();
        }
    }

    private void applyRestEnabledState() {
        float alpha = isRestEnabled ? 1f : 0.4f;
        restContent.setAlpha(alpha);
        restTimeTextView.setAlpha(alpha);
        restSlider.setEnabled(isRestEnabled);
    }

    private void applyInfiniteCyclesState() {
        cyclesStepper.setValueFormatter(isInfiniteCycles
                ? value -> getString(R.string.timer_infinite_symbol)
                : null);
        cyclesStepper.setValue(cyclesNumber);
        cyclesStepper.setEnabled(!isInfiniteCycles);
        cyclesStepper.setAlpha(isInfiniteCycles ? 0.4f : 1f);
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private String formatTime(int milliseconds) {
        int totalSeconds = milliseconds / 1000;
        int seconds = totalSeconds % 60;
        int totalMinutes = totalSeconds / 60;
        int minutes = totalMinutes % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void saveValues() {
        if (configuration.getHomeTimerTime() != timerTime
                || configuration.getHomeRestTime() != restTime
                || configuration.isHomeIsRestEnabled() != isRestEnabled
                || configuration.isHomeIsAutoCycle() != isAutoCycleEnabled
                || configuration.getHomeNumberOfCycles() != cyclesNumber
                || configuration.isHomeIsInfiniteCycleEnabled() != isInfiniteCycles) {
            configuration.setHomeTimerTime(timerTime);
            configuration.setHomeRestTime(restTime);
            configuration.setHomeIsRestEnabled(isRestEnabled);
            configuration.setHomeIsAutoCycle(isAutoCycleEnabled);
            configuration.setHomeNumberOfCycles(cyclesNumber);
            configuration.setHomeIsInfiniteCycleEnabled(isInfiniteCycles);
            listener.OnClosePopup(PopupHelper.TIMER_POPUP, configuration);
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
    }

    @Override
    public void onStart() {
        super.onStart();
        SheetBehavior.expand(getDialog());
    }
}
