package com.example.bbettercalendar.projects;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

// Un proyecto agrupa CalendarEntry (type task) via projectId — no hay tabla de items propia
// (decisión de datos del roadmap). Retención (spec projects-mvp decisión #2): completar un
// proyecto es un cambio de status, nunca un borrado — createdAtMillis/completedAtMillis quedan
// para las gráficas de Phase 5.
@Entity(tableName = "project")
public class Project {
    public static final int STATUS_ACTIVE = 0;
    public static final int STATUS_COMPLETED = 1;
    public static final int STATUS_ARCHIVED = 2;

    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name;
    public String notes;
    public int status;
    public long softDeadlineMillis;  // 0 = sin deadline; absoluto, nunca se sobreescribe
    public int colorIndex;           // chip de acento en la lista, cicla la paleta bb_*
    public long createdAtMillis;
    public long completedAtMillis;

    public Project() {
    }
}
