package com.example.bbettercalendar.helpers;

public class FormatHelper {

    public static String formatTime(int milliseconds, String formatMask) {
        int totalSeconds = milliseconds / 1000;
        int seconds = totalSeconds % 60;
        int totalMinutes = totalSeconds / 60;
        int minutes = totalMinutes % 60;
        int hours = totalMinutes / 60;

        if(formatMask.equals("HH:mm")){
            // Formatear el tiempo en el formato HH:mm
            return String.format("%02d:%02d", hours, minutes);
        }else if(formatMask.equals("mm:ss")) {
            // Formatear el tiempo en el formato mm:ss
            return String.format("%02d:%02d", minutes, seconds);
        }else{
            return "Format mask not recognized";
        }
    }
}
