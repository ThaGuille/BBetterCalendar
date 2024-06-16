package com.example.bbettercalendar.ui.calendar;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ScaleXSpan;
import android.util.Log;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.configuration.Configuration;
import com.example.bbettercalendar.database.AppDatabase;
import com.example.bbettercalendar.events.Event;
import com.example.bbettercalendar.events.EventDao;
import com.example.bbettercalendar.helpers.ScreenHelper;
import com.example.bbettercalendar.helpers.ToolbarHelper;

import java.text.SimpleDateFormat;
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
    EventDao eventDao;
    private Activity activity;
    private Context context;
    private ToolbarHelper toolbarHelper;

    public CalendarController(Activity activity, Context context){

        this.activity = activity;
        this.context = context;
        eventDao = AppDatabase.getDatabase(context.getApplicationContext()).eventDao();

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
                    List<Event> events;
                    events = eventDao.getAllEvents();
                    try {
                        for(int i=0;i<events.size();i++){
                            printEvents(events.get(i));
                        }
                    }catch (Exception e){
                        Log.i(TAG, "no hay eventos");
                    }

                }
            });
        }
    }

    private void printEvents(Event event){
        SimpleDateFormat dateFormat=new SimpleDateFormat("dd/MM/yyyy HH:mm");
        Log.i(TAG, "event title: "+event.getTitle());
        if(event.getStartDayAndHour()!=null)
            Log.i(TAG,"dia y hora start: "+ dateFormat.format(event.getStartDayAndHour().getTime()));
        else
            Log.i(TAG,"dia y hora: null");
        if(event.getEndDayAndHour()!=null)
            Log.i(TAG,"dia y hora start: "+ dateFormat.format(event.getEndDayAndHour().getTime()));
        else
            Log.i(TAG,"dia y hora: null");
        if(event.getDescription()!=null && !event.getDescription().isEmpty()){
            Log.i(TAG, "event description: "+event.getDescription());
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
