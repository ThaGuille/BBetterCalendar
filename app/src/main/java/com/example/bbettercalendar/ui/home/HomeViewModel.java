package com.example.bbettercalendar.ui.home;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.example.bbettercalendar.configuration.Configuration;
import com.example.bbettercalendar.configuration.ConfigurationManager;
import com.example.bbettercalendar.configuration.InitialConfiguration;
import com.example.bbettercalendar.database.AppDatabase;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.example.bbettercalendar.stats.Stats;
import com.example.bbettercalendar.stats.StatsDAO;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

public class HomeViewModel extends AndroidViewModel {

    private final String TAG = "HomeFragmentTag";
    private final MutableLiveData<String> mText;
    private final MutableLiveData<String> timerText;
    private final MutableLiveData<String> currentStreakText;
    private final MutableLiveData<String> todayFailsText;
    private final MutableLiveData<String> todayTimeStudiedText;
    private final MutableLiveData<String> timerModeText;
    private String currentStreakString;
    private ExecutorService executorService;
    private StatsDAO statsDao;
    public ConfigurationManager configManager;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        mText = new MutableLiveData<>();
        timerText = new MutableLiveData<>();
        currentStreakText = new MutableLiveData<>();
        todayFailsText = new MutableLiveData<>();
        todayTimeStudiedText = new MutableLiveData<>();
        timerModeText = new MutableLiveData<>();
        mText.setValue("This is home fragment");
        timerText.setValue("20:00");
        AppDatabase db = AppDatabase.getDatabase(application);
        statsDao = db.statsDao();
        executorService = Executors.newFixedThreadPool(2);

        // Observador que se activa cuando la base de datos se ha inicializado en la clase InitialConfiguration
        /*Observer<Boolean> initializationObserver = new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean initialized) {
                if (initialized) {
                    executorService.execute(new Runnable() {
                            @Override
                            public void run() {
                                Stats initialStats = statsDao.getStats();
                                setInitialTexts(initialStats);
                        }
                    });
                    // Elimina el observador una vez que la condición se ha cumplido
                    InitialConfiguration.getInstance().getInitializationStatus().removeObserver(this);
                }
            }
        };
        InitialConfiguration.getInstance().getInitializationStatus().observeForever(initializationObserver);*/
        executorService.execute(() -> {
            Stats initialStats = statsDao.getStats();
            setInitialTexts(initialStats);
        });

    }

    private void setInitialTexts(Stats initialStats){
        Log.i(TAG, "View Model setInitialTexts()");
        //get the current streak from the database using statsDao.getCurrentStreak() and transform it from int to string
        //currentStreakText.postValue("Current streak: "+statsDao.getCurrentStreak());
        currentStreakText.postValue("Current streak: "+initialStats.currentStreak);
        //todayFailsText.postValue("Today fails: "+statsDao.getTodayFails());
        todayFailsText.postValue("Today fails: "+initialStats.todayFails);
        String formattedTime = FormatHelper.formatTime(initialStats.todayTimeStudied, "HH:mm");
        todayTimeStudiedText.postValue("Today studied time: " + formattedTime);
        timerModeText.postValue("-- Concentration --");
    }

    //Cuando el usuario cierra la app a medio contador
    public void addFails() {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "View Model addFails()");
                statsDao.addFails();
                todayFailsText.postValue("Today fails: "+statsDao.getTodayFails());
            }
        });
    }

    //Cuando el temporizador llega a 0 se actualizan y guardan estadísticas
    public void completeTimer(int timerTime){
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "View Model addTimeStudied( " + timerTime + " )");
                statsDao.addTimeStudied(timerTime);
                statsDao.addTasksDone();
                String formattedTime = FormatHelper.formatTime(statsDao.getTodayTimeStudied(), "HH:mm");
                todayTimeStudiedText.postValue("Today studied time: " + formattedTime);
            }
        });
    }

    public void setRestTimer(){
        timerText.postValue(FormatHelper.formatTime(configManager.getConfiguration().getHomeRestTime(), "mm:ss"));
        timerModeText.postValue("-- Rest --");
    }

    public void resetTimer(){
        timerText.postValue(FormatHelper.formatTime(configManager.getConfiguration().getHomeTimerTime(), "mm:ss"));
        timerModeText.postValue("-- Concentration --");
    }

    public void setConfigManager(ConfigurationManager configManager) {
        this.configManager = configManager;
        timerText.postValue(FormatHelper.formatTime(configManager.getConfiguration().getHomeTimerTime(), "mm:ss"));
    }

    public void updateConfiguration(Configuration config){
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                configManager.updateConfiguration(config);
                updateConfigurationUIValues();
            }
        });
    }

    private void updateConfigurationUIValues(){
        timerText.postValue(FormatHelper.formatTime(configManager.getConfiguration().getHomeTimerTime(), "mm:ss"));
        //todo update other values
    }

    public LiveData<String> getText() {
        return mText;
    }
    public LiveData<String> getTimerText() {
        return timerText;
    }
    public LiveData<String> getCurrentStreakText() {return currentStreakText;}
    public LiveData<String> getTodayFailsText() {return todayFailsText;}
    public LiveData<String> getTodayTimeStudiedText() {return todayTimeStudiedText;}
    public LiveData<String> getTimerModeText() {return timerModeText;}

}