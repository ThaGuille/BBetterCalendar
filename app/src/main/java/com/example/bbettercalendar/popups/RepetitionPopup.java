package com.example.bbettercalendar.popups;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.example.bbettercalendar.R;

/**
 * Selector de recurrencia (spec tasks-recurrence). Extiende el popup original (4 opciones
 * exclusivas que auto-cerraban) para capturar también el intervalo "cada X días" (diaria) y
 * el bitmask de días de la semana (semanal), devolviendo un {@link RepetitionSpec} vía
 * {@link OnPopupListener#OnClosePopup(int, Object)}. Ya no auto-cierra: hay un botón "Hecho".
 */
public class RepetitionPopup extends DialogFragment implements View.OnClickListener {

    /** @deprecated use {@link RepetitionOptions#NONE}. Kept for binary compatibility. */
    @Deprecated public static final int REPETITION_NONE = RepetitionOptions.NONE;
    /** @deprecated use {@link RepetitionOptions#DAILY}. */
    @Deprecated public static final int REPETITION_DAILY = RepetitionOptions.DAILY;
    /** @deprecated use {@link RepetitionOptions#WEEKLY}. */
    @Deprecated public static final int REPETITION_WEEKLY = RepetitionOptions.WEEKLY;
    /** @deprecated use {@link RepetitionOptions#MONTHLY}. */
    @Deprecated public static final int REPETITION_MONTHLY = RepetitionOptions.MONTHLY;

    private static final int MIN_INTERVAL = 1;
    private static final int MAX_INTERVAL = 30;

    private OnPopupListener listener;

    // Estado inicial (seedeable por el caller antes de show()).
    private int selected = RepetitionOptions.NONE;
    private int interval = 1;
    private int daysMask = 0;

    private ToggleButton[] baseToggles = new ToggleButton[4];
    private ToggleButton[] weekdayToggles = new ToggleButton[7];
    private View dailyOptions;
    private View weeklyOptions;
    private android.widget.TextView intervalValue;
    private boolean updatingToggles = false;

    /** Semilla el popup con el spec actual (para reabrir con la selección previa). */
    public void setInitialSpec(RepetitionSpec spec) {
        if (spec == null) return;
        this.selected = spec.repetition;
        this.interval = Math.max(MIN_INTERVAL, spec.interval);
        this.daysMask = spec.daysMask;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.RoundedDialog);
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.popup_repetition, null);
        builder.setView(view);

        baseToggles[0] = view.findViewById(R.id.repetition_popup_toggle_none);
        baseToggles[1] = view.findViewById(R.id.repetition_popup_toggle_daily);
        baseToggles[2] = view.findViewById(R.id.repetition_popup_toggle_weekly);
        baseToggles[3] = view.findViewById(R.id.repetition_popup_toggle_monthly);

        int[] rowIds = {
                R.id.repetition_popup_row_none,
                R.id.repetition_popup_row_daily,
                R.id.repetition_popup_row_weekly,
                R.id.repetition_popup_row_monthly
        };
        for (int i = 0; i < rowIds.length; i++) {
            final int idx = i;
            view.findViewById(rowIds[i]).setOnClickListener(v -> selectBase(idx));
        }

        dailyOptions = view.findViewById(R.id.repetition_popup_daily_options);
        weeklyOptions = view.findViewById(R.id.repetition_popup_weekly_options);
        intervalValue = view.findViewById(R.id.repetition_interval_value);
        view.findViewById(R.id.repetition_interval_minus).setOnClickListener(this);
        view.findViewById(R.id.repetition_interval_plus).setOnClickListener(this);

        int[] weekdayIds = {
                R.id.repetition_weekday_0, R.id.repetition_weekday_1, R.id.repetition_weekday_2,
                R.id.repetition_weekday_3, R.id.repetition_weekday_4, R.id.repetition_weekday_5,
                R.id.repetition_weekday_6
        };
        String[] initials = getResources().getStringArray(R.array.weekday_initials);
        for (int i = 0; i < weekdayIds.length; i++) {
            ToggleButton tb = view.findViewById(weekdayIds[i]);
            tb.setTextOn(initials[i]);
            tb.setTextOff(initials[i]);
            tb.setText(initials[i]);
            final int bit = i;
            tb.setOnClickListener(v -> toggleWeekday(bit, ((ToggleButton) v).isChecked()));
            weekdayToggles[i] = tb;
        }

        view.findViewById(R.id.repetition_popup_confirm).setOnClickListener(v -> dismiss());

        renderSelection();
        renderInterval();
        renderWeekdays();
        return builder.create();
    }

    private void selectBase(int idx) {
        selected = idx;
        renderSelection();
    }

    private void renderSelection() {
        updatingToggles = true;
        for (int i = 0; i < baseToggles.length; i++) {
            baseToggles[i].setChecked(i == selected);
        }
        updatingToggles = false;
        dailyOptions.setVisibility(selected == RepetitionOptions.DAILY ? View.VISIBLE : View.GONE);
        weeklyOptions.setVisibility(selected == RepetitionOptions.WEEKLY ? View.VISIBLE : View.GONE);
    }

    private void toggleWeekday(int bit, boolean checked) {
        if (checked) {
            daysMask |= (1 << bit);
        } else {
            daysMask &= ~(1 << bit);
        }
    }

    private void renderWeekdays() {
        for (int i = 0; i < weekdayToggles.length; i++) {
            weekdayToggles[i].setChecked((daysMask & (1 << i)) != 0);
        }
    }

    private void renderInterval() {
        if (intervalValue == null) return;
        intervalValue.setText(getResources().getQuantityString(
                R.plurals.repetition_interval_days, interval, interval));
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.repetition_interval_minus) {
            interval = Math.max(MIN_INTERVAL, interval - 1);
            renderInterval();
        } else if (id == R.id.repetition_interval_plus) {
            interval = Math.min(MAX_INTERVAL, interval + 1);
            renderInterval();
        }
    }

    public void setOnPopupListener(OnPopupListener listener) {
        this.listener = listener;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (listener != null) {
            listener.OnClosePopup(PopupHelper.REPETITION_POPUP,
                    new RepetitionSpec(selected, interval, daysMask));
        }
    }
}
