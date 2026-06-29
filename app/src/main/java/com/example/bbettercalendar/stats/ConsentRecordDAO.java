package com.example.bbettercalendar.stats;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface ConsentRecordDAO {

    // `key` va con backticks por consistencia con la migración (no es palabra reservada en SQLite,
    // pero así evitamos cualquier ambigüedad).
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ConsentRecord record);

    @Query("SELECT * FROM consent_record WHERE `key` = :key")
    ConsentRecord get(String key);

    @Query("SELECT EXISTS(SELECT 1 FROM consent_record WHERE `key` = :key)")
    boolean hasConsent(String key);
}
