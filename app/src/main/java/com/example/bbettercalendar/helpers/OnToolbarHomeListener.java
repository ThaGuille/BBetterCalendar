package com.example.bbettercalendar.helpers;

public interface OnToolbarHomeListener {

    //métodos a ejecutar en home para los elementos de la toolbar
    void onToolbarTimerClick();

    /** Tap en el contador de racha de la toolbar (spec focus-mode-and-streak). */
    void onToolbarStreakClick();
}
