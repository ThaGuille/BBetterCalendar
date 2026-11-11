package com.example.bbettercalendar.ui.screentime;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// Chip de "Siempre permitidas" (mejora-visual u10): paquete de FocusAllowlist con su nombre e icono,
// resueltos en el executor del ViewModel (PackageManager fuera del hilo principal, regla #3).
public final class AllowedApp {
    @NonNull public final String packageName;
    @NonNull public final String label;
    @Nullable public final Drawable icon;

    public AllowedApp(@NonNull String packageName, @NonNull String label, @Nullable Drawable icon) {
        this.packageName = packageName;
        this.label = label;
        this.icon = icon;
    }
}
