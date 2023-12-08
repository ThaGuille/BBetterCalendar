package com.example.bbettercalendar.database;

import android.location.Location;

import androidx.room.TypeConverter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Calendar;

public class DBConverter {

    @TypeConverter
    public String fromCalendar(Calendar calendar) {
        if (calendar == null) {
            return null;
        }
        Gson gson = new Gson();
        return gson.toJson(calendar);
    }
    @TypeConverter
    public Calendar toCalendar(String calendar) {
        if (calendar == null) {
            return null;
        }
        Gson gson = new Gson();
        return gson.fromJson(calendar, Calendar.class);
    }

    @TypeConverter
    public String fromNotifications(boolean[] notifications) {
        if (notifications == null) {
            return null;
        }
        Gson gson = new Gson();
        return gson.toJson(notifications);
    }

    @TypeConverter
    public boolean[] toNotifications(String notifications) {
        if (notifications == null) {
            return null;
        }
        Gson gson = new Gson();
        Type type = new TypeToken<boolean[]>() {}.getType();
        return gson.fromJson(notifications, type);
    }

    @TypeConverter
    public String fromLocation(Location location) {
        if (location == null) {
            return null;
        }
        // Aquí convertirías el objeto Location a un String, por ejemplo, usando JSON
        return null;
    }

    @TypeConverter
    public Location toLocation(String locationString) {
        if (locationString == null) {
            return null;
        }
        // Aquí convertirías el String de vuelta a un objeto Location
        return null;
    }
}
