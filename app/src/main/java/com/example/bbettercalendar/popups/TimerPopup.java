package com.example.bbettercalendar.popups;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.example.bbettercalendar.R;

public class TimerPopup extends DialogFragment implements View.OnClickListener {

    //todo public void setOnTimerPopupListener(OnTimerPopupListener listener) {this.listener = listener;}


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

        /**
        view.findViewById(R.id.okButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.OnClosePopup(popupType);
                dismiss();
            }
        });**/

        return builder.create();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_add_timer:
                //todo listener.onTimerAdd();
                break;
        }
    }
}
