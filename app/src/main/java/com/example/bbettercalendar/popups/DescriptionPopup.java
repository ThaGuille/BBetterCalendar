package com.example.bbettercalendar.popups;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class DescriptionPopup extends BottomSheetDialogFragment {

    private OnPopupListener listener;
    private EditText descriptionText;
    private int popupType;
    private String text;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // Inflar y establecer el layout para el dialog
        View view = inflater.inflate(R.layout.popup_description, container, false);

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

        return view;
    }

    public void setOnPopupListener(OnPopupListener listener) {
        this.listener = listener;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);

    }

    @Override
    public void onStart() {
        super.onStart();
        SheetBehavior.expand(getDialog());
        // Con el teclado abierto la hoja sube por encima de el (si no, tapa el boton principal).
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }
}
