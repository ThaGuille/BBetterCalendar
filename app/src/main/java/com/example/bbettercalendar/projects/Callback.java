package com.example.bbettercalendar.projects;

/** Resultado de una escritura de los repositorios de Proyectos; siempre llega en el hilo principal. */
public interface Callback<T> {
    void onResult(T value);
}
