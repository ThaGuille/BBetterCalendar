package com.example.bbettercalendar.stats;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

// Una fila por sesión completada / fallo. Lleva timestamp real, así que alimenta el gráfico
// por horas ("cuándo me concentro / fallo") y, agregando por día, también las series diarias.
@Entity(tableName = "focus_event")
public class FocusEvent {
    public static final int TYPE_FOCUS = 0;  // sesión de concentración completada
    public static final int TYPE_FAIL = 1;   // el timer falló
    public static final int TYPE_TASK = 2;   // reservado; aún no se emite (ver plan)

    @PrimaryKey(autoGenerate = true)
    public int id;
    public long timestamp;   // System.currentTimeMillis() en el momento del evento
    public int type;
    public int durationMin;  // minutos para sesiones de concentración, 0 para fallos
    // Tarea/item a la que se atribuye esta sesión (spec focus-attribution): CalendarEntry.id.
    // 0 = sesión genérica sin vincular (comportamiento previo). La atribución se expresa aquí,
    // no con un type nuevo: una sesión vinculada sigue siendo un TYPE_FOCUS real.
    public int entryId;

    public FocusEvent() {
    }
}
