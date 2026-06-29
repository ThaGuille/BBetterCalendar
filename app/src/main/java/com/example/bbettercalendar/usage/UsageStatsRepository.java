package com.example.bbettercalendar.usage;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.HashMap;
import java.util.Map;

// Lee el uso de apps del sistema. NO usa getTotalTimeInForeground() (sobre-cuenta en varios OEM):
// suma el flujo de eventos MOVE_TO_FOREGROUND -> MOVE_TO_BACKGROUND por paquete, recortando cada
// intervalo a [begin, end] y cerrando al final los que sigan en primer plano. Las llamadas a
// queryEvents devuelven vacío con el dispositivo bloqueado -> el llamador las ejecuta en primer
// plano y siempre fuera del hilo principal (lo invoca el executor del ViewModel, regla #3).
public final class UsageStatsRepository {

    private final Context appContext;
    private final PackageManager pm;
    private final String ownPackage;

    public UsageStatsRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.pm = appContext.getPackageManager();
        this.ownPackage = appContext.getPackageName();
    }

    // Milisegundos en primer plano por paquete dentro de [begin, end). Excluye nuestro propio
    // paquete. Mapa vacío si no hay acceso/eventos.
    public Map<String, Long> foregroundMillis(long begin, long end) {
        Map<String, Long> totals = new HashMap<>();
        if (end <= begin) return totals;

        UsageStatsManager usm =
                (UsageStatsManager) appContext.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return totals;

        UsageEvents events = usm.queryEvents(begin, end);
        if (events == null) return totals;

        // package -> instante del último MOVE_TO_FOREGROUND aún sin cerrar.
        Map<String, Long> openSince = new HashMap<>();
        UsageEvents.Event ev = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(ev);
            String pkg = ev.getPackageName();
            if (pkg == null || pkg.equals(ownPackage)) continue;

            int type = ev.getEventType();
            if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                openSince.put(pkg, ev.getTimeStamp());
            } else if (type == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                Long start = openSince.remove(pkg);
                if (start != null) {
                    addInterval(totals, pkg, start, ev.getTimeStamp(), begin, end);
                }
            }
        }
        // Intervalos abiertos al final de la ventana: se cierran en `end`.
        for (Map.Entry<String, Long> open : openSince.entrySet()) {
            addInterval(totals, open.getKey(), open.getValue(), end, begin, end);
        }
        return totals;
    }

    // Tiempo total de pantalla del rango: suma del uso por app, excluyendo el launcher por defecto
    // (la pantalla de inicio inflaría el total) y nuestro propio paquete (ya excluido arriba).
    public long totalScreenTime(long begin, long end) {
        Map<String, Long> totals = foregroundMillis(begin, end);
        String launcher = defaultLauncherPackage();
        long sum = 0L;
        for (Map.Entry<String, Long> e : totals.entrySet()) {
            if (launcher != null && launcher.equals(e.getKey())) continue;
            sum += e.getValue();
        }
        return sum;
    }

    // Etiqueta legible de un paquete; si la app ya no está instalada, devuelve el packageName.
    public String labelFor(String packageName) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(ai);
            return label != null ? label.toString() : packageName;
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    private static void addInterval(Map<String, Long> totals, String pkg,
                                    long start, long stop, long begin, long end) {
        long clampedStart = Math.max(start, begin);
        long clampedStop = Math.min(stop, end);
        if (clampedStop > clampedStart) {
            Long prev = totals.get(pkg);
            totals.put(pkg, (prev == null ? 0L : prev) + (clampedStop - clampedStart));
        }
    }

    private String defaultLauncherPackage() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = pm.resolveActivity(home, 0);
        return (info != null && info.activityInfo != null) ? info.activityInfo.packageName : null;
    }
}
