package com.example.bbettercalendar.configuration;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.example.bbettercalendar.MainActivity;
import com.example.bbettercalendar.R;
import com.example.bbettercalendar.database.AppDatabase;
import com.example.bbettercalendar.stats.Stats;
import com.example.bbettercalendar.stats.StatsDAO;

import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SplashActivity extends AppCompatActivity {

    private final String TAG = "SplashScreenTag";
    private StatsDAO statsDao;
    private ConfigurationDAO configurationDao;
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

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

            // Una vez que las tareas de inicialización están completas, iniciar MainActivity en el hilo principal
            runOnUiThread(() -> {
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            });
        });

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

    private void initializeStats() {
        if (statsDao.getStats() == null) {
            statsDao.insert(new Stats());
        }
        if(configurationDao.getConfiguration() == null){
            configurationDao.insert(new Configuration());
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown(); // Apagar el executor cuando la actividad se destruye
    }
}