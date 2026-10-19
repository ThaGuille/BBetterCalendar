package com.example.bbettercalendar.configuration;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.example.bbettercalendar.BuildConfig;
import com.example.bbettercalendar.MainActivity;
import com.example.bbettercalendar.R;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.calendarEntries.RecurrenceMaterializer;
import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.example.bbettercalendar.stats.DailyStat;
import com.example.bbettercalendar.stats.DailyStatDAO;
import com.example.bbettercalendar.stats.Stats;
import com.example.bbettercalendar.stats.StatsDAO;
import com.example.bbettercalendar.ui.common.InsetsHelper;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SplashActivity extends AppCompatActivity {

    private final String TAG = "SplashScreenTag";
    @Inject StatsDAO statsDao;
    @Inject DailyStatDAO dailyStatDao;
    @Inject ConfigurationDAO configurationDao;
    @Inject CalendarEntryDAO calendarEntryDao;
    @Inject @IoExecutor ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // A sangre completa (F0-u2): el logo va centrado, no hay nada que proteger de las barras.
        InsetsHelper.enableEdgeToEdge(this);
        // u14 (pregunta u6): el icono del launcher con la app ya viva. Si la tarea no se abrió
        // desde el launcher (notificación, adb, IDE...), su intent base no coincide y el sistema
        // apila ESTA Splash encima de la MainActivity viva: antes arrancaba una segunda
        // MainActivity (Home en reposo, una segunda sesión, y la primera contaba un fallo).
        // Sin ser raíz de la tarea y con un intent MAIN/LAUNCHER, basta con cerrarse: queda
        // delante la MainActivity existente con su sesión intacta.
        if (isRelaunchFromLauncher()) {
            Log.i(TAG, "Launcher tap on a live task: resuming the existing MainActivity");
            finish();
            return;
        }
        setContentView(R.layout.activity_splash);

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
                // Solo debug (u12): reenvía los extras bb_debug_* de MainActivity.handleDebugNav,
                // para abrir un destino secundario con adb am start.
                if (BuildConfig.DEBUG && getIntent().getExtras() != null) {
                    intent.putExtras(getIntent().getExtras());
                }
                startActivity(intent);
                finish();
            });
        });

        // Top-up de recurrencia (spec tasks-recurrence): adopta series legacy y rellena la ventana
        // de ocurrencias de cada plantilla. En su propia tarea para no retrasar el arranque de la UI.
        executorService.execute(() ->
                new RecurrenceMaterializer(getApplicationContext(), calendarEntryDao).materializeAll());

    }

    /** Launcher intent delivered to a task that already has activities below this one. */
    private boolean isRelaunchFromLauncher() {
        if (isTaskRoot()) {
            return false;
        }
        Intent intent = getIntent();
        return intent != null
                && Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_LAUNCHER);
    }

    private void resetDailyStats(Calendar today, Calendar yesterday){
        //Usamos la lastDayStreak para saber si ya se ha conectado hoy
        Calendar lastDayStreak = statsDao.getLastDayStreak();
        if (lastDayStreak!=null){
            if(lastDayStreak.before(today)){
                // Guardamos los valores de "hoy" en el histórico ANTES de ponerlos a 0.
                // Pertenecen al último día activo (lastDayStreak), no necesariamente a ayer.
                persistDailyStat(lastDayStreak);
                statsDao.resetDailyStats();
            }
        }
    }

    // Vuelca los contadores de "hoy" de Stats en una fila DailyStat, indexada por el día
    // (ISO) del último día activo. todayTimeStudied está en milisegundos -> a minutos.
    private void persistDailyStat(Calendar lastDayStreak){
        Stats s = statsDao.getStats();
        DailyStat ds = new DailyStat();
        ds.day = LocalDate.of(lastDayStreak.get(Calendar.YEAR),
                lastDayStreak.get(Calendar.MONTH) + 1,
                lastDayStreak.get(Calendar.DAY_OF_MONTH)).toString();
        ds.focusMinutes = FormatHelper.millisToMinutes(s.todayTimeStudied);
        ds.fails = s.todayFails;
        ds.tasksDone = s.todayTasksDone;
        dailyStatDao.upsert(ds);
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

}