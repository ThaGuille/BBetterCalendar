package com.example.bbettercalendar.database;

import android.content.Context;

import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.configuration.ConfigurationDAO;
import com.example.bbettercalendar.projects.ProjectDAO;
import com.example.bbettercalendar.stats.AppRuleDAO;
import com.example.bbettercalendar.stats.ConsentRecordDAO;
import com.example.bbettercalendar.stats.DailyStatDAO;
import com.example.bbettercalendar.stats.FocusEventDAO;
import com.example.bbettercalendar.stats.StatsDAO;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

// Único módulo con @Provides para AppDatabase + sus 8 DAOs (spec di-threading-consolidation).
// Absorbe ConfigurationDatabaseModule (sólo daba AppDatabase) y los providers de DAO sueltos que
// vivían en ConfigurationModule/NotificationsModule (3 de 8) -- ahora un único lugar que mirar.
// AppDatabase.getDatabase() es package-private: este módulo (mismo paquete) es la única puerta.
@Module
@InstallIn(SingletonComponent.class)
public class DatabaseModule {

    @Provides
    @Singleton
    public static AppDatabase provideDatabase(@ApplicationContext Context context) {
        return AppDatabase.getDatabase(context);
    }

    @Provides
    @Singleton
    public static CalendarEntryDAO provideCalendarEntryDAO(AppDatabase database) {
        return database.eventDao();
    }

    @Provides
    @Singleton
    public static StatsDAO provideStatsDAO(AppDatabase database) {
        return database.statsDao();
    }

    @Provides
    @Singleton
    public static ConfigurationDAO provideConfigurationDAO(AppDatabase database) {
        return database.configurationDao();
    }

    @Provides
    @Singleton
    public static DailyStatDAO provideDailyStatDAO(AppDatabase database) {
        return database.dailyStatDao();
    }

    @Provides
    @Singleton
    public static FocusEventDAO provideFocusEventDAO(AppDatabase database) {
        return database.focusEventDao();
    }

    @Provides
    @Singleton
    public static AppRuleDAO provideAppRuleDAO(AppDatabase database) {
        return database.appRuleDao();
    }

    @Provides
    @Singleton
    public static ConsentRecordDAO provideConsentRecordDAO(AppDatabase database) {
        return database.consentRecordDao();
    }

    @Provides
    @Singleton
    public static ProjectDAO provideProjectDAO(AppDatabase database) {
        return database.projectDao();
    }
}
