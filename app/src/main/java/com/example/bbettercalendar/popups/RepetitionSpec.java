package com.example.bbettercalendar.popups;

import android.content.Context;

import com.example.bbettercalendar.R;

import java.io.Serializable;
import java.util.Calendar;

/**
 * Valor de recurrencia elegido en {@link RepetitionPopup} (spec tasks-recurrence). Encapsula el
 * ordinal base ({@link RepetitionOptions}) más los detalles que ese ordinal por sí solo no lleva:
 * el intervalo "cada X días" (diaria) y el bitmask de días de la semana (semanal).
 *
 * Se pasa como {@code Object} a través de {@link OnPopupListener#OnClosePopup(int, Object)}.
 */
public final class RepetitionSpec implements Serializable {

    /** Bit 0 = lunes … bit 6 = domingo. */
    public static final int MASK_ALL_DAYS = 0b1111111;

    public final int repetition;
    public final int interval;   // "cada X días" cuando repetition == DAILY (>= 1)
    public final int daysMask;   // bitmask Lun..Dom cuando repetition == WEEKLY

    public RepetitionSpec(int repetition, int interval, int daysMask) {
        this.repetition = repetition;
        this.interval = Math.max(1, interval);
        this.daysMask = daysMask;
    }

    public static RepetitionSpec none() {
        return new RepetitionSpec(RepetitionOptions.NONE, 1, 0);
    }

    public boolean repeats() {
        return repetition != RepetitionOptions.NONE;
    }

    /** Índice de bit (0 = lunes) para el día de la semana de un Calendar. */
    public static int weekdayBit(Calendar cal) {
        // Calendar.DAY_OF_WEEK: SUNDAY=1 … SATURDAY=7 -> lunes=0 … domingo=6.
        return (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;
    }

    public boolean matchesWeekday(Calendar cal) {
        if (daysMask == 0) {
            return true; // sin días marcados: la ocurrencia cae en el día de la ancla (lo decide el caller)
        }
        return (daysMask & (1 << weekdayBit(cal))) != 0;
    }

    /** Etiqueta corta para la fila de "Repetir" (quick-add + create task). */
    public String describe(Context context) {
        switch (repetition) {
            case RepetitionOptions.DAILY:
                if (interval <= 1) {
                    return context.getString(R.string.repetition_option_daily);
                }
                return context.getString(R.string.repetition_summary_every_n_days, interval);
            case RepetitionOptions.WEEKLY:
                if (daysMask == 0) {
                    return context.getString(R.string.repetition_option_weekly);
                }
                return context.getString(R.string.repetition_summary_weekly_on,
                        weekdayInitials(context));
            case RepetitionOptions.MONTHLY:
                return context.getString(R.string.repetition_option_monthly);
            default:
                return context.getString(R.string.repetition_option_none);
        }
    }

    private String weekdayInitials(Context context) {
        String[] initials = context.getResources().getStringArray(R.array.weekday_initials);
        StringBuilder sb = new StringBuilder();
        for (int bit = 0; bit < 7; bit++) {
            if ((daysMask & (1 << bit)) != 0) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(initials[bit]);
            }
        }
        return sb.toString();
    }
}
