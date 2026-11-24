package com.example.bbettercalendar.projects;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.database.AppDatabase;

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

    // Binding por defecto: no hace nada. La parte del aviso de empezar lo sustituye por la alarma real.
    @Provides
    @Singleton
    public static TaskStartAlarm provideTaskStartAlarm() {
        return new TaskStartAlarm() {
            @Override
            public void schedule(CalendarEntry entry) {
            }

            @Override
            public void cancel(CalendarEntry entry) {
            }
        };
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
