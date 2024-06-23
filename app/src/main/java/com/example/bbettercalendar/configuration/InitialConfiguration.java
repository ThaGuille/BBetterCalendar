package com.example.bbettercalendar.configuration;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.bbettercalendar.database.AppDatabase;
import com.example.bbettercalendar.stats.Stats;
import com.example.bbettercalendar.stats.StatsDAO;

import java.util.Calendar;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InitialConfiguration extends AppCompatActivity {

    private static InitialConfiguration instance;
    private MutableLiveData<Boolean> isInitialized = new MutableLiveData<>();



    private final String TAG = "InitialConfigurationTag";
    private StatsDAO statsDao;
    private ConfigurationDAO configurationDao;
    private ExecutorService executorService;
    //Esto sirve para que la app espere a que se terminen X tareas en background antes de continuar, en este caso una
    final CountDownLatch latch = new CountDownLatch(1);

    public static synchronized InitialConfiguration getInstance() {
        if (instance == null) {
            instance = new InitialConfiguration();
        }
        return instance;
    }

    public LiveData<Boolean> getInitializationStatus() {
        return isInitialized;
    }

    public void initialize(Context context) {
        statsDao = AppDatabase.getDatabase(this).statsDao();
        configurationDao = AppDatabase.getDatabase(this).configurationDao();
        executorService = Executors.newFixedThreadPool(2);

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        Calendar yesterday = (Calendar) today.clone();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);

        //Primero se ejecuta un método asíncrono, luego otro, y finalmente se notifica
        executorService.execute(() -> {
            initializeStats();
            resetDailyStats(today, yesterday);
            checkAndUpdateStreak(today, yesterday);
            latch.countDown(); // Esto se llama después de completar checkAndUpdateStreak
        });


        executorService.execute(() -> {
            try {
                latch.await();  // Espera hasta que countDown() se llame
                isInitialized.postValue(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // Manejar la excepción como consideres necesario.
            }
        });


    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    private void initializeStats() {
        if (statsDao.getStats() == null) {
            statsDao.insert(new Stats());
        }
        if(configurationDao.getConfiguration() == null){
            configurationDao.insert(new Configuration());
        }
    }

    private void resetDailyStats(Calendar today, Calendar yesterday){
        //Usamos la lastDayStreak para saber si ya se ha conectado hoy
        Calendar lastDayStreak = statsDao.getLastDayStreak();
        if (lastDayStreak!=null){
            if(lastDayStreak.before(today)){
                statsDao.resetDailyStats();
            }
        }
    }

    private void checkAndUpdateStreak(Calendar today, Calendar yesterday) {

                Calendar lastDayStreak = statsDao.getLastDayStreak();

                if (lastDayStreak != null) {

                    if(lastDayStreak.before(today)){
                        if (lastDayStreak.equals(yesterday)) {
                            // Continuar la racha
                            statsDao.addCurrentStreak();
                            statsDao.updateLastDayStreak(today);
                        } else if (lastDayStreak.before(yesterday)) {
                            // Reiniciar la racha
                            statsDao.updateCurrentStreak(1);
                            statsDao.updateLastDayStreak(today);
                        }
                    }
                } else {
                    // Primera vez o día no consecutivo
                    statsDao.updateLastDayStreak(today);
                    statsDao.updateCurrentStreak(1);
                }
                if(statsDao.getCurrentStreak() > statsDao.getMaxStreak()){
                    statsDao.updateMaxStreak(statsDao.getCurrentStreak());
                }
                Log.i(TAG, "current Streak"+statsDao.getCurrentStreak());
                Log.i(TAG, "max Streak"+statsDao.getMaxStreak());


    }
}
