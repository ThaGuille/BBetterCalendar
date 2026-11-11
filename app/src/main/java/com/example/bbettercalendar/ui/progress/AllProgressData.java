package com.example.bbettercalendar.ui.progress;

import androidx.annotation.Nullable;

import com.example.bbettercalendar.stats.HourHeatmapCalculator;
import com.example.bbettercalendar.stats.ProjectMinutes;
import com.example.bbettercalendar.stats.Records;
import com.example.bbettercalendar.stats.SessionStats;
import com.example.bbettercalendar.stats.UsageInsights;

import java.util.List;

/**
 * Datos ya calculados de «Todo tu progreso» (mejora-visual u8). Plano y sin Android: lo construye
 * {@link AllProgressViewModel} en el executor y {@link AllProgressCards} lo convierte en datos de cada
 * grafico y en frases. Las tarjetas 1, 5, 6 y 8 siguen al periodo; las 2, 3, 4 y 7 son fijas
 * (ultimos 90 dias, 12 semanas, 26 semanas) y se calculan una sola vez.
 */
final class AllProgressData {

    // ---- siguen al periodo ----
    TimeRange range;
    PeriodBuckets buckets;

    // 1 · Enfoque por dia (minutos por columna, periodo y anterior alineados)
    float[] focusCurrent;
    float[] focusPrevious;
    /** Columna de hoy (o de la hora actual en el dia de hoy), o -1 si el periodo no es el actual. */
    int focusHighlight;
    /** Columnas ya empezadas (media y comparacion "hasta hoy"); count si el periodo ya acabo. */
    int focusElapsed;

    // 5 · Sesiones terminadas
    float[][] sessionBars;                // [columna][completadas, abandonadas]
    SessionStats.Result sessionsCurrent;
    SessionStats.Result sessionsPrevious;

    // 6 · Tiempo por proyecto (mas a menos)
    List<ProjectMinutes> projects;

    // 8 · Enfoque frente a movil
    boolean phoneAvailable;               // acceso de uso concedido y >= 3 dias con datos
    float[] mirrorFocus;
    float[] mirrorPhone;
    String[] mirrorLabels;
    @Nullable UsageInsights.FocusVsPhone focusVsPhone;

    // ---- fijas ----
    HourHeatmapCalculator.Result hours;   // 2 · ultimos 90 dias
    float[] trendCurrentHours;            // 3 · 12 semanas completas
    float[] trendPreviousHours;
    String[] trendLabels;
    float[] daily26Weeks;                 // 4 · minutos por dia, 26 * 7 dias que terminan hoy
    SessionStats.Result sessions90;       // 7 · ultimos 90 dias
    Records.Result records;
    int longestRunDays;                   // 4 · racha mas larga de dias activos en las 26 semanas
}
