package com.example.bbettercalendar.social.interaction;

import androidx.annotation.NonNull;

import java.util.Set;

/**
 * Dónde guarda {@link SentTodayLog} sus entradas (F5-u5): un conjunto de cadenas en un fichero propio de
 * preferencias del dispositivo ({@link PrefsSentLogStore}). Interfaz propia para que sus tests usen uno en
 * memoria. Se llama siempre desde el serial del repositorio (E/S fuera del hilo principal).
 */
public interface SentLogStore {

    @NonNull
    Set<String> read();

    void write(@NonNull Set<String> entries);

    /** Borra todo, incluido el fichero. */
    void clear();
}
