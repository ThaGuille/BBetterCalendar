package com.example.bbettercalendar.notifications;

import android.app.AlarmManager;
import android.content.Context;

import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.database.AppDatabase;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

import javax.inject.Singleton;

@Module
@InstallIn(SingletonComponent.class)
public class NotificationsModule {

    @Provides
    @Singleton
    public static AlarmManager provideAlarmManager(@ApplicationContext Context context) {
        return (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    @Provides
    @Singleton
    public static CalendarEntryDAO provideCalendarEntryDAO(AppDatabase database) {
        return database.eventDao();
    }
}
