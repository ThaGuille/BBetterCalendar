package com.example.bbettercalendar.configuration;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "configuration")
public class Configuration {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private int homeTimerTime;
    private int homeRestTime;
    private int homeNumberOfCycles;
    private boolean homeIsRestEnabled;
    private boolean homeIsAutoCycle;
    private boolean homeIsPauseEnabled;
    private boolean homeIsAlarmEnabled;
    private boolean homeIsVibrationEnabled;


    private String country;
    /** Clase contenedor para las configuraciones de la aplicación **/
    public Configuration(int homeTimerTime, int homeRestTime, int homeNumberOfCycles, boolean homeIsRestEnabled, boolean homeIsAutoCycle, boolean homeIsPauseEnabled, boolean homeIsAlarmEnabled, boolean homeIsVibrationEnabled) {
        this.homeTimerTime = homeTimerTime;
        this.homeRestTime = homeRestTime;
        this.homeNumberOfCycles = homeNumberOfCycles;
        this.homeIsRestEnabled = homeIsRestEnabled;
        this.homeIsAutoCycle = homeIsAutoCycle;
        this.homeIsPauseEnabled = homeIsPauseEnabled;
        this.homeIsAlarmEnabled = homeIsAlarmEnabled;
        this.homeIsVibrationEnabled = homeIsVibrationEnabled;
    }

    public Configuration(){
        this.homeTimerTime = 20;
        this.homeRestTime = 5;
        this.homeNumberOfCycles = 3;
        this.homeIsRestEnabled = true;
        this.homeIsAutoCycle = true;
        this.homeIsPauseEnabled = true;
        this.homeIsAlarmEnabled = true;
        this.homeIsVibrationEnabled = true;
    }

    // Getters y Setters

    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public int getHomeTimerTime() {
        return homeTimerTime;
    }
    public void setHomeTimerTime(int homeTimerTime) {
        this.homeTimerTime = homeTimerTime;
    }
    public int getHomeRestTime() {
        return homeRestTime;
    }
    public void setHomeRestTime(int homeRestTime) {
        this.homeRestTime = homeRestTime;
    }
    public int getHomeNumberOfCycles() {
        return homeNumberOfCycles;
    }
    public void setHomeNumberOfCycles(int homeNumberOfCycles) {
        this.homeNumberOfCycles = homeNumberOfCycles;
    }
    public boolean isHomeIsRestEnabled() {
        return homeIsRestEnabled;
    }
    public void setHomeIsRestEnabled(boolean homeIsRestEnabled) {
        this.homeIsRestEnabled = homeIsRestEnabled;
    }
    public boolean isHomeIsAutoCycle() {
        return homeIsAutoCycle;
    }
    public void setHomeIsAutoCycle(boolean homeIsAutoCycle) {
        this.homeIsAutoCycle = homeIsAutoCycle;
    }
    public boolean isHomeIsPauseEnabled() {
        return homeIsPauseEnabled;
    }
    public void setHomeIsPauseEnabled(boolean homeIsPauseEnabled) {
        this.homeIsPauseEnabled = homeIsPauseEnabled;
    }
    public boolean isHomeIsAlarmEnabled() {
        return homeIsAlarmEnabled;
    }
    public void setHomeIsAlarmEnabled(boolean homeIsAlarmEnabled) {
        this.homeIsAlarmEnabled = homeIsAlarmEnabled;
    }
    public boolean isHomeIsVibrationEnabled() {
        return homeIsVibrationEnabled;
    }
    public void setHomeIsVibrationEnabled(boolean homeIsVibrationEnabled) {
        this.homeIsVibrationEnabled = homeIsVibrationEnabled;
    }
    public String getCountry() {
        return country;
    }
    public void setCountry(String country) {
        this.country = country;
    }
}
