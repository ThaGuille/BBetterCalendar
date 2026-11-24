package com.example.bbettercalendar.projects;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * rediseno-proyectos (DB v16): una etiqueta de tarea ({@link #KIND_TAG}) o un área de proyectos
 * ({@link #KIND_AREA}); comparten tabla porque las dos son nombre + icono (+ color las etiquetas).
 * Las tareas apuntan a una etiqueta con {@code CalendarEntry.tagId} y los proyectos a un área con
 * {@code Project.areaId}; 0 = ninguna en los dos casos.
 *
 * <p>{@link #iconKey}: clave de icono (pen, book, bag, phone, code, heart, home, bulb para
 * etiquetas; cap, book, code, plane, box, home, heart, image para áreas). {@link #colorKey}: clave
 * de color de etiqueta (primary, secondary, seafoam, muted); vacía en las áreas, que no tienen color.
 * Las 8 predefinidas están en {@link TagSeeds}.
 */
@Entity(tableName = "tag")
public class Tag {
    public static final int KIND_TAG = 0;
    public static final int KIND_AREA = 1;

    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name;
    public String iconKey;
    public String colorKey;
    public int kind;
    // Orden dentro de su tipo: 0 = primera.
    public int position;

    public Tag() {
    }

    public Tag(String name, String iconKey, String colorKey, int kind, int position) {
        this.name = name;
        this.iconKey = iconKey;
        this.colorKey = colorKey;
        this.kind = kind;
        this.position = position;
    }
}
