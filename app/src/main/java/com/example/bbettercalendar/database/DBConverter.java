package com.example.bbettercalendar.database;

import android.location.Location;

import androidx.room.TypeConverter;

public class DBConverter {
    @TypeConverter
    public String fromLocation(Location location) {
        if (location == null) {
            return null;
        }
        // Aquí convertirías el objeto Location a un String, por ejemplo, usando JSON
        //todo Guillem return convertLocationToString(location);
        return null;
    }

    @TypeConverter
    public Location toLocation(String locationString) {
        if (locationString == null) {
            return null;
        }
        // Aquí convertirías el String de vuelta a un objeto Location
        //todo Guillem: return convertStringToLocation(locationString);
        return null;
    }
}
