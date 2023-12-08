package com.example.bbettercalendar.events;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;

import androidx.appcompat.widget.Toolbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.room.Room;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.database.AppDatabase;
import com.example.bbettercalendar.databinding.ActivityCreateEventBinding;
import com.example.bbettercalendar.helpers.OnToolBarListener;
import com.example.bbettercalendar.helpers.ToolbarHelper;
import com.example.bbettercalendar.popups.NotificationsPopup;
import com.example.bbettercalendar.popups.OnNotificationsPopupListener;
import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class AddEventActivity extends AppCompatActivity implements OnToolBarListener, OnNotificationsPopupListener, View.OnClickListener  {


    public static final int CLOSE_AND_SAVE = 1;
    public static final int CLOSE = 2;
    private final int START = 10;
    private final int END = 11;
    private final String TAG = "AddEventActivityTag";

    private boolean[] createdNotificationLayouts = new boolean[7];
    private View[] createdNotificationViews = new View[7];
    private int indexLayout=4;

    private ActivityCreateEventBinding binding;
    private ToolbarHelper toolbarHelper;
    EventDao eventDao;

    private Event.EventBuilder eventBuilder;
    private NotificationsPopup notificationsPopup = new NotificationsPopup();

    //private boolean[] notifications = new boolean[5];


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(R.style.ThemeColorEvenTalk);
        super.onCreate(savedInstanceState);
        binding = ActivityCreateEventBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        eventDao = AppDatabase.getDatabase(this.getApplicationContext()).eventDao();

        eventBuilder = new Event.EventBuilder();
        Button btnSaveEvent = findViewById(R.id.btnSaveEvent);
        ImageButton btnClose = findViewById(R.id.btnClose);
        Toolbar toolbar = findViewById(R.id.toolbar_close_or_save);

        setSupportActionBar(toolbar);
        toolbarHelper = new ToolbarHelper(this, this, getMenuInflater(), R.id.toolbar_close_or_save, false);
        toolbarHelper.setOnToolbarListener(this);
        notificationsPopup.setOnNotificationsPopupListener(this);

        List<View> toolbarElements = new ArrayList<>();
        toolbarElements.add(btnSaveEvent);
        toolbarElements.add(btnClose);
        toolbarHelper.setToolbarElements(toolbarElements);

        findViewById(R.id.event_start_hour).setOnClickListener(this);
        findViewById(R.id.event_end_hour).setOnClickListener(this);
        findViewById(R.id.event_end_date).setOnClickListener(this);
        findViewById(R.id.event_start_date).setOnClickListener(this);
        findViewById(R.id.event_notification_1).setOnClickListener(this);

    }


    private void saveAndQuit(){
        EditText editTitle = findViewById(R.id.event_title);
        EditText editDescription = findViewById(R.id.event_description);
        eventBuilder.setEventTitle(editTitle.getText().toString());
        eventBuilder.setEventDescription(editDescription.getText().toString());

        /**Gson gson = new Gson();
        String jsonEvent = gson.toJson(event);**/

        Event event = eventBuilder.build();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                eventDao.insert(event);
            }catch (Exception e){
                Log.i(TAG, "evento fallido -> " + event.getTitle());
            }
            Log.i(TAG, "evento guardado -> " + event.getTitle());
            Intent returnIntent = new Intent();
            returnIntent.putExtra("resultado", 67); // "resultado" es la clave y valor es el dato que deseas retornar
            setResult(Activity.RESULT_OK, returnIntent);
            finish();
        });
    }

    private void quit(){
        Intent returnIntent = new Intent();
        returnIntent.putExtra("resultado", 69); // "resultado" es la clave y valor es el dato que deseas retornar
        setResult(Activity.RESULT_CANCELED, returnIntent);
        finish();
    }


    private void openNotificationsPicker(){
        // todo expandir la layout de notificaciones, se puede hacer con un índice. Está al final de esta clase el código para hacerlo

        notificationsPopup.show(getSupportFragmentManager(), "popup_tag");
    }

    private void openHourPicker(int type){
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                        // Manejar la selección de la fecha aquí
                        Log.i(TAG, "onDateSet: " + hourOfDay + ":" + minute);
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                        calendar.set(Calendar.MINUTE,minute);
                        updateHourLabel(calendar, type);
                    }
                }, hour, minute, true);
        timePickerDialog.show();
    }

    private void openDatePicker(int type){
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                        // Manejar la selección de la fecha aquí
                        Log.i(TAG, "onDateSet: " + dayOfMonth + "/" + month + "/" + year);
                        calendar.set(Calendar.YEAR, year);
                        calendar.set(Calendar.MONTH,month);
                        calendar.set(Calendar.DAY_OF_MONTH,dayOfMonth);
                         updateDateLabel(calendar, type);
                    }
                }, year, month, day);
        datePickerDialog.show();
    }

    private void updateDateLabel(Calendar calendar, int type){
        String myFormat="dd/MM/yy";
        Log.i(TAG, "day and hour: "+new SimpleDateFormat("dd/MM/yyyy HH:mm").format(calendar.getTime()));
        SimpleDateFormat dateFormat=new SimpleDateFormat(myFormat, Locale.getDefault());
        if(type== START){
            eventBuilder.setEventStartDay(calendar);
            TextView eventStartDate = findViewById(R.id.event_start_date);
            eventStartDate.setText(dateFormat.format(calendar.getTime()));
        }else if(type == END){
            eventBuilder.setEventEndDay(calendar);
            TextView eventEndDate = findViewById(R.id.event_end_date);
            eventEndDate.setText(dateFormat.format(calendar.getTime()));
        }
    }

    private void updateHourLabel(Calendar calendar, int type){
        String myFormat="HH:mm";
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String time = sdf.format(calendar.getTime());
        Log.i(TAG, "time: "+ time);
        SimpleDateFormat dateFormat=new SimpleDateFormat(myFormat, Locale.getDefault());
        if(type== START){
            eventBuilder.setEventStartHour(calendar);
            TextView eventStartDate = findViewById(R.id.event_start_hour);
            eventStartDate.setText(dateFormat.format(calendar.getTime()));
        }else if(type == END){
            eventBuilder.setEventEndHour(calendar);
            TextView eventEndDate = findViewById(R.id.event_end_hour);
            eventEndDate.setText(dateFormat.format(calendar.getTime()));
        }
    }

    //Retorno del popup NotificationsPopup, guarda las notificaciones y coloca sus nuevas vistas
    @Override
    public void OnSetNotifications(boolean[] notificationsArray) {
        int countNotifications=0;
        for(int i=0;i<notificationsArray.length;i++){
            if(notificationsArray[i]){
                if(createdNotificationLayouts[i]==false) {
                    createdNotificationLayouts[i] = true;
                    addNotificationLayout(i, indexLayout + countNotifications);
                }
                countNotifications++;
            }
        }
        eventBuilder.setEventNotifications(notificationsArray);
    }

    private void addNotificationLayout(int indexNotification, int indexLayout){
        LinearLayout createEventLinearLayout = findViewById(R.id.create_event_linear_layout);
        View newView = LayoutInflater.from(this).inflate(R.layout.add_notification, null);
        TextView notificationText = newView.findViewById(R.id.event_notification_text);
        if(indexNotification==0)
            notificationText.setText("5 minutos antes");
        else if(indexNotification==1)
            notificationText.setText("10 minutos antes");
        else if(indexNotification==2)
            notificationText.setText("15 minutos antes");
        else if(indexNotification==3)
            notificationText.setText("30 minutos antes");
        else if(indexNotification==4)
            notificationText.setText("1 hora antes");
        else if(indexNotification==5)
            notificationText.setText("3 horas antes");
        else if(indexNotification==6)
            notificationText.setText("1 día antes");

        newView.findViewById(R.id.event_notification_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createdNotificationLayouts[indexNotification]=false;
                createEventLinearLayout.removeView(newView);
                eventBuilder.getNotifications()[indexNotification]=false;
            }
        });

        //createdNotificationViews[indexNotification]=newView;
        //newView.setOnClickListener(this);
        createEventLinearLayout.addView(newView, indexLayout);
    }

    @Override
    public void onToolbarLoaded(int result) {
        switch (result) {
            case CLOSE_AND_SAVE:
                saveAndQuit();
                break;
            case CLOSE:
                quit();
                break;
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.event_start_date:
                openDatePicker(START);
                break;
            case R.id.event_end_date:
                openDatePicker(END);
                break;
            case R.id.event_start_hour:
                openHourPicker(START);
                break;
            case R.id.event_end_hour:
                openHourPicker(END);
                break;
            case R.id.event_notification_1:
                openNotificationsPicker();
                break;
        }
    }


}
