package com.example.bbettercalendar.blocking;

import android.content.Context;
import android.content.SharedPreferences;

// Interruptor maestro de "hacer cumplir los límites" (Phase 4a). Desactivarlo apaga TODO el
// bloqueo sin tocar los grants del OS (el usuario mantiene el acceso de Accesibilidad concedido
// pero el servicio no cubre ninguna app). Vive en SharedPreferences porque no es por-app y no
// justifica una columna nueva (regla #6). Por defecto ON: si el usuario activó enforce en una app
// es porque quiere que se cumpla.
public final class BlockingSettings {

    private static final String PREFS_NAME = "blocking_settings";
    private static final String KEY_ENFORCEMENT_ENABLED = "enforcement_enabled";

    private BlockingSettings() { }

    public static boolean isEnforcementEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENFORCEMENT_ENABLED, true);
    }

    public static void setEnforcementEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENFORCEMENT_ENABLED, enabled).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
