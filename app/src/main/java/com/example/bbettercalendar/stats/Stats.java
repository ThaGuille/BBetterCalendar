package com.example.bbettercalendar.stats;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.time.LocalDate;
import java.util.Calendar;

@Entity(tableName = "stats")
public class Stats {
    @PrimaryKey(autoGenerate = true)
    private int id;
    public int totalTimeStudied;
    public int todayTimeStudied;
    public int totalTasksDone;
    public int todayTasksDone;
    public int maxStreak;
    public int currentStreak;
    private Calendar lastDayStreak;
    public int totalFails;
    public int todayFails;

    public Stats(int totalTimeStudied, int todayTimeStudied, int totalTasksDone, int todayTasksDone, int maxStreak, int currentStreak, Calendar lastDayStreak, int totalFails, int todayFails) {
        this.totalTimeStudied = totalTimeStudied;
        this.todayTimeStudied = todayTimeStudied;
        this.totalTasksDone = totalTasksDone;
        this.todayTasksDone = todayTasksDone;
        this.maxStreak = maxStreak;
        this.currentStreak = currentStreak;
        this.lastDayStreak = lastDayStreak;
        this.totalFails = totalFails;
        this.todayFails = todayFails;
    }

    public Stats() {
        this.totalTimeStudied = 0;
        this.todayTimeStudied = 0;
        this.totalTasksDone = 0;
        this.todayTasksDone = 0;
        this.maxStreak = 0;
        this.currentStreak = 0;
        this.lastDayStreak = null;
        this.totalFails = 0;
        this.todayFails = 0;
    }

    // Getters y Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getTotalTimeStudied() {
        return totalTimeStudied;
    }

    public void setTotalTimeStudied(int totalTimeStudied) {
        this.totalTimeStudied = totalTimeStudied;
    }

    public int getTodayTimeStudied() {
        return todayTimeStudied;
    }

    public void setTodayTimeStudied(int todayTimeStudied) {
        this.todayTimeStudied = todayTimeStudied;
    }

    public int getTotalTasksDone() {
        return totalTasksDone;
    }

    public void setTotalTasksDone(int totalTasksDone) {
        this.totalTasksDone = totalTasksDone;
    }

    public int getTodayTasksDone() {return todayTasksDone;}

    public void setTodayTasksDone(int todayTasksDone) {this.todayTasksDone = todayTasksDone;}

    public int getMaxStreak() {
        return maxStreak;
    }

    public void setMaxStreak(int maxStreak) {
        this.maxStreak = maxStreak;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public void setCurrentStreak(int currentStreak) {
        this.currentStreak = currentStreak;
    }

    public Calendar getLastDayStreak() {return lastDayStreak;}

    public void setLastDayStreak(Calendar lastDayStreak) {this.lastDayStreak = lastDayStreak;}

    public int getTotalFails() {
        return totalFails;
    }

    public void setTotalFails(int totalFails) {
        this.totalFails = totalFails;
    }

    public int getTodayFails() {
        return todayFails;
    }

    public void setTodayFails(int todayFails) {
        this.todayFails = todayFails;
    }
}
