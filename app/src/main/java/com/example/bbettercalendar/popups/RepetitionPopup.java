package com.example.bbettercalendar.popups;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.example.bbettercalendar.R;

public class RepetitionPopup extends DialogFragment implements CompoundButton.OnCheckedChangeListener{

    public static final int REPETITION_NONE = 0;
    public static final int REPETITION_DAILY = 1;
    public static final int REPETITION_WEEKLY = 2;
    public static final int REPETITION_MONTHLY = 3;

    private OnPopupListener listener;
    private int repetitionToggleSelected=0;
    private ToggleButton[] toggleButtons = new ToggleButton[4];
    private boolean blocked = false;
    private final String TAG = "RepetitionPopupTAG";

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.RoundedDialog);

        // Inflar y establecer el layout para el dialog
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.popup_repetition, null);
        builder.setView(view);

        // Configurar elementos del layout y manejar eventos aquí
        //El sistema de guardar notificaciones se podría hacer con un diccionario de parejas minutos/booleano

        toggleButtons[0] = view.findViewById(R.id.repetition_popup_toggle_none);
        toggleButtons[1] = view.findViewById(R.id.repetition_popup_toggle_daily);
        toggleButtons[2] = view.findViewById(R.id.repetition_popup_toggle_weekly);
        toggleButtons[3] = view.findViewById(R.id.repetition_popup_toggle_monthly);

        toggleButtons[repetitionToggleSelected].setChecked(true);

        for(ToggleButton tb:toggleButtons){tb.setOnCheckedChangeListener(this);}

        return builder.create();
    }

    public void setOnPopupListener(OnPopupListener listener) {
        this.listener = listener;
    }


    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (blocked)
            return;
        blocked = true;
        for (ToggleButton tb : toggleButtons) {
            if (tb != buttonView) {
                tb.setChecked(false);
                tb.setEnabled(false);
            }else {
                tb.setChecked(true);
            }
        }
        if (toggleButtons[0] == buttonView)
            repetitionToggleSelected = REPETITION_NONE;
        else if (toggleButtons[1] == buttonView)
            repetitionToggleSelected = REPETITION_DAILY;
        else if (toggleButtons[2] == buttonView)
            repetitionToggleSelected = REPETITION_WEEKLY;
        else if (toggleButtons[3] == buttonView)
            repetitionToggleSelected = REPETITION_MONTHLY;
        else
            Log.d(TAG, "onCheckedChanged: ERROR");

        // Crear un Handler para retrasar la ejecución de dismiss()
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                dismiss();
            }
        }, 750); // 1000 milisegundos = 1 segundo
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        blocked = false;
        listener.OnClosePopup(PopupHelper.REPETITION_POPUP, repetitionToggleSelected);
    }

}
