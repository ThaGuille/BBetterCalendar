package com.example.bbettercalendar.configuration;

import android.content.Context;

import com.example.bbettercalendar.database.AppDatabase;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class ConfigurationDatabaseModule {

    @Provides
    @Singleton
    public static AppDatabase provideDatabase(@ApplicationContext Context context) {
        return AppDatabase.getDatabase(context);
    }

}
