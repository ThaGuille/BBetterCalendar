package com.example.bbettercalendar.ui.progress;

// Fila de la lista de uso: sólo datos planos (paquete, etiqueta y ms en primer plano del rango).
// El icono lo resuelve el adapter vía PackageManager — el ViewModel no retiene Drawables.
public class AppUsageRow {
    public final String packageName;
    public final String label;
    public final long foregroundMillis;

    public AppUsageRow(String packageName, String label, long foregroundMillis) {
        this.packageName = packageName;
        this.label = label;
        this.foregroundMillis = foregroundMillis;
    }
}
