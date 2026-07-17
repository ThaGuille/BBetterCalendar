package com.example.bbettercalendar.database;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Qualifier;

// Qualifier para el ExecutorService compartido de propósito general (spec di-threading-consolidation).
// Sustituye los ~15 Executors.newFixedThreadPool/newSingleThreadExecutor ad-hoc (uno por ViewModel/
// diálogo/receiver, nunca cerrados al recrearse) por un único pool @Singleton provisto por
// ThreadingModule. T1 mapea 1:1 todo uso existente aquí (mismo o más paralelismo -> sin cambio de
// comportamiento); mover las escrituras a @DbWriteExecutor para orden global es T2.
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface IoExecutor {
}
