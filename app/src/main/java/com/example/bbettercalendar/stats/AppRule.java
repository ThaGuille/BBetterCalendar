package com.example.bbettercalendar.stats;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

// Una fila por app que el usuario eligió seguir (picker de Phase 2). El histórico de uso se mide
// para TODAS las apps, pero la lista de Progress sólo muestra las que tienen tracked = true.
@Entity(tableName = "app_rule")
public class AppRule {
    @PrimaryKey
    @NonNull
    public String packageName;
    public boolean tracked;            // el usuario la añadió a la lista
    public int dailyLimitMinutes;      // Phase 3 (límite diario); 0 = sin límite
    public int warnBeforeMinutes;      // Phase 3 (aviso previo); por defecto 5
    // Phase 4a: "hacer cumplir el límite" (cubrir la app al superar el límite diario). Reutiliza la
    // columna 'instantBlock' (el instant-block se descartó) para no bumpear el esquema (regla #6).
    @ColumnInfo(name = "instantBlock")
    public boolean enforceAtLimit;     // Phase 4a (cubrir al superar el límite)
    public boolean blockedToday;       // reservado (decisión en vivo lo hace redundante)
    public int blockStyle;             // reservado (un único estilo de bloqueo por ahora)

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
