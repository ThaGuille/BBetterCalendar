package com.example.bbettercalendar.stats;

// Proyección de FocusEventDAO.getMinutesByProject() (spec project-deadlines-progress): minutos de
// concentración atribuidos, agrupados por proyecto y acotados a un rango de fechas.
//
// Lleva projectId ADEMÁS del nombre porque una sola query alimenta las dos superficies nuevas de
// Progress: la gráfica (nombre -> minutos) y la banda de proyectos (id -> minutos).
public class ProjectMinutes {
    public int projectId;
    public String projectName;
    public int minutes;
}
