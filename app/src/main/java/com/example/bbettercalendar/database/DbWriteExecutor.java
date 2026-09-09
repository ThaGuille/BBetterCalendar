package com.example.bbettercalendar.database;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Qualifier;

// Qualifier para el ExecutorService de escritura de un único hilo (spec di-threading-consolidation).
// Provisto en T1 para que el tipo existiera en el grafo de Hilt; desde repository-layer-
// consolidation (T2) tiene consumidores reales -- todas las escrituras de DB en los ViewModels y
// en los nuevos repositorios pasan por aquí, para orden global de escritura.
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface DbWriteExecutor {
}
