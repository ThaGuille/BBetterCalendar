package com.example.bbettercalendar.usage;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

// Helper para el permiso de acceso especial PACKAGE_USAGE_STATS. No se concede por
// requestPermissions: se comprueba con AppOpsManager y se envía al usuario a
// Ajustes -> Acceso a uso. No hay callback al volver -> hay que re-comprobar en onResume().
public final class UsageAccess {

    private UsageAccess() { }

    // true si el usuario ya concedió "Acceso a uso" para nuestro paquete.
    public static boolean hasUsageAccess(Context context) {
        AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) return false;
        int mode;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), context.getPackageName());
        } else {
            mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), context.getPackageName());
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    // Intent a la pantalla de sistema "Acceso a uso". Es una lista global de apps; Android no
    // permite enlazar directamente a nuestra fila, así que abrimos la lista completa.
    public static Intent usageAccessSettingsIntent() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
}
