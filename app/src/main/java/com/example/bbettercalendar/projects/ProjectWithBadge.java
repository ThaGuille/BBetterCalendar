package com.example.bbettercalendar.projects;

import androidx.room.Embedded;

/**
 * Proyeccion de ProjectDAO.observeActiveWithBadge(): un proyecto ACTIVE con sus recuentos
 * hechas/total (sin plantillas ni retiradas) y lo que necesita {@code TodayBadge}: cuantas
 * pendientes vencen hoy o antes y cuantas de ellas vencen antes de hoy.
 */
public class ProjectWithBadge {
    @Embedded
    public Project project;
    public int doneCount;
    public int totalCount;
    // Pendientes con fecha que vencen hoy o antes (TodayBadge.count).
    public int dueTodayCount;
    // De ellas, las que vencen antes de hoy (TodayBadge.late = lateCount > 0).
    public int lateCount;
}
