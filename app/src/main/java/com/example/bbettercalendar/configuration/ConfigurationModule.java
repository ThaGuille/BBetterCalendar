package com.example.bbettercalendar.configuration;

import android.content.Context;

import com.example.bbettercalendar.database.AppDatabase;

import java.util.Locale;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class ConfigurationModule {

    /** Clase con Dagger Hilt para la inyección de dependencias.
     * Su propósito es proveer una instancia de ConfigurationManager que pueda ser inyectada donde sea necesaria en la aplicación**/

    //Aquí es donde debo cargar los ajustes desde una base de datos o cualquier otra fuente externa.

    @Provides
    @Singleton
    public static ConfigurationDAO provideConfigurationDAO(AppDatabase database) {
        return database.configurationDao();
    }

    @Provides
    @Singleton
    public static ConfigurationManager provideConfigurationManager(ConfigurationDAO configurationDAO) {
        return ConfigurationManager.getInstance(configurationDAO);
    }

}
