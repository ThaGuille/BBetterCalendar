package com.example.bbettercalendar.calendarEntries;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.widget.Toolbar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bbettercalendar.MainActivity;
import com.example.bbettercalendar.R;
import com.example.bbettercalendar.configuration.Configuration;
import com.example.bbettercalendar.database.AppDatabase;
import com.example.bbettercalendar.databinding.ActivityCreateEventBinding;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.example.bbettercalendar.helpers.OnToolBarListener;
import com.example.bbettercalendar.helpers.ToolbarHelper;
import com.example.bbettercalendar.popups.DescriptionPopup;
import com.example.bbettercalendar.popups.NotificationsPopup;
import com.example.bbettercalendar.popups.OnNotificationsPopupListener;
import com.example.bbettercalendar.popups.OnPopupListener;
import com.example.bbettercalendar.popups.PopupHelper;
import com.example.bbettercalendar.popups.RepetitionPopup;
import com.google.android.material.textfield.TextInputEditText;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class AddEventActivity extends AppCompatActivity implements OnToolBarListener, OnNotificationsPopupListener, OnPopupListener<Object>,
        NumberPicker.OnValueChangeListener, View.OnClickListener{


    public static final int CLOSE_AND_SAVE = 1;
    public static final int CLOSE = 2;
    private final int START = 10;
    private final int END = 11;

    public static final int TYPE_EVENT = 1;
    public static final int TYPE_TASK = 2;
    public static final int TYPE_NOTIFICATION = 3;

    public static final String EXTRA_PRESELECTED_DATE_MILLIS = "preselected_date_millis";
    private final String TAG = "AddEventActivityTag";

    private boolean[] createdNotificationLayouts = new boolean[7];
    private View[] createdNotificationViews = new View[7];
    private int indexLayout=0;

    private ActivityCreateEventBinding binding;
    private ToolbarHelper toolbarHelper;
    CalendarEntryDAO calendarEntryDAO;
    private Handler handler;
    private Runnable runnableMinutes;
    private Runnable runnableHours;

    private CalendarEntry.EventBuilder eventBuilder;
    private Calendar localCalendar = Calendar.getInstance();
    private NotificationsPopup notificationsPopup = new NotificationsPopup();
    private RepetitionPopup repetitionPopup = new RepetitionPopup();
    private DescriptionPopup descriptionPopup = new DescriptionPopup();
    private int layoutType;

    private EditText titleView;
    private TextView descriptionView;
    private TextView startDateView;
    private TextView startTimeView;
    private TextView endDateView;
    private TextView endTimeView;
    private TextView notificationsView = null;  //todo IMPORTANT cambiar View por Layout cuando haga falta
    private LinearLayout repetitionView = null;
    private LinearLayout descriptionLayout = null;
    private NumberPicker numberPickerMinutesView;
    private NumberPicker numberPickerHoursView;
    private LinearLayout createEntryLinearLayout;
    private Button switchToEventButton;
    private Button switchToTaskButton;

    //private boolean[] notifications = new boolean[5];


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(R.style.ThemeChatGPTBlue_NoActionBar);
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        layoutType = intent.getIntExtra("entry", TYPE_EVENT); //todo algo esta mal con esto

        // Apply preselected date (e.g. from "Add for this day" in the month view)
        // before initializeComponents() reads localCalendar to populate the date fields.
        long preselectedMillis = intent.getLongExtra(EXTRA_PRESELECTED_DATE_MILLIS, -1L);
        if (preselectedMillis > 0L) {
            localCalendar.setTimeInMillis(preselectedMillis);
        }
        // Cargar el layout adecuado
        switch (layoutType) {
            case TYPE_EVENT:
                setContentView(R.layout.activity_create_event);
                break;
            case TYPE_TASK:
                setContentView(R.layout.activity_create_task);
                break;
            case TYPE_NOTIFICATION:
                setContentView(R.layout.activity_create_event); //todo cambiar a layout de notificaciones
                break;
            default:
                setContentView(R.layout.activity_create_event);
                break;
        }

        // Inicializar componentes comunes y específicos del layout
        eventBuilder = new CalendarEntry.EventBuilder();
        toolbarHelper = new ToolbarHelper(this, this, getMenuInflater(), R.id.toolbar_close_or_save, false);
        toolbarHelper.setOnToolbarListener(this);
        initializeComponents(layoutType);

        // For TYPE_EVENT with a preselected date, default the end date to the same day so the
        // event spans a single day by default. The start date was already populated from
        // localCalendar inside initializeComponents.
        if (preselectedMillis > 0L && layoutType == TYPE_EVENT) {
            eventBuilder.setEventEndDay(localCalendar);
            if (endDateView != null) {
                endDateView.setText(java.text.DateFormat.getDateInstance(
                        java.text.DateFormat.SHORT, java.util.Locale.getDefault())
                        .format(localCalendar.getTime()));
            }
        }

        //binding = ActivityCreateEventBinding.inflate(getLayoutInflater());
        //setContentView(binding.getRoot();

        calendarEntryDAO = AppDatabase.getDatabase(this.getApplicationContext()).eventDao();

        /**Button btnSaveEvent = findViewById(R.id.btnSaveEvent);
        ImageButton btnClose = findViewById(R.id.btnClose);
        Toolbar toolbar = findViewById(R.id.toolbar_close_or_save);

        setSupportActionBar(toolbar);**/

        notificationsPopup.setOnNotificationsPopupListener(this);
        repetitionPopup.setOnPopupListener(this);
        descriptionPopup.setOnPopupListener(this);

        handler = new Handler(Looper.getMainLooper());

        // Configurar valores mínimos y máximos

        // Configurar un array de valores a mostrar

    }

    @Override
    public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
        // Remover cualquier llamada pendiente a runnable
        if (picker == numberPickerMinutesView) {
            handler.removeCallbacks(runnableMinutes);

            // Crear un nuevo runnable
            runnableMinutes = new Runnable() {
                @Override
                public void run() {
                    // Hacer algo cuando el usuario se detiene
                    String[] displayedValues = numberPickerMinutesView.getDisplayedValues();
                    String selectedValue = displayedValues[newVal];
                    // Por ejemplo, puedes mostrar el valor seleccionado en un Toast
                    Toast.makeText(getApplication(), "Selected: " + selectedValue, Toast.LENGTH_SHORT).show();
                }
            };

            // Ejecutar el runnable con un retraso de 500 ms
            handler.postDelayed(runnableMinutes, 1000);
        }
        else if (picker == numberPickerHoursView) {
            handler.removeCallbacks(runnableHours);

            runnableHours = new Runnable() {
                @Override
                public void run() {
                    String[] displayedValues = numberPickerHoursView.getDisplayedValues();
                    String selectedValue = displayedValues[newVal];
                    Toast.makeText(getApplication(), "Selected: " + selectedValue, Toast.LENGTH_SHORT).show();
                }
            };

            handler.postDelayed(runnableHours, 1000);
        }
    }

    //se puede hacer con ifs para juntar codigo común entre varios
    private void initializeComponents(int layoutType) {
        this.layoutType = layoutType;
        eventBuilder.setEventType(layoutType);
        Button btnSaveEvent = findViewById(R.id.btnSaveEvent);
        ImageButton btnClose = findViewById(R.id.btnClose);
        TextView toolbarTitle = findViewById(R.id.toolbarText);
        Toolbar toolbar = findViewById(R.id.toolbar_close_or_save);
        setSupportActionBar(toolbar);
        List<View> toolbarElements = new ArrayList<>();
        toolbarElements.add(btnSaveEvent);
        toolbarElements.add(btnClose);

        toolbarHelper.setToolbarElements(toolbarElements);
        // Aquí puedes inicializar componentes comunes y específicos de cada layout
        switch (layoutType) {
            case TYPE_EVENT:
                titleView = findViewById(R.id.event_title);
                descriptionView = findViewById(R.id.event_description);
                startDateView = findViewById(R.id.event_start_date);
                startTimeView = findViewById(R.id.event_start_hour);
                endDateView = findViewById(R.id.event_end_date);
                endTimeView = findViewById(R.id.event_end_hour);
                notificationsView = findViewById(R.id.event_notification_1);
                numberPickerMinutesView = null;
                numberPickerHoursView = null;
                repetitionView = null;
                descriptionLayout = findViewById(R.id.event_description_layout);
                createEntryLinearLayout = findViewById(R.id.create_event_linear_layout);
                switchToEventButton = findViewById(R.id.switch_to_event_button);
                switchToTaskButton = findViewById(R.id.switch_to_task_button);

                startTimeView.setOnClickListener(this);
                endTimeView.setOnClickListener(this);
                endDateView.setOnClickListener(this);
                startDateView.setOnClickListener(this);
                notificationsView.setOnClickListener(this);
                switchToEventButton.setOnClickListener(this);
                switchToTaskButton.setOnClickListener(this);
                indexLayout=5;

                eventBuilder.setEventType(TYPE_EVENT);

                startDateView.setText(DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(localCalendar.getTime()));
                startTimeView.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(localCalendar.getTime()));
                toolbarTitle.setText(R.string.event);

                break;
            case TYPE_TASK:
                indexLayout=12;
                titleView = findViewById(R.id.task_title);
                descriptionView = findViewById(R.id.task_description);
                startDateView = findViewById(R.id.task_start_date);
                startTimeView = findViewById(R.id.task_start_hour);

                endDateView = null;
                endTimeView = null;
                notificationsView = findViewById(R.id.task_notification_1);
                repetitionView = findViewById(R.id.task_repetition_layout);
                descriptionLayout = findViewById(R.id.task_description_layout);
                createEntryLinearLayout = findViewById(R.id.create_task_linear_layout);
                numberPickerMinutesView = findViewById(R.id.number_picker_minutes);
                numberPickerHoursView = findViewById(R.id.number_picker_hours);
                switchToEventButton = findViewById(R.id.switch_to_event_button);
                switchToTaskButton = findViewById(R.id.switch_to_task_button);

                startTimeView.setOnClickListener(this);
                startDateView.setOnClickListener(this);
                notificationsView.setOnClickListener(this);
                repetitionView.setOnClickListener(this);
                descriptionLayout.setOnClickListener(this);
                switchToEventButton.setOnClickListener(this);
                switchToTaskButton.setOnClickListener(this);

                startDateView.setText(DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(localCalendar.getTime()));
                startTimeView.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(localCalendar.getTime()));
                String[] displayedValuesMinutes = {"00", "55", "50", "45", "40", "35", "30", "25", "20", "15", "10", "05"};
                numberPickerMinutesView.setMinValue(0);
                numberPickerMinutesView.setMaxValue(displayedValuesMinutes.length - 1);
                numberPickerMinutesView.setDisplayedValues(displayedValuesMinutes);
                numberPickerMinutesView.setOnValueChangedListener(this);

                String[] displayedValuesHours = {"00", "24", "20", "16", "12", "11", "10", "09", "08", "07", "06", "05", "04", "03", "02", "01"};
                numberPickerHoursView.setMinValue(0);
                numberPickerHoursView.setMaxValue(displayedValuesHours.length - 1);
                numberPickerHoursView.setDisplayedValues(displayedValuesHours);
                numberPickerHoursView.setOnValueChangedListener(this);
                toolbarTitle.setText(R.string.task);
                
                eventBuilder.setEventType(TYPE_TASK);
                break;
            case TYPE_NOTIFICATION:
                // Inicializar componentes específicos del layout 3
                break;
        }
    }


    private void saveAndQuit(){
        eventBuilder.setEventTitle(titleView.getText().toString());
        //eventBuilder.setEventDescription(editDescription.getText().toString());

        if(layoutType==AddEventActivity.TYPE_TASK){
            if(numberPickerMinutesView.getValue()!=0 || numberPickerHoursView.getValue()!=0) {
                int hour = Integer.parseInt ((numberPickerHoursView.getDisplayedValues())[numberPickerHoursView.getValue()]);
                int minute = Integer.parseInt ((numberPickerMinutesView.getDisplayedValues())[numberPickerMinutesView.getValue()]);
                eventBuilder.setEventDuration(minute * 60000 + hour * 3600000);
            }
        }
        /**Gson gson = new Gson();
        String jsonEvent = gson.toJson(event);**/

        CalendarEntry calendarEntry = eventBuilder.build();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                calendarEntryDAO.insert(calendarEntry);
            }catch (Exception e){
                Log.i(TAG, "evento fallido -> " + calendarEntry.getTitle());
            }
            Log.i(TAG, "evento guardado -> " + calendarEntry.getTitle());
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

    private void openRepetitionPicker(){
        repetitionPopup.show(getSupportFragmentManager(), "popup_tag");
    }

    @Override
    public void OnClosePopup(int type, Object value) {
        try {
            switch (type){
                case PopupHelper.REPETITION_POPUP:
                    int repetition = (Integer) value;
                    eventBuilder.setEventRepetition(repetition);
                    break;
                case PopupHelper.DESCRIPTION_POPUP:
                    String description = (String) value;
                    eventBuilder.setEventDescription(description);
                    descriptionView.setText(description);
                    break;
            }
        }catch (ClassCastException e){
            e.printStackTrace();
            Log.e(TAG, "error -> " + e.getMessage());
        }
    }
    @Override
    public void OnClosePopup(int type) {
    }

    private void openHourPicker(int type){
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(this, R.style.ThemeChatGPTBlue_AndroidPopups,
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

        DatePickerDialog datePickerDialog = new DatePickerDialog(this, R.style.ThemeChatGPTBlue_AndroidPopups,
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
        if(type== START) {
            eventBuilder.setEventStartDay(calendar);
            startDateView.setText(DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(calendar.getTime()));
            //startDateView.setText(dateFormat.format(calendar.getTime()));
        }else if(type == END){
            eventBuilder.setEventEndDay(calendar);
            if(endDateView!=null)
                endDateView.setText(FormatHelper.formatDateToDateString(calendar));
        }
    }

    private void updateHourLabel(Calendar calendar, int type){
        String myFormat="HH:mm";
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String time = sdf.format(calendar.getTime());
        Log.i(TAG, "time: "+ time);
        SimpleDateFormat dateFormat=new SimpleDateFormat(myFormat, Locale.getDefault());
        if(type== START) {
            eventBuilder.setEventStartHour(calendar);
            startTimeView.setText(dateFormat.format(calendar.getTime()));
        }else if(type == END){
            eventBuilder.setEventEndHour(calendar);
            if(endTimeView!=null)
                endTimeView.setText(dateFormat.format(calendar.getTime()));
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
                createEntryLinearLayout.removeView(newView);
                eventBuilder.getEventNotifications()[indexNotification]=false;
            }
        });

        //Añadir la vista al layout general, en la posición indexLayout que cambia entre evento y tarea
        createEntryLinearLayout.addView(newView, indexLayout);
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
        int id = view.getId();
        if (id == R.id.event_start_date || id == R.id.task_start_date) {
            openDatePicker(START);
        } else if (id == R.id.event_start_hour || id == R.id.task_start_hour) {
            openHourPicker(START);
        } else if (id == R.id.event_end_date) {
            openDatePicker(END);
        } else if (id == R.id.event_end_hour) {
            openHourPicker(END);
        } else if (id == R.id.switch_to_event_button) {
            switchLayout(TYPE_EVENT);
        } else if (id == R.id.switch_to_task_button) {
            switchLayout(TYPE_TASK);
        }

        if(view.getId()==notificationsView.getId()){
            openNotificationsPicker();
        }
        else if (repetitionView!=null && view.getId()==repetitionView.getId()){
            openRepetitionPicker();
        }
        else if (view.getId()==descriptionLayout.getId()){
            descriptionPopup.show(getSupportFragmentManager(), "popup_tag");
        }
    }

    private void switchEventAndTask(int previousLayout) {
        // En la layout de evento, deben mostrarse con los datos de la tarea las siguientes variables:
         //title, description, startDate, startTime, notifications;

        if (eventBuilder.getEventTitle()!=null)
            titleView.setText(eventBuilder.getEventTitle());
        if (eventBuilder.getEventDescription()!=null)
            descriptionView.setText(eventBuilder.getEventDescription());
        if(eventBuilder.getEventStartDayAndHour()!=null && eventBuilder.getEventStartDayAndHour().isSet(Calendar.DAY_OF_MONTH))  //todo error per aqui
            startDateView.setText(FormatHelper.formatDateToDateString(eventBuilder.getEventStartDayAndHour()));
        if(eventBuilder.getEventStartDayAndHour() !=null && eventBuilder.getEventStartDayAndHour().isSet(Calendar.HOUR_OF_DAY))
            startTimeView.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(eventBuilder.getEventStartDayAndHour().getTime()));


        //todo guillem: probablemente haya un fallo si la pantalla ya tenía layouts de notificaciones. Habrá que eliminarlas y recrearlas al cambiar.
        if(eventBuilder.getEventNotifications()!=null) {
            for(int i=0;i<createdNotificationLayouts.length;i++){
                createdNotificationLayouts[i]=false;
            }
            OnSetNotifications(eventBuilder.getEventNotifications());
        }
    }


    private void switchLayout(int layoutType) {
        eventBuilder.setEventTitle(titleView.getText().toString());

        switch (layoutType) {
            case TYPE_EVENT:
                setContentView(R.layout.activity_create_event);
                initializeComponents(TYPE_EVENT);
                switchEventAndTask(this.layoutType);
                break;
            case TYPE_TASK:
                setContentView(R.layout.activity_create_task);
                initializeComponents(TYPE_TASK);
                switchEventAndTask(this.layoutType);
                break;
            /*case TYPE_NOTIFICATION:
                setContentView(R.layout.activity_add_event_type3);
                break;*/
        }
        Log.i(TAG, "Switched layouts");
    }



}
