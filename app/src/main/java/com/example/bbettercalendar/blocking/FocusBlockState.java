package com.example.bbettercalendar.blocking;

import android.content.Context;
import android.content.SharedPreferences;

// Interruptor "modo bloqueo total" del pomodoro: mientras está activo, BlockDecisionEngine cubre
// CUALQUIER app que no sea la nuestra ni el launcher (ver focusExemptPackages), no sólo las que
// tienen un AppRule con límite. Vive en SharedPreferences, igual que BlockingSettings (no es
// por-app, no justifica columna nueva, regla #6). El ÚNICO escritor es HomeFragment: engancha este
// flag a updateTimerControls(), así que queda activo exactamente mientras hay un ciclo de
// concentración corriendo (TIMER_RUNNING) Y el usuario lo armó con el botón 🚫.
//
// Riesgo de "kill-trap": si el proceso muere con esto en true, no queda ningún timer vivo que lo
// vuelva a poner en false -> bloquearía el teléfono entero indefinidamente. Por eso
// BlockerAccessibilityService.onServiceConnected() lo limpia SIEMPRE al (re)conectar: el servicio
// comparte proceso con la app (sin android:process), así que un kill se lleva por delante también
// al servicio, y el reconecto que el sistema fuerza es el punto de auto-sanación.
public final class FocusBlockState {

    private static final String PREFS_NAME = "focus_block_state";
    private static final String KEY_ACTIVE = "focus_block_active";

    private FocusBlockState() { }

    public static boolean isActive(Context context) {
        return prefs(context).getBoolean(KEY_ACTIVE, false);
    }

    public static void setActive(Context context, boolean active) {
        prefs(context).edit().putBoolean(KEY_ACTIVE, active).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
