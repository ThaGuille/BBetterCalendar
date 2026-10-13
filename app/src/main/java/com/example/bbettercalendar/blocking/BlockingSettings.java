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
    private static final String KEY_FOCUS_SHIELD = "focus_shield";

    private BlockingSettings() { }

    public static boolean isEnforcementEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENFORCEMENT_ENABLED, true);
    }

    public static void setEnforcementEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENFORCEMENT_ENABLED, enabled).apply();
    }

    // u19 "Focus shield" (Screen time): preferencia persistente de que cada sesion de concentracion
    // ARRANCA con el modo bloqueo total armado (FocusSessionViewModel la lee al crearse). No es el
    // estado vivo (eso es FocusBlockState) ni toca el interruptor maestro de arriba. Por defecto OFF.
    public static boolean isFocusShieldEnabled(Context context) {
        return prefs(context).getBoolean(KEY_FOCUS_SHIELD, false);
    }

    public static void setFocusShieldEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_FOCUS_SHIELD, enabled).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
