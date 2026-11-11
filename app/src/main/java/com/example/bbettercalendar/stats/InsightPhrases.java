package com.example.bbettercalendar.stats;

import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.R;

/**
 * Frases de lectura de las estadísticas (mejora-visual u5), EN/ES desde {@code strings_insights.xml}.
 * Sin Android más allá de {@link Resources}, así que se prueba con Resources simulado.
 *
 * <p>Regla invariante: NUNCA una frase inventada. Cada método devuelve {@code ""} cuando el dato no
 * alcanza el mínimo que se documenta en él; quien pinta decide ocultar la fila. Los umbrales
 * están aquí, no dispersos por las pantallas.
 */
public final class InsightPhrases {

    /** Días con sesión mínimos para decir "rindes más ...". */
    public static final int MIN_ACTIVE_DAYS = 7;
    /** Sesiones (completadas + fallos) mínimas para hablar de tasa; completadas mínimas para la duración habitual. */
    public static final int MIN_SESSIONS = 5;
    /** Semanas seguidas por encima de la media a partir de las que se menciona. */
    public static final int MIN_WEEKS_ABOVE = 2;
    /** Diferencia mínima (puntos / minutos) para mencionar un cambio. */
    public static final int MIN_DELTA = 1;
    /** Minutos mínimos de ahorro de móvil para mencionarlo. */
    public static final int MIN_PHONE_SAVING_MIN = 5;

    private final Resources res;

    public InsightPhrases(@NonNull Resources res) {
        this.res = res;
    }

    /** "Rindes más los martes de 9 a 11". Vacío con menos de {@link #MIN_ACTIVE_DAYS} días activos. */
    @NonNull
    public String bestSlot(@NonNull HourHeatmapCalculator.Result heatmap) {
        HourHeatmapCalculator.Slot slot = heatmap.bestSlot();
        if (slot == null || heatmap.activeDays < MIN_ACTIVE_DAYS) {
            return "";
        }
        String[] days = res.getStringArray(R.array.insight_weekdays_plural);
        String day = days[slot.dayOfWeek.getValue() - 1];
        return res.getString(R.string.insight_best_slot, day, slot.startHour, slot.endHour);
    }

    /**
     * "Completas el 87 % de tus sesiones" (+ ", 3 puntos más que antes" si hay periodo previo y la
     * diferencia es de al menos {@link #MIN_DELTA} punto). Vacío con menos de {@link #MIN_SESSIONS} sesiones.
     *
     * @param previous periodo anterior (null o sin tasa = sin comparación)
     */
    @NonNull
    public String completion(@NonNull SessionStats.Result current, @Nullable SessionStats.Result previous) {
        if (current.focusCount + current.failCount < MIN_SESSIONS) {
            return "";
        }
        int pct = (int) Math.round(current.completionRate() * 100);
        if (previous != null && SessionStats.hasDelta(current, previous)) {
            int delta = (int) Math.round(SessionStats.deltaPoints(current, previous));
            if (delta >= MIN_DELTA) {
                return res.getString(R.string.insight_completion_up, pct, delta);
            }
            if (delta <= -MIN_DELTA) {
                return res.getString(R.string.insight_completion_down, pct, -delta);
            }
        }
        return res.getString(R.string.insight_completion, pct);
    }

    /** "Tu sesión habitual dura 25 minutos". Vacío con menos de {@link #MIN_SESSIONS} sesiones completadas. */
    @NonNull
    public String modalDuration(@NonNull SessionStats.Result stats) {
        if (stats.focusCount < MIN_SESSIONS || stats.modalDurationMin <= 0) {
            return "";
        }
        return res.getQuantityString(R.plurals.insight_modal_duration, stats.modalDurationMin,
                stats.modalDurationMin);
    }

    /** "Tu sesión más larga: 50 minutos". Vacío sin sesiones. */
    @NonNull
    public String longestSession(@NonNull Records.Result records) {
        if (records.isEmpty() || records.longestSessionMin <= 0) {
            return "";
        }
        return res.getQuantityString(R.plurals.insight_longest_session, records.longestSessionMin,
                records.longestSessionMin);
    }

    /** "3 semanas seguidas por encima de tu media". Vacío con menos de {@link #MIN_WEEKS_ABOVE} semanas. */
    @NonNull
    public String weeksAboveAverage(@NonNull Records.Result records) {
        if (records.weeksAboveAverage < MIN_WEEKS_ABOVE) {
            return "";
        }
        return res.getQuantityString(R.plurals.insight_weeks_above_average, records.weeksAboveAverage,
                records.weeksAboveAverage);
    }

    /** "Tu hora punta son las 10". Vacío sin hora punta. */
    @NonNull
    public String peakHour(@NonNull Records.Result records) {
        if (records.isEmpty() || records.peakHour < 0) {
            return "";
        }
        return res.getString(R.string.insight_peak_hour, records.peakHour);
    }

    /**
     * "X está entre tus apps más usadas y no tiene límite". Vacío si la lista de sugeridas está vacía.
     *
     * @param label nombre visible de la app (el llamador lo resuelve desde el PackageManager)
     */
    @NonNull
    public String suggestedLimit(@Nullable UsageInsights.AppAverage suggested, @NonNull String label) {
        if (suggested == null || label.isEmpty()) {
            return "";
        }
        return res.getString(R.string.insight_suggested_limit, label,
                (int) Math.round(suggested.averageMinutes));
    }

    /**
     * "Desde el límite, X pasó de 70 a 45 min al día". Vacío sin comparación o si no mejoró.
     */
    @NonNull
    public String beforeAfter(@Nullable UsageInsights.BeforeAfter result, @NonNull String label) {
        if (result == null || !result.improved() || label.isEmpty()) {
            return "";
        }
        return res.getString(R.string.insight_before_after, label,
                (int) Math.round(result.beforeAverage), (int) Math.round(result.afterAverage));
    }

    /** "Has recuperado 200 minutos" (minutos totales); vacío si no hay nada recuperado. */
    @NonNull
    public String recovered(@Nullable UsageInsights.BeforeAfter result) {
        if (result == null || result.recoveredMinutes() <= 0) {
            return "";
        }
        return res.getQuantityString(R.plurals.insight_recovered, result.recoveredMinutes(),
                result.recoveredMinutes());
    }

    /**
     * "Los días que te concentras usas el móvil 40 min menos". Vacío sin comparación (menos de 14 días)
     * o si el ahorro es menor de {@link #MIN_PHONE_SAVING_MIN} min (no se afirma lo contrario).
     */
    @NonNull
    public String focusVsPhone(@Nullable UsageInsights.FocusVsPhone result) {
        if (result == null) {
            return "";
        }
        int saved = (int) Math.round(result.savedMinutes());
        if (saved < MIN_PHONE_SAVING_MIN) {
            return "";
        }
        return res.getQuantityString(R.plurals.insight_focus_vs_phone, saved, saved);
    }
}
