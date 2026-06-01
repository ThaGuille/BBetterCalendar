package com.example.bbettercalendar.stats;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface DailyStatDAO {

    // REPLACE sobre la PK 'day' -> upsert limpio sin read-modify-write.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(DailyStat stat);

    @Query("SELECT * FROM daily_stat WHERE day = :day")
    DailyStat getByDay(String day);

    // Rango inclusivo por fecha ISO; las cadenas "YYYY-MM-DD" ordenan cronológicamente.
    @Query("SELECT * FROM daily_stat WHERE day BETWEEN :start AND :end ORDER BY day")
    List<DailyStat> getRange(String start, String end);
}
