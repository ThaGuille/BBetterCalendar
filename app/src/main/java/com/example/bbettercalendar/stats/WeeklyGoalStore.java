package com.example.bbettercalendar.stats;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.social.SocialFeature;
import com.example.bbettercalendar.social.stats.StatsPublisher;

import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Lazy;
import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Objetivo semanal de la racha (días de foco por semana, 1-7). Vive en SharedPreferences y no en
 * {@code Configuration} a propósito: añadir una columna obligaría a subir {@code @Database(version)}
 * (regla #6). La primera lectura toca disco, así que se hace en el executor y se publica con
 * postValue (regla #3).
 */
@Singleton
public class WeeklyGoalStore {

    public static final int DEFAULT_GOAL = 3;

    private static final String PREFS = "focus_goal";
    private static final String KEY_WEEKLY_DAYS = "weekly_days";

    private final Context context;
    private final ExecutorService ioExecutor;
    // F3-u4 (D-030): Constancia usa el objetivo, así que cambiarlo republica stats/. Lazy y detrás del flag:
    // en release (FEATURE_SOCIAL=false) nunca se crea el publicador ni se toca Firebase desde aquí.
    private final Lazy<StatsPublisher> statsPublisher;
    private final MutableLiveData<Integer> goal = new MutableLiveData<>();

    @Inject
    public WeeklyGoalStore(@ApplicationContext Context context, @IoExecutor ExecutorService ioExecutor,
                           Lazy<StatsPublisher> statsPublisher) {
        this.context = context;
        this.ioExecutor = ioExecutor;
        this.statsPublisher = statsPublisher;
        ioExecutor.execute(() -> goal.postValue(getGoalSync()));
    }

    /** Lectura síncrona del objetivo (acotado a 1-7), para {@code StatsPublisher}. Toca disco: nunca en main. */
    @WorkerThread
    public int getGoalSync() {
        return StreakCalculator.clampGoal(prefs().getInt(KEY_WEEKLY_DAYS, DEFAULT_GOAL));
    }

    /** Objetivo actual; emite al cargarse y en cada {@link #setGoal(int)}. */
    @NonNull
    public LiveData<Integer> observeGoal() {
        return goal;
    }

    /** Guarda el objetivo (acotado a 1-7) y lo publica a todos los observadores de la racha. */
    public void setGoal(int days) {
        int g = StreakCalculator.clampGoal(days);
        goal.postValue(g);
        ioExecutor.execute(() -> {
            prefs().edit().putInt(KEY_WEEKLY_DAYS, g).apply();
            // Tras guardar: apply() ya deja el valor en memoria, así que getGoalSync() lo ve.
            if (SocialFeature.isAccountEnabled()) {
                statsPublisher.get().requestPublish();
            }
        });
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
