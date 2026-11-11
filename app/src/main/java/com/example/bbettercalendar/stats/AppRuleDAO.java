package com.example.bbettercalendar.stats;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AppRuleDAO {

    // REPLACE sobre la PK 'packageName' -> upsert limpio sin read-modify-write.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(AppRule rule);

    // Lista de apps seguidas (uso síncrono desde el executor al unir con el uso del rango).
    @Query("SELECT * FROM app_rule WHERE tracked = 1 ORDER BY packageName")
    List<AppRule> getTracked();

    // Versión observable por si alguna pantalla quiere reaccionar a cambios del picker.
    @Query("SELECT * FROM app_rule WHERE tracked = 1 ORDER BY packageName")
    LiveData<List<AppRule>> observeTracked();

    @Query("SELECT * FROM app_rule")
    List<AppRule> getAll();

    @Query("SELECT * FROM app_rule WHERE packageName = :pkg")
    AppRule getByPackage(String pkg);

    @Query("UPDATE app_rule SET tracked = :tracked WHERE packageName = :pkg")
    void setTracked(String pkg, boolean tracked);

    // Phase 3: límite diario en minutos (0 = sin límite). Columna ya existente desde MIGRATION_9_10.
    // mejora-visual u1 (v15): en el mismo UPDATE se lleva limitSinceMillis -> :nowMillis al pasar de
    // sin límite (0) a un límite, 0 al quitarlo, y se conserva si solo cambia el valor. En SQLite las
    // expresiones del SET leen la fila ANTERIOR, así que el CASE ve el dailyLimitMinutes viejo.
    @Query("UPDATE app_rule SET "
            + "limitSinceMillis = CASE WHEN :minutes <= 0 THEN 0 "
            + "WHEN dailyLimitMinutes <= 0 THEN :nowMillis ELSE limitSinceMillis END, "
            + "dailyLimitMinutes = :minutes WHERE packageName = :pkg")
    void setDailyLimit(String pkg, int minutes, long nowMillis);

    // u20: aviso previo en minutos (detalle por app). Columna ya existente desde MIGRATION_9_10.
    @Query("UPDATE app_rule SET warnBeforeMinutes = :minutes WHERE packageName = :pkg")
    void setWarnBefore(String pkg, int minutes);

    // Apps seguidas Y con un límite activo — lo que UsageLimitChecker/Scheduler necesitan comprobar.
    @Query("SELECT * FROM app_rule WHERE tracked = 1 AND dailyLimitMinutes > 0")
    List<AppRule> getLimited();

    // Phase 4a: activa/desactiva el "hacer cumplir el límite" por app (columna física 'instantBlock').
    @Query("UPDATE app_rule SET instantBlock = :enforce WHERE packageName = :pkg")
    void setEnforceAtLimit(String pkg, boolean enforce);

    // Apps que el bloqueador debe hacer cumplir: seguidas, con límite y con enforce activo.
    @Query("SELECT * FROM app_rule WHERE tracked = 1 AND dailyLimitMinutes > 0 AND instantBlock = 1")
    List<AppRule> getEnforced();

    // Versión observable — la usa BlockerAccessibilityService para mantener su caché de reglas viva.
    @Query("SELECT * FROM app_rule WHERE tracked = 1 AND dailyLimitMinutes > 0 AND instantBlock = 1")
    LiveData<List<AppRule>> observeEnforced();

    @Query("DELETE FROM app_rule WHERE packageName = :pkg")
    void delete(String pkg);
}
