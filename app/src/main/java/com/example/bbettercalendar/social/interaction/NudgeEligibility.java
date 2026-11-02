package com.example.bbettercalendar.social.interaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.social.stats.FriendStats;
import com.example.bbettercalendar.social.stats.WeekKey;

import java.time.LocalDate;

/**
 * ¿Se le ofrece un empujón a este amigo? (F5-u5; D-034, D-035). Es la misma regla que aplica el servidor
 * ({@code sendNudge}): el amigo cuenta como «ya ha estudiado hoy» si y solo si {@code minutesToday > 0} y su
 * {@code stats.dayKey} es mi {@code dayKey} o el de mañana ({@code dayKey + 1}): quien está en una zona horaria
 * por delante ya vive en mi mañana. Sin stats, con 0 minutos, de ayer ({@code dayKey - 1}), de pasado mañana o
 * de cualquier otro día, no cuenta y el empujón se ofrece. Puro: solo claves de día, nunca el reloj (quien
 * llama pasa mi fecha de hoy).
 */
public final class NudgeEligibility {

    private NudgeEligibility() {
    }

    /** true si el amigo ya estudió hoy (no se le ofrece empujón). */
    public static boolean studiedToday(@Nullable FriendStats stats, @NonNull LocalDate myToday) {
        if (stats == null || stats.minutesToday <= 0) {
            return false;
        }
        return stats.dayKey.equals(WeekKey.dayKey(myToday)) || stats.dayKey.equals(WeekKey.dayKey(myToday.plusDays(1)));
    }

    /** true si se le ofrece el empujón: no ha estudiado hoy. */
    public static boolean offered(@Nullable FriendStats stats, @NonNull LocalDate myToday) {
        return !studiedToday(stats, myToday);
    }
}
