package com.example.bbettercalendar.ui.calendar;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ScaleXSpan;
import android.util.Log;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;

import com.example.bbettercalendar.calendarEntries.AddEventActivity;
import com.example.bbettercalendar.configuration.Configuration;
import com.example.bbettercalendar.database.AppDatabase;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.helpers.ScreenHelper;
import com.example.bbettercalendar.helpers.ToolbarHelper;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

public class CalendarController {

    @Inject
    Configuration config;
    public static final String TAG = "CalendarTag";
    CalendarEntryDAO calendarEntryDAO;
    private Activity activity;
    private Context context;
    private ToolbarHelper toolbarHelper;

    public CalendarController(Activity activity, Context context){

        this.activity = activity;
        this.context = context;
        calendarEntryDAO = AppDatabase.getDatabase(context.getApplicationContext()).eventDao();

    }


    public void handleOnActivityResult(ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK) {
            // Hay datos de retorno
            Intent data = result.getData();
            // Maneja el resultado utilizando los datos del Intent
        }
        if(result.getResultCode() == Activity.RESULT_CANCELED || result.getResultCode() == Activity.RESULT_OK){
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    List<CalendarEntry> calendarEntries;
                    calendarEntries = calendarEntryDAO.getAllEvents();
                    try {
                        for(int i = 0; i< calendarEntries.size(); i++){
                            printEvents(calendarEntries.get(i));
                        }
                    }catch (Exception e){
                        Log.i(TAG, "no hay eventos");
                    }

                }
            });
        }
    }

    private void printEvents(CalendarEntry calendarEntry){
        SimpleDateFormat dateFormat=new SimpleDateFormat("dd/MM/yyyy HH:mm");
        if (calendarEntry.getType() == AddEventActivity.TYPE_EVENT) {
            Log.i(TAG, "event type: event");
        } else {
            Log.i(TAG, "event type: task");
        }
        Log.i(TAG, "event title: "+ calendarEntry.getTitle());
        if(calendarEntry.getStartDayAndHour()!=null)
            Log.i(TAG,"dia y hora start: "+ dateFormat.format(calendarEntry.getStartDayAndHour().getTime()));
        else
            Log.i(TAG,"dia y hora start: null");
        if(calendarEntry.getEndDayAndHour()!=null)
            Log.i(TAG,"dia y hora end: "+ dateFormat.format(calendarEntry.getEndDayAndHour().getTime()));
        else
            Log.i(TAG,"dia y hora end: null");
        if(calendarEntry.getDescription()!=null && !calendarEntry.getDescription().isEmpty()){
            Log.i(TAG, "event description: "+ calendarEntry.getDescription());
        }
        if(calendarEntry.getDuration()!=0){
            Log.i(TAG, "event duration: "+ calendarEntry.getDuration());
        }
            Log.i(TAG, "event repetition: "+ calendarEntry.getRepetition());
        if(calendarEntry.getNotifications()!=null){
            Log.i(TAG, "event notifications: "+ Arrays.toString( calendarEntry.getNotifications()));
        }
        Log.i(TAG, "-------------------------------------------");
    }

    //Función para posicionar los días de la semana de manera uniforme a lo largo de screenWidth
    public void positionWeekDaysText(TextView calendarDayInitials, float screenWidth, int marginLeft){
        int firstDayOfWeek = Calendar.getInstance(Locale.getDefault()).getFirstDayOfWeek();
        String dayInitials = "";
        if(firstDayOfWeek == 1){
            dayInitials = "SMTWTFS";
        }else if(firstDayOfWeek == 2){
            dayInitials ="MTWTFSU";
        }else if(firstDayOfWeek == 3) {
            dayInitials = "TWTFSUM";
        }
        SpannableStringBuilder spannableBuilder = new SpannableStringBuilder(dayInitials);
        int marginLeftPixels = ScreenHelper.convertDpToPixels(marginLeft, context);
        screenWidth = screenWidth - marginLeftPixels;

        // Calcular el espacio disponible después de dibujar todas las letras
        Paint paint = new Paint();
        paint.setTextSize(calendarDayInitials.getTextSize());
        float totalTextWidth = paint.measureText(dayInitials);

        // Calcular el espacio disponible y el factor de escala requerido para los espacios
        float spaceAvailable = screenWidth - totalTextWidth;
        int numberOfSpaces = dayInitials.length();
        float spaceWidth = paint.measureText(" ");
        float totalSpaceWidthRequired = spaceAvailable + (numberOfSpaces * spaceWidth);
        float scaleX = totalSpaceWidthRequired / (numberOfSpaces * spaceWidth);

        // Insertar espacios y aplicar ScaleXSpan
        for (int i = dayInitials.length() - 1; i > 0; i--) {
            spannableBuilder.insert(i, " ");

            spannableBuilder.setSpan(new ScaleXSpan(scaleX - 1), i, i + 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        }
        calendarDayInitials.setText(spannableBuilder);
    }
}
