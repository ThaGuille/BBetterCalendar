package com.example.bbettercalendar.projects;

import androidx.room.Embedded;

// Proyección de ProjectDAO.observeAllWithCounts() (spec repository-layer-consolidation, T2):
// Project + sus recuentos done/total ya resueltos en la misma query, con subqueries correladas
// sobre calendarEntry -- así el InvalidationTracker de Room observa ambas tablas a la vez y el
// % ya no necesita un recálculo manual en onResume.
public class ProjectWithCounts {
    @Embedded
    public Project project;
    public int doneCount;
    public int totalCount;
}
