package com.example.bbettercalendar.configuration;

import java.util.Locale;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class ConfigurationModule {

    @Provides
    public static Configuration setConfiguration() {
        // Guillem: Aquí puedes cargar los ajustes desde un archivo, base de datos, etc.
        Locale locale = Locale.getDefault();
        String country = locale.getCountry();

        return new Configuration(country);
    }
}
