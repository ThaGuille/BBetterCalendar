package com.example.bbettercalendar.stats;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

// Una fila por app que el usuario eligió seguir (picker de Phase 2). El histórico de uso se mide
// para TODAS las apps, pero la lista de Progress sólo muestra las que tienen tracked = true.
// Los campos de límite/bloqueo se incluyen ya pero NO se usan hasta Phases 3-4 (placeholder).
@Entity(tableName = "app_rule")
public class AppRule {
    @PrimaryKey
    @NonNull
    public String packageName;
    public boolean tracked;            // el usuario la añadió a la lista
    public int dailyLimitMinutes;      // Phase 3 (límite diario); 0 = sin límite
    public int warnBeforeMinutes;      // Phase 3 (aviso previo); por defecto 5
    public boolean instantBlock;       // Phase 4 (bloqueo inmediato)
    public boolean blockedToday;       // Phase 4 (estado de bloqueo del día)
    public int blockStyle;             // Phase 4 (estilo de la pantalla de bloqueo)

    public AppRule() {
        this.packageName = "";
    }

    @Ignore
    public AppRule(@NonNull String packageName, boolean tracked) {
        this.packageName = packageName;
        this.tracked = tracked;
        this.warnBeforeMinutes = 5;
    }
}
