package com.example.bbettercalendar.configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConfigurationManager {

    private static ConfigurationManager instance;
    private Configuration configuration;
    private final ConfigurationDAO configurationDAO;
    private ExecutorService executorService;

    private ConfigurationManager(ConfigurationDAO configurationDAO) {
        this.configurationDAO = configurationDAO;
        executorService = Executors.newFixedThreadPool(2);
        // Cargar la configuración inicialmente
        loadConfiguration();
    }

    public static synchronized ConfigurationManager getInstance(ConfigurationDAO configurationDAO) {
        if (instance == null) {
            instance = new ConfigurationManager(configurationDAO);
        }
        return instance;
    }

    private void loadConfiguration() {
        // Aquí deberías cargar la configuración de manera asincrónica si es necesario
        // Esta es una implementación simplificada
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                configuration = configurationDAO.getConfiguration();
            }
        });

    }

    public Configuration getConfiguration() {
        if (configuration == null) {
            configuration = configurationDAO.getConfiguration();
            if (configuration == null) {
                configuration = new Configuration();
                configurationDAO.insert(configuration);
            }
        }
        return configuration;
    }

    //todo añadir más métodos de update para componentes individuales de la configuración

    public void updateConfiguration(Configuration newConfiguration) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                configurationDAO.update(newConfiguration);
                configuration = newConfiguration;
            }
        });
    }

}
