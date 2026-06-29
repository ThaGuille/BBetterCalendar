package com.example.bbettercalendar.ui.progress.apppicker;

import android.graphics.drawable.Drawable;

// Fila del picker de apps: paquete + etiqueta + icono y el estado de selección. wasTracked guarda
// si la app ya estaba seguida al abrir, para no reescribir filas innecesariamente al guardar.
public class AppPickItem {
    public final String packageName;
    public final String label;
    public final Drawable icon;
    public boolean checked;
    public final boolean wasTracked;

    public AppPickItem(String packageName, String label, Drawable icon, boolean checked, boolean wasTracked) {
        this.packageName = packageName;
        this.label = label;
        this.icon = icon;
        this.checked = checked;
        this.wasTracked = wasTracked;
    }
}
