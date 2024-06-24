package com.example.bbettercalendar.popups;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.example.bbettercalendar.R;

public class AlertPopup extends DialogFragment {

    public int popupType;
    public int popupView;
    private OnPopupListener listener;

    public void setOnPopupListener(OnPopupListener listener) {
        this.listener = listener;
    }

    public void selectView(int popupType) {
        this.popupType = popupType;
        switch (popupType) {
            case PopupHelper.ALERT_POPUP:
                this.popupView = R.layout.popup_error;
                break;
            default:
                this.popupView =0;
                break;
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if(popupView==0) return null;
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.RoundedDialog);

        // Inflar y establecer el layout para el dialog
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = null;
        if (popupView!=0) view = inflater.inflate(popupView, null);
        builder.setView(view);

        // Configurar elementos del layout y manejar eventos aquí
        //El sistema de guardar notificaciones se podría hacer con un diccionario de parejas minutos/booleano

        view.findViewById(R.id.okButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        return builder.create();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        listener.OnClosePopup(popupType);
    }

}
