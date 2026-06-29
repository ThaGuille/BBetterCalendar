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

    @Query("DELETE FROM app_rule WHERE packageName = :pkg")
    void delete(String pkg);
}
