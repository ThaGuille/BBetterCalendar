package com.example.bbettercalendar.stats;

import com.example.bbettercalendar.events.Event;
import com.example.bbettercalendar.stats.Stats;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.Calendar;

@Dao
public interface StatsDAO {

        @Insert
        void insert(Stats stats);

        @Query("SELECT * FROM stats WHERE id = :id")
        Stats getStatsById(int id);

        @Query("SELECT * FROM stats")
        Stats getStats();

        @Query("SELECT lastDayStreak FROM stats")
        Calendar getLastDayStreak();

        @Query("SELECT currentStreak FROM stats")
        int getCurrentStreak();

        @Query("SELECT maxStreak FROM stats")
        int getMaxStreak();

        @Query("SELECT todayFails FROM stats")
        int getTodayFails();

        @Query("SELECT todayTimeStudied FROM stats")
        int getTodayTimeStudied();

        @Query("UPDATE Stats SET totalTimeStudied = :totalTimeStudied")
        void updateTotalTimeStudied(int totalTimeStudied);

        @Query("UPDATE Stats SET totalTimeStudied = totalTimeStudied + :addTimeStudied")
        void addTotalTimeStudied(int addTimeStudied);

        @Query("UPDATE Stats SET todayTimeStudied = :todayTimeStudied")
        void updateTodayTimeStudied(int todayTimeStudied);

        @Query("UPDATE Stats SET totalTimeStudied = totalTimeStudied + :addTimeStudied, todayTimeStudied = todayTimeStudied + :addTimeStudied")
        void addTimeStudied(int addTimeStudied);

        @Query("UPDATE Stats SET todayTimeStudied = todayTimeStudied + 1")
        void addTodayTimeStudied();

        @Query("UPDATE Stats SET totalTasksDone = :totalTasksDone")
        void updateTotalTasksDone(int totalTasksDone);

        @Query("UPDATE Stats SET totalTasksDone = totalTasksDone+1")
        void addTotalTasksDone();

        @Query("UPDATE Stats SET totalTasksDone = totalTasksDone+1, todayTasksDone =todayTasksDone+1")
        void addTasksDone();

        @Query("UPDATE Stats SET maxStreak = :maxStreak")
        void updateMaxStreak(int maxStreak);

        @Query("UPDATE Stats SET maxStreak = maxStreak +1")
        void addMaxStreak();

        @Query("UPDATE Stats SET currentStreak = :currentStreak")
        void updateCurrentStreak(int currentStreak);

        @Query("UPDATE Stats SET currentStreak = currentStreak +1")
        void addCurrentStreak();

        @Query("UPDATE Stats SET lastDayStreak = :lastDayStreak")
        void updateLastDayStreak(Calendar lastDayStreak);

        @Query("UPDATE Stats SET totalFails = :totalFails")
        void updateTotalFails(int totalFails);

        @Query("UPDATE Stats SET totalFails = totalFails +1")
        void addTotalFails();

        @Query("UPDATE Stats SET todayFails = :todayFails")
        void updateTodayFails(int todayFails);

        @Query("UPDATE Stats SET todayFails = todayFails +1")
        void addTodayFails();

        @Query("UPDATE Stats SET totalFails = totalFails + 1, todayFails = todayFails + 1")
        void addFails();

        @Query("UPDATE Stats SET todayTimeStudied = 0, todayFails = 0, todayTasksDone = 0")
        void resetDailyStats();

}
