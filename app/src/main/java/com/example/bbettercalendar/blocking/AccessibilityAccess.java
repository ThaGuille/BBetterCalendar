package com.example.bbettercalendar.blocking;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;

// Helper para el acceso de Accesibilidad (Phase 4a) — el permiso que habilita el servicio de
// bloqueo. Igual que Usage Access, NO se concede por requestPermissions: se comprueba leyendo
// Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES y se envía al usuario a Ajustes -> Accesibilidad.
// No hay callback al volver -> hay que re-comprobar en onResume() (mismo caveat que UsageAccess).
public final class AccessibilityAccess {

    private AccessibilityAccess() { }

    // true si NUESTRO BlockerAccessibilityService está activado por el usuario.
    public static boolean isEnabled(Context context) {
        ComponentName service = new ComponentName(context, BlockerAccessibilityService.class);
        String enabled = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;

        // La lista es ':'-separada de ComponentName aplanados. Cotejamos por igualdad de componente
        // (flattenToString / flattenToShortString) para no fallar por variaciones de formato.
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            ComponentName parsed = ComponentName.unflattenFromString(splitter.next());
            if (parsed != null && parsed.equals(service)) return true;
        }
        return false;
    }

    // Intent a "Ajustes -> Accesibilidad". Es la lista global de servicios; Android no permite
    // enlazar directamente a nuestra fila, así que abrimos la lista completa (como Usage Access).
    public static Intent accessibilitySettingsIntent() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
}
