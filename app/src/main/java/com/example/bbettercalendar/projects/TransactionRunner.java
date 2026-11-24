package com.example.bbettercalendar.projects;

/**
 * Ejecuta un bloque de escrituras en una sola transaccion de la base de datos. En la app es
 * {@code AppDatabase.runInTransaction} (ver {@link ProjectsModule}); en los tests JVM, {@code Runnable::run}.
 */
public interface TransactionRunner {
    void run(Runnable block);
}
