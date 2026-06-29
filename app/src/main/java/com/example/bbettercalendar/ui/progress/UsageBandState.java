package com.example.bbettercalendar.ui.progress;

// Estado de la banda de uso de apps (band 3) de la pantalla Progress.
//  LOCKED        -> sin "Acceso a uso": tarjeta con CTA a la divulgación -> Ajustes.
//  LOADING       -> leyendo el uso del rango en el executor.
//  EMPTY_NO_APPS -> con permiso pero el usuario no ha elegido apps: CTA "Add apps".
//  READY         -> lista de apps seguidas con su tiempo.
// Los gráficos (band 2) NUNCA dependen de este estado: siguen pintando sin permiso.
public enum UsageBandState {
    LOCKED,
    LOADING,
    EMPTY_NO_APPS,
    READY
}
