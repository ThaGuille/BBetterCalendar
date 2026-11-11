package com.example.bbettercalendar.popups;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationsPopup extends BottomSheetDialogFragment implements CompoundButton.OnCheckedChangeListener {

    private OnNotificationsPopupListener listener;
    private boolean[] notificationsArray = new boolean[7];
    private ToggleButton[] toggleButtons = new ToggleButton[7];
    private final String TAG = "NotificationsPopupTAG";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // Inflar y establecer el layout para el dialog
        View view = inflater.inflate(R.layout.popup_notifications, container, false);

        // Configurar elementos del layout y manejar eventos aquí
        //El sistema de guardar notificaciones se podría hacer con un diccionario de parejas minutos/booleano

        toggleButtons[0] = view.findViewById(R.id.notifications_popup_toggle_button_5min);
        toggleButtons[1] = view.findViewById(R.id.notifications_popup_toggle_button_10min);
        toggleButtons[2] = view.findViewById(R.id.notifications_popup_toggle_button_15min);
        toggleButtons[3] = view.findViewById(R.id.notifications_popup_toggle_button_30min);
        toggleButtons[4] = view.findViewById(R.id.notifications_popup_toggle_button_1h);
        toggleButtons[5] = view.findViewById(R.id.notifications_popup_toggle_button_3h);
        toggleButtons[6] = view.findViewById(R.id.notifications_popup_toggle_button_1d);

        for(ToggleButton tb:toggleButtons){tb.setOnCheckedChangeListener(this);}

        // Whole-row click toggles the corresponding ToggleButton (which is itself non-clickable).
        int[] rowIds = {
                R.id.notifications_popup_row_5min,
                R.id.notifications_popup_row_10min,
                R.id.notifications_popup_row_15min,
                R.id.notifications_popup_row_30min,
                R.id.notifications_popup_row_1h,
                R.id.notifications_popup_row_3h,
                R.id.notifications_popup_row_1d
        };
        for (int i = 0; i < rowIds.length; i++) {
            final int idx = i;
            view.findViewById(rowIds[i]).setOnClickListener(v -> toggleButtons[idx].toggle());
        }

        setToggleButtons();

        view.findViewById(R.id.btnCloseNotificationsPopup).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                notificationsArray = new boolean[]{false,false,false,false,false,false,false};
                dismiss();
            }
        });
        view.findViewById(R.id.btnSaveNotificationsPopup).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.OnSetNotifications(notificationsArray);
                dismiss();
            }
        });

        return view;
    }

    private void setToggleButtons(){
        for(int i=0;i<toggleButtons.length;i++){
            toggleButtons[i].setChecked(notificationsArray[i]);
        }
    }

    public void setOnNotificationsPopupListener(OnNotificationsPopupListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if(toggleButtons[0]==buttonView)
            notificationsArray[0] = isChecked;
        else if(toggleButtons[1]==buttonView)
            notificationsArray[1] = isChecked;
        else if(toggleButtons[2]==buttonView)
            notificationsArray[2] = isChecked;
        else if(toggleButtons[3]==buttonView)
            notificationsArray[3] = isChecked;
        else if(toggleButtons[4]==buttonView)
            notificationsArray[4] = isChecked;
        else if(toggleButtons[5]==buttonView)
            notificationsArray[5] = isChecked;
        else if(toggleButtons[6]==buttonView)
            notificationsArray[6] = isChecked;
        else
            Log.d(TAG, "onCheckedChanged: ERROR");
        /*
        switch (buttonView.getId()) {
            case R.id.notifications_popup_toggle_button_5min:
                notificationsArray[0] = isChecked;
                // Manejar toggleButton1
                break;
            case R.id.notifications_popup_toggle_button_10min:
                notificationsArray[1] = isChecked;
                // Manejar toggleButton2
                break;
            case R.id.notifications_popup_toggle_button_15min:
                notificationsArray[2] = isChecked;
                // Manejar toggleButton3
                break;
            case R.id.notifications_popup_toggle_button_30min:
                notificationsArray[3] = isChecked;
                // Manejar toggleButton4
                break;
            case R.id.notifications_popup_toggle_button_1h:
                notificationsArray[4] = isChecked;
                // Manejar toggleButton5
                break;
            case R.id.notifications_popup_toggle_button_3h:
                notificationsArray[5] = isChecked;
                break;
            case R.id.notifications_popup_toggle_button_1d:
                notificationsArray[6] = isChecked;
                break;
        }*/
    }



    @Override
    public void onStart() {
        super.onStart();
        SheetBehavior.expand(getDialog());
    }
}
