package com.example.bbettercalendar.popups;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.example.bbettercalendar.R;

public class DescriptionPopup extends DialogFragment {

    private OnPopupListener listener;
    private EditText descriptionText;
    private int popupType;
    private String text;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.RoundedDialog);
        // Inflar y establecer el layout para el dialog
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.popup_description, null);
        builder.setView(view);

        popupType = PopupHelper.DESCRIPTION_POPUP;
        descriptionText = view.findViewById(R.id.popup_description_edit_text);

        if(text!=null)
            descriptionText.setText(text);

        view.findViewById(R.id.okButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!descriptionText.getText().toString().isEmpty()) {
                    text = descriptionText.getText().toString();
                    listener.OnClosePopup(popupType, text);
                }
                dismiss();
            }
        });

        return builder.create();
    }

    public void setOnPopupListener(OnPopupListener listener) {
        this.listener = listener;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);

    }
}
