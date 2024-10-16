package com.example.bbettercalendar.helpers;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class FormatHelper {

    public static String formatTime(int milliseconds, String formatMask) {
        int totalSeconds = milliseconds / 1000;
        int totalMinutes = totalSeconds / 60;
        int minutes = totalMinutes % 60;

        if(formatMask.equals("HH:mm")){
            // Formatear el tiempo en el formato HH:mm
            int hours = totalMinutes / 60;
            return String.format("%02d:%02d", hours, minutes);
        }else if(formatMask.equals("mm:ss")) {
            // Formatear el tiempo en el formato mm:ss
            int seconds = totalSeconds % 60;
            return String.format("%02d:%02d", minutes, seconds);
        }else{
            return "Format mask not recognized";
        }
    }

    public static String formatDateToDateString(Calendar calendar){
        return DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(calendar.getTime());
    }
    public static String formatTimeToTimeString(Calendar calendar){
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.getTime());
    }

    public static int millisToMinutes(int millis){
           return millis / 60000;
    }

    public static int minutesToMillis(int minutes){
           return minutes * 60000;
    }
}
