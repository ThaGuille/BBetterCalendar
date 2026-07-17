package com.example.bbettercalendar.database;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

// Ejecutores compartidos vía Hilt (spec di-threading-consolidation). Antes cada ViewModel/diálogo/
// receiver creaba el suyo (Executors.newFixedThreadPool/newSingleThreadExecutor -- ~15 sitios,
// ninguno se cerraba nunca al recrearse la instancia que lo poseía). @IoExecutor cubre TODO uso
// existente 1:1 (mismo pool fijo-4 o más paralelismo que antes -> sin cambio de comportamiento).
// @DbWriteExecutor se deja provisto para T2 (orden global de escritura); sin consumidores en T1.
// Ninguno de los dos se cierra nunca (@Singleton, vive todo el proceso -- igual que AppDatabase).
@Module
@InstallIn(SingletonComponent.class)
public class ThreadingModule {

    @Provides
    @Singleton
    @IoExecutor
    public static ExecutorService provideIoExecutor() {
        return Executors.newFixedThreadPool(4);
    }

    @Provides
    @Singleton
    @DbWriteExecutor
    public static ExecutorService provideDbWriteExecutor() {
        return Executors.newSingleThreadExecutor();
    }
}
