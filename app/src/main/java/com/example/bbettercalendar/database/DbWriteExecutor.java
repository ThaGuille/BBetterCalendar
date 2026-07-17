package com.example.bbettercalendar.database;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Qualifier;

// Qualifier para el ExecutorService de escritura de un único hilo (spec di-threading-consolidation).
// Provisto ya en T1 para que el tipo exista en el grafo de Hilt, pero sin consumidores todavía —
// migrar las escrituras de DB a este executor (orden global de escritura) es trabajo de T2, que
// necesita su propia verificación en el emulador (ver roadmap).
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface DbWriteExecutor {
}
