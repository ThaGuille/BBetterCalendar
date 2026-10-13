package com.example.bbettercalendar.ui.screentime;

import java.time.LocalDate;

// Estado plano de "Detalle por app" (u20, html 10). El ViewModel lo publica; el Fragment dibuja.
public final class AppDetailState {

    /** Dias de la vista Week / Month. */
    public static final int WEEK_DAYS = 7;
    public static final int MONTH_DAYS = 30;

    public final boolean usageAccess;
    public final boolean month;
    public final boolean tracked;
    public final int limitMinutes;       // 0 = sin limite
    public final int warnBeforeMinutes;
    public final boolean enforceAtLimit;
    public final long todayMillis;
    public final long avgMillis;         // media diaria del periodo mostrado
    public final boolean hasPrevious;    // hay media del periodo anterior con la que comparar
    public final int deltaPercent;       // (media - media anterior) / media anterior, en %
    public final LocalDate firstDay;     // dia del primer valor de dayMillis
    public final long[] dayMillis;       // un valor por dia del periodo, el ultimo = hoy

    public AppDetailState(boolean usageAccess, boolean month, boolean tracked, int limitMinutes,
                          int warnBeforeMinutes, boolean enforceAtLimit, long todayMillis,
                          long avgMillis, boolean hasPrevious, int deltaPercent,
                          LocalDate firstDay, long[] dayMillis) {
        this.usageAccess = usageAccess;
        this.month = month;
        this.tracked = tracked;
        this.limitMinutes = limitMinutes;
        this.warnBeforeMinutes = warnBeforeMinutes;
        this.enforceAtLimit = enforceAtLimit;
        this.todayMillis = todayMillis;
        this.avgMillis = avgMillis;
        this.hasPrevious = hasPrevious;
        this.deltaPercent = deltaPercent;
        this.firstDay = firstDay;
        this.dayMillis = dayMillis;
    }

    public boolean hasLimit() {
        return limitMinutes > 0;
    }
}
