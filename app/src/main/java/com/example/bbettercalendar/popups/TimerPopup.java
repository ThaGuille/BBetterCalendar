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
import com.google.android.material.snackbar.Snackbar;

public class TimerPopup extends DialogFragment implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    //todo estos final ints deberían centralizarse en una sola clase popupHelper

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
    private ToggleButton restEnabledToggleButton;
    private ToggleButton autoCycleEnabledToggleButton;
    private ToggleButton infiniteCyclesToggleButton;

    private OnPopupListener listener;
    private boolean hasChanged = false;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.RoundedDialog);

        // Inflar y establecer el layout para el dialog
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.popup_home_timer_configuration, null);
        builder.setView(view);

        // Configurar elementos del layout y manejar eventos aquí
        //El sistema de guardar notificaciones se podría hacer con un diccionario de parejas minutos/booleano

        timerTimeTextView = view.findViewById(R.id.text_time_timer);
        restTimeTextView = view.findViewById(R.id.text_time_resting);
        cyclesNumberTextView = view.findViewById(R.id.text_cycles);
        restEnabledToggleButton = view.findViewById(R.id.toggle_resting);
        autoCycleEnabledToggleButton = view.findViewById(R.id.toggle_auto_cycle);
        infiniteCyclesToggleButton = view.findViewById(R.id.toggle_infinite_cycles);

        //ImageButton tempButton = view.findViewById(R.id.btn_add_timer);
        view.findViewById(R.id.btn_add_timer).setOnClickListener(this);
        view.findViewById(R.id.btn_subtract_timer).setOnClickListener(this);
        view.findViewById(R.id.btn_add_resting_timer).setOnClickListener(this);
        view.findViewById(R.id.btn_subtract_resting_timer).setOnClickListener(this);
        view.findViewById(R.id.btn_add_cycles).setOnClickListener(this);
        view.findViewById(R.id.btn_subtract_cycles).setOnClickListener(this);

        restEnabledToggleButton.setOnCheckedChangeListener(this);
        autoCycleEnabledToggleButton.setOnCheckedChangeListener(this);
        infiniteCyclesToggleButton.setOnCheckedChangeListener(this);

        getInitialValues();
        setValues();

        return builder.create();
    }

    public void setOnPopupListener(OnPopupListener listener) {
        this.listener = listener;
    }

    private void getInitialValues(){
        timerTime = configuration.getHomeTimerTime();
        restTime = configuration.getHomeRestTime();
        isRestEnabled = configuration.isHomeIsRestEnabled();
        isAutoCycleEnabled = configuration.isHomeIsAutoCycle();
        cyclesNumber = configuration.getHomeNumberOfCycles();
    }

    private void setValues(){
        timerTimeTextView.setText(formatTime(timerTime));
        restTimeTextView.setText(formatTime(restTime));
        cyclesNumberTextView.setText(String.valueOf(cyclesNumber));
        restEnabledToggleButton.setChecked(isRestEnabled);
        autoCycleEnabledToggleButton.setChecked(isAutoCycleEnabled);
        infiniteCyclesToggleButton.setChecked(cyclesNumber == 0);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_add_timer:
                changeTime(60000, true);
                break;
            case R.id.btn_subtract_timer:
                changeTime(-60000, true);
                break;
            case R.id.btn_add_resting_timer:
                changeTime(60000, false);
                break;
            case R.id.btn_subtract_resting_timer:
                changeTime(-60000, false);
                break;
            case R.id.btn_add_cycles:
                changeCycles(1);
                break;
            case R.id.btn_subtract_cycles:
                changeCycles(-1);
                break;
            case R.id.toggle_resting:
                //todo listener.onRestEnabled();
                break;
            case R.id.toggle_auto_cycle:
                //todo listener.onAutoCycleEnabled();
                break;
            case R.id.toggle_infinite_cycles:
                //todo listener.onInfiniteCycles();
                break;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if(buttonView==restEnabledToggleButton) {
            isRestEnabled = isChecked;
        }
        if(buttonView==autoCycleEnabledToggleButton) {
            isAutoCycleEnabled = isChecked;
        }
        if(buttonView==infiniteCyclesToggleButton) {
            isInfiniteCycles = isChecked;
            if(isInfiniteCycles){
                cyclesNumberTextView.setText("∞");
            }else{
                cyclesNumberTextView.setText(String.valueOf(cyclesNumber));
            }
        }
    }

    private void changeCycles(int cyclesChange){
        if(cyclesNumber+cyclesChange <= 0 || cyclesNumber+cyclesChange > 15){
            sendErrorMessage("You can't set the number of cycles lower than 1 or higher than 15.");
            return;
        }
        cyclesNumber+=cyclesChange;
        cyclesNumberTextView.setText(String.valueOf(cyclesNumber));
    }

    private void sendErrorMessage(String message){
        try {
            Snackbar.make(this.getParentFragment().getView(), message, Snackbar.LENGTH_SHORT).show();
        }catch (NullPointerException e){
            Log.e(TAG, "the TimerPopup is not attached to any view.");
        }
    }

    private void changeTime(int timeChange, boolean isTimer ){
        if(isTimer){
            if(timerTime+timeChange <= 0 || timerTime+timeChange > FormatHelper.minutesToMillis(90)){
                sendErrorMessage("You can't set a timer with a value lower than 1 minute or higher than 90 minutes.");
                return;
            }
            timerTime+=timeChange;
            timerTimeTextView.setText(formatTime(timerTime));
        }else{
            if(restTime+timeChange <= 0 || restTime+timeChange > timerTime){
                sendErrorMessage("You can't set a rest time with a value lower than 1 minute or higher than the concentration time.");
                return;
            }
            restTime+=timeChange;
            restTimeTextView.setText(formatTime(restTime));
        }
    }



    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    private String formatTime(int milliseconds) {
        int totalSeconds = milliseconds / 1000;
        int seconds = totalSeconds % 60;
        int totalMinutes = totalSeconds / 60;
        int minutes = totalMinutes % 60;
        int hours = totalMinutes / 60;

        // Formatear el tiempo en el formato mm:ss
        return String.format("%02d:%02d", minutes, seconds);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if(configuration.getHomeTimerTime()!=timerTime || configuration.getHomeRestTime()!=restTime ||
                configuration.isHomeIsRestEnabled()!=isRestEnabled || configuration.isHomeIsAutoCycle()!=isAutoCycleEnabled ||
                configuration.getHomeNumberOfCycles()!=cyclesNumber || configuration.isHomeIsInfiniteCycleEnabled()!=isInfiniteCycles)
        {
            configuration.setHomeTimerTime(timerTime);
            configuration.setHomeRestTime(restTime);
            configuration.setHomeIsRestEnabled(isRestEnabled);
            configuration.setHomeIsAutoCycle(isAutoCycleEnabled);
            configuration.setHomeNumberOfCycles(cyclesNumber);
            configuration.setHomeIsInfiniteCycleEnabled(isInfiniteCycles);
            listener.OnClosePopup(PopupHelper.TIMER_POPUP, configuration);
        }
    }
}
