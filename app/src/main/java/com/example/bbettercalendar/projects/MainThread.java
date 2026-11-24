package com.example.bbettercalendar.projects;

/**
 * Entrega un Runnable en el hilo principal. Frontera del sistema (Looper) que los repositorios de
 * Proyectos reciben por Hilt para devolver resultados ({@link Callback}); en los tests JVM es
 * {@code Runnable::run}.
 */
public interface MainThread {
    void post(Runnable runnable);
}
