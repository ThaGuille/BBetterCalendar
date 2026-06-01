package com.example.bbettercalendar.stats;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

// Una fila por día: el histórico que alimenta los gráficos "concent"/"fails" de Progress.
// Se rellena en el reset diario, antes de que Stats ponga a 0 los contadores de "hoy".
@Entity(tableName = "daily_stat")
public class DailyStat {
    @PrimaryKey
    @NonNull
    public String day;            // ISO "2026-06-01" (LocalDate.toString())
    public int focusMinutes;      // minutos de concentración ese día
    public int fails;             // fallos del timer ese día
    public int tasksDone;
    public int phoneUsageMinutes; // placeholder para Phase 2 (UsageStatsManager), por defecto 0

    public DailyStat() {
        this.day = "";
    }
}
