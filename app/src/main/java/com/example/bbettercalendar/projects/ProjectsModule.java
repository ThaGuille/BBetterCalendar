package com.example.bbettercalendar.projects;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.bbettercalendar.database.AppDatabase;
import com.example.bbettercalendar.notifications.task.TaskStartScheduler;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

/** DI de los repositorios de Proyectos (rediseno-proyectos). */
@Module
@InstallIn(SingletonComponent.class)
public class ProjectsModule {

    // El aviso de empezar de una tarea: la alarma real (rediseno-proyectos 3.8).
    @Provides
    @Singleton
    public static TaskStartAlarm provideTaskStartAlarm(TaskStartScheduler scheduler) {
        return scheduler;
    }

    @Provides
    @Singleton
    public static TransactionRunner provideTransactionRunner(AppDatabase database) {
        return database::runInTransaction;
    }

    // Nombres sembrados (seccion Tareas...) en el idioma del movil.
    @Provides
    @Singleton
    public static TagSeeds.Names provideSeedNames(@ApplicationContext Context context) {
        return context::getString;
    }

    @Provides
    @Singleton
    public static MainThread provideMainThread() {
        Handler handler = new Handler(Looper.getMainLooper());
        return handler::post;
    }
}
