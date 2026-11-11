package com.example.bbettercalendar.stats;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;

// mejora-visual u1 (DB v15): minutos de uso de UNA app en UN día civil local. Android solo conserva
// los eventos de uso unos pocos días; UsageSnapshotter vuelca aquí los días completos para que el
// histórico (Tiempo de pantalla, detalle de app, Todo tu progreso) sobreviva a esa ventana.
// Clave compuesta (day, packageName): un upsert por app y día es idempotente.
@Entity(tableName = "app_usage_daily", primaryKeys = {"day", "packageName"})
public class AppUsageDaily {
    /** Fecha civil local "YYYY-MM-DD" (LocalDate.toString()); ordena cronológicamente como texto. */
    @NonNull
    public String day;
    @NonNull
    public String packageName;
    /** Minutos enteros en primer plano ese día (floor de los milisegundos). */
    public int minutes;

    public AppUsageDaily() {
        this.day = "";
        this.packageName = "";
    }

    @Ignore
    public AppUsageDaily(@NonNull String day, @NonNull String packageName, int minutes) {
        this.day = day;
        this.packageName = packageName;
        this.minutes = minutes;
    }
}
