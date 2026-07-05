package com.example.bbettercalendar.ui.progress;

// Fila de la lista de uso: sólo datos planos (paquete, etiqueta, ms en primer plano del rango y
// límite diario en minutos — Phase 3, 0 = sin límite). El icono lo resuelve el adapter vía
// PackageManager — el ViewModel no retiene Drawables.
public class AppUsageRow {
    public final String packageName;
    public final String label;
    public final long foregroundMillis;
    public final int dailyLimitMinutes;
    // El límite es diario: el adapter sólo debe pintar "usado/límite" cuando el rango
    // seleccionado es un único día (foregroundMillis de una semana/mes no es comparable contra
    // un límite diario). dailyLimitMinutes se conserva siempre para precargar AppLimitDialog.
    public final boolean showLimitProgress;
    // Phase 4a: si el usuario activó "hacer cumplir el límite" para esta app (toggle 🚫 de la fila).
    public final boolean enforceAtLimit;

    public AppUsageRow(String packageName, String label, long foregroundMillis, int dailyLimitMinutes,
                        boolean showLimitProgress, boolean enforceAtLimit) {
        this.packageName = packageName;
        this.label = label;
        this.foregroundMillis = foregroundMillis;
        this.dailyLimitMinutes = dailyLimitMinutes;
        this.showLimitProgress = showLimitProgress;
        this.enforceAtLimit = enforceAtLimit;
    }
}
