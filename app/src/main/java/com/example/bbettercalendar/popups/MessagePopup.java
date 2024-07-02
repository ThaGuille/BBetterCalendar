package com.example.bbettercalendar.popups;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.example.bbettercalendar.R;

public class MessagePopup extends DialogFragment {

    private OnPopupListener listener;
    private TextView popupText;
    private int popupType;


    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.RoundedDialog);
        // Inflar y establecer el layout para el dialog
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.popup_message, null);
        builder.setView(view);

        popupType = PopupHelper.MESSAGE_POPUP;
        popupText = view.findViewById(R.id.popupMessageText);
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

    public void setOnPopupListener(OnPopupListener listener) {
        this.listener = listener;
    }

    //Previamente hay que haber llamado a selectView
    public void setText(String text) {
        if(popupText!=null) popupText.setText(text);
    }



    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        listener.OnClosePopup(popupType);
    }
}
