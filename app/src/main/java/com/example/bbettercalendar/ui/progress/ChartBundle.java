package com.example.bbettercalendar.ui.progress;

// Datos ya agregados para los gráficos de Progress. Plano y sin tipos de MPAndroidChart:
// el ViewModel no depende de la librería de gráficos (la conversión a Entry/BarEntry vive
// en el adapter, en el hilo principal). Los arrays diarios van alineados con dayLabels.
public final class ChartBundle {

    public final String[] dayLabels;   // etiqueta corta por día del rango (eje X)
    public final int[] focusMinutes;   // "concent": minutos de concentración por día
    public final int[] fails;          // "fails": fallos del timer por día
    public final int[] focusByHour;    // 24 buckets: sesiones completadas por hora del día
    public final int[] failByHour;     // 24 buckets: fallos por hora del día

    public ChartBundle(String[] dayLabels, int[] focusMinutes, int[] fails,
                       int[] focusByHour, int[] failByHour) {
        this.dayLabels = dayLabels;
        this.focusMinutes = focusMinutes;
        this.fails = fails;
        this.focusByHour = focusByHour;
        this.failByHour = failByHour;
    }
}
