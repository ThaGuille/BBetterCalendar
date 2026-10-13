package com.example.bbettercalendar.ui.progress;

// Granularidad del navegador temporal de Progress. La UI rediseñada (u17) ofrece Week / Month / Year;
// DAY se conserva porque el carrusel heredado (hasta u18) sigue sabiendo pintar barras por hora.
public enum Granularity {
    DAY, WEEK, MONTH, YEAR
}
