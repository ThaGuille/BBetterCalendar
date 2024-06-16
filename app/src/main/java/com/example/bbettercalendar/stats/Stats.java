package com.example.bbettercalendar.stats;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "stats")
public class Stats {
    @PrimaryKey(autoGenerate = true)
    private int id;
    public int totalTimeStudied;
    public int todayTimeStudied;
    public int totalTasksDone;
    public int maxStreak;
    public int currentStreak;
    public int totalFails;
    public int todayFails;

    public Stats(int totalTimeStudied, int todayTimeStudied, int totalTasksDone, int maxStreak, int currentStreak, int totalFails, int todayFails) {
        this.totalTimeStudied = totalTimeStudied;
        this.todayTimeStudied = todayTimeStudied;
        this.totalTasksDone = totalTasksDone;
        this.maxStreak = maxStreak;
        this.currentStreak = currentStreak;
        this.totalFails = totalFails;
        this.todayFails = todayFails;
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
