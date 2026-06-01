package com.example.bbettercalendar.stats;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FocusEventDAO {

    @Insert
    void insert(FocusEvent event);

    @Query("SELECT * FROM focus_event WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp")
    List<FocusEvent> getRange(long start, long end);
}
