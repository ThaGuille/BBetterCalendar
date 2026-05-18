package com.example.bbettercalendar.popups;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.configuration.Configuration;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class TimerPopup extends DialogFragment implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    private static final int MIN_MINUTES = 1;
    private static final int MAX_MINUTES = 90;
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
    private TextView cyclesNumberTextView;
    private Slider timerSlider;
    private Slider restSlider;
    private View restContent;
    private SwitchMaterial restEnabledSwitch;
    private SwitchMaterial autoCycleSwitch;
    private ToggleButton infiniteCyclesToggle;
    private ImageButton btnAddCycles;
    private ImageButton btnSubtractCycles;

    private OnPopupListener listener;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.RoundedDialog);

        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.popup_home_timer_configuration, null);
        builder.setView(view);

        timerTimeTextView = view.findViewById(R.id.text_time_timer);
        restTimeTextView = view.findViewById(R.id.text_time_resting);
        cyclesNumberTextView = view.findViewById(R.id.text_cycles);
        timerSlider = view.findViewById(R.id.slider_timer);
        restSlider = view.findViewById(R.id.slider_rest);
        restContent = view.findViewById(R.id.rest_content);
        restEnabledSwitch = view.findViewById(R.id.switch_rest_enabled);
        autoCycleSwitch = view.findViewById(R.id.switch_auto_cycle);
        infiniteCyclesToggle = view.findViewById(R.id.toggle_infinite_cycles);
        btnAddCycles = view.findViewById(R.id.btn_add_cycles);
        btnSubtractCycles = view.findViewById(R.id.btn_subtract_cycles);

        btnAddCycles.setOnClickListener(this);
        btnSubtractCycles.setOnClickListener(this);
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
            updateRestSliderBounds();
        });

        restSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser) return;
            int newMinutes = (int) value;
            restTime = FormatHelper.minutesToMillis(newMinutes);
            restTimeTextView.setText(formatTime(restTime));
        });

        return builder.create();
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

        updateRestSliderBounds();

        restEnabledSwitch.setChecked(isRestEnabled);
        autoCycleSwitch.setChecked(isAutoCycleEnabled);
        infiniteCyclesToggle.setChecked(isInfiniteCycles);

        applyRestEnabledState();
        applyInfiniteCyclesState();
    }

    private void updateRestSliderBounds() {
        int maxRestMinutes = Math.max(MIN_MINUTES, timerTime / 60000);
        restSlider.setValueFrom(MIN_MINUTES);
        restSlider.setValueTo(maxRestMinutes);

        int restMinutes = clamp(restTime / 60000, MIN_MINUTES, maxRestMinutes);
        restTime = FormatHelper.minutesToMillis(restMinutes);
        restSlider.setValue(restMinutes);
        restTimeTextView.setText(formatTime(restTime));
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.btn_add_cycles) {
            changeCycles(1);
        } else if (id == R.id.btn_subtract_cycles) {
            changeCycles(-1);
        } else if (id == R.id.btn_restore) {
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
        restSlider.setEnabled(isRestEnabled);
    }

    private void applyInfiniteCyclesState() {
        float alpha = isInfiniteCycles ? 0.4f : 1f;
        btnAddCycles.setEnabled(!isInfiniteCycles);
        btnSubtractCycles.setEnabled(!isInfiniteCycles);
        btnAddCycles.setAlpha(alpha);
        btnSubtractCycles.setAlpha(alpha);
        cyclesNumberTextView.setText(isInfiniteCycles
                ? getString(R.string.timer_infinite_symbol)
                : String.valueOf(cyclesNumber));
    }

    private void changeCycles(int cyclesChange) {
        int next = cyclesNumber + cyclesChange;
        if (next < MIN_CYCLES || next > MAX_CYCLES) {
            sendErrorMessage(getString(R.string.timer_error_cycles_range));
            return;
        }
        cyclesNumber = next;
        cyclesNumberTextView.setText(String.valueOf(cyclesNumber));
    }

    private void sendErrorMessage(String message) {
        try {
            Snackbar.make(this.getParentFragment().getView(), message, Snackbar.LENGTH_SHORT).show();
        } catch (NullPointerException e) {
            Log.e(TAG, "the TimerPopup is not attached to any view.");
        }
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
}
