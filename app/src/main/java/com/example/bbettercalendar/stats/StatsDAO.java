package com.example.bbettercalendar.stats;

import com.example.bbettercalendar.stats.Stats;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

@Dao
public interface StatsDAO {

        @Insert
        void insert(Stats stats);

        @Query("SELECT * FROM stats WHERE id = :id")
        Stats getStatsById(int id);

        @Query("SELECT * FROM stats")
        Stats getStats();

        @Query("UPDATE Stats SET totalTimeStudied = :totalTimeStudied")
        void updateTotalTimeStudied(int totalTimeStudied);

        @Query("UPDATE Stats SET todayTimeStudied = :todayTimeStudied")
        void updateTodayTimeStudied(int todayTimeStudied);

        @Query("UPDATE Stats SET totalTasksDone = :totalTasksDone")
        void updateTotalTasksDone(int totalTasksDone);

        @Query("UPDATE Stats SET maxStreak = :maxStreak")
        void updateMaxStreak(int maxStreak);

        @Query("UPDATE Stats SET currentStreak = :currentStreak")
        void updateCurrentStreak(int currentStreak);

        @Query("UPDATE Stats SET totalFails = :totalFails")
        void updateTotalFails(int totalFails);

        @Query("UPDATE Stats SET todayFails = :todayFails")
        void updateTodayFails(int todayFails);

}
