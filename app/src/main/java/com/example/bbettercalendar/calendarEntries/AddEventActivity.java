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
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
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
import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.databinding.ActivityCreateEventBinding;
import com.example.bbettercalendar.helpers.FormatHelper;
import com.example.bbettercalendar.helpers.OnToolBarListener;
import com.example.bbettercalendar.helpers.ToolbarHelper;
import com.example.bbettercalendar.notifications.NotificationOffsets;
import com.example.bbettercalendar.notifications.event.EventReminderScheduler;
import com.example.bbettercalendar.popups.DescriptionPopup;
import com.example.bbettercalendar.popups.NotificationsPopup;
import com.example.bbettercalendar.popups.OnNotificationsPopupListener;
import com.example.bbettercalendar.popups.OnPopupListener;
import com.example.bbettercalendar.popups.PopupHelper;
import com.example.bbettercalendar.popups.RepetitionOptions;
import com.example.bbettercalendar.popups.RepetitionPopup;
import com.example.bbettercalendar.popups.RepetitionSpec;
import com.example.bbettercalendar.ui.common.InsetsHelper;
import com.example.bbettercalendar.ui.common.motion.Motion;
import com.example.bbettercalendar.ui.common.widget.SegmentedControlView;
import com.google.android.material.textfield.TextInputEditText;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;


@AndroidEntryPoint
public class AddEventActivity extends AppCompatActivity implements OnToolBarListener, OnNotificationsPopupListener, OnPopupListener<Object>,
        NumberPicker.OnValueChangeListener, View.OnClickListener{

    @Inject EventReminderScheduler eventReminderScheduler;
    @Inject @IoExecutor ExecutorService ioExecutor;


    public static final int CLOSE_AND_SAVE = 1;
    public static final int CLOSE = 2;
    private final int START = 10;
    private final int END = 11;

    public static final int TYPE_EVENT = 1;
    public static final int TYPE_TASK = 2;
    public static final int TYPE_NOTIFICATION = 3;

    public static final String EXTRA_PRESELECTED_DATE_MILLIS = "preselected_date_millis";
    // Título tecleado en el quick-add de Home antes de saltar a "More options" (spec tasks-home-today).
    public static final String EXTRA_PREFILL_TITLE = "prefill_title";
    // Proyecto dueño del item cuando se llega aquí desde el "More options" del quick-add de un
    // proyecto (spec projects-mvp): sin esto la tarea se guardaba suelta, fuera del proyecto.
    public static final String EXTRA_PROJECT_ID = "project_id";
    private final String TAG = "AddEventActivityTag";

    private boolean[] createdNotificationLayouts = new boolean[7];
    private View[] createdNotificationViews = new View[7];
    private int indexLayout=0;

    private ActivityCreateEventBinding binding;
    private ToolbarHelper toolbarHelper;
    @Inject CalendarEntryDAO calendarEntryDAO;
    private Handler handler;
    private Runnable runnableMinutes;
    private Runnable runnableHours;

    private CalendarEntry.EventBuilder eventBuilder;
    // Recurrencia elegida en el RepetitionPopup (spec tasks-recurrence). Sólo aplica a TYPE_TASK.
    private RepetitionSpec repetitionSpec = RepetitionSpec.none();
    private Calendar localCalendar = Calendar.getInstance();
    private NotificationsPopup notificationsPopup = new NotificationsPopup();
    private RepetitionPopup repetitionPopup = new RepetitionPopup();
    private DescriptionPopup descriptionPopup = new DescriptionPopup();
    private int layoutType;
    // 0 = alta normal; si no, el proyecto al que pertenece la entrada creada.
    private int projectId = 0;

    private EditText titleView;
    private TextView descriptionView;
    private TextView startDateView;
    private TextView startTimeView;
    private TextView endDateView;
    private TextView endTimeView;
    private TextView notificationsView = null;  //todo IMPORTANT cambiar View por Layout cuando haga falta
    private LinearLayout repetitionView = null;
    private TextView repetitionTextView = null;
    private LinearLayout descriptionLayout = null;
    private NumberPicker numberPickerMinutesView;
    private NumberPicker numberPickerHoursView;
    private LinearLayout createEntryLinearLayout;
    private LinearLayout optionsCard;
    private SegmentedControlView formTypeSelector;
    private Button saveButton;
    private TextView formErrorView;
    // u14: fecha y hora del formulario (la fuente de verdad); el builder las recibe al guardar.
    // Un evento nuevo dura 1 h por defecto, asi la fila "Termina" nunca esta vacia.
    private Calendar formStart;
    private Calendar formEnd;

    //private boolean[] notifications = new boolean[5];


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        // Tema Theme.BBetter.NoActionBar desde el manifest (u5: sin setTheme en ejecución).
        super.onCreate(savedInstanceState);
        InsetsHelper.enableEdgeToEdge(this);

        Intent intent = getIntent();
        layoutType = intent.getIntExtra("entry", TYPE_EVENT); //todo algo esta mal con esto

        // Apply preselected date (e.g. from "Add for this day" in the month view)
        // before initializeComponents() reads localCalendar to populate the date fields.
        long preselectedMillis = intent.getLongExtra(EXTRA_PRESELECTED_DATE_MILLIS, -1L);
        if (preselectedMillis > 0L) {
            localCalendar.setTimeInMillis(preselectedMillis);
        }
        localCalendar.set(Calendar.SECOND, 0);
        localCalendar.set(Calendar.MILLISECOND, 0);
        formStart = (Calendar) localCalendar.clone();
        formEnd = (Calendar) localCalendar.clone();
        formEnd.add(Calendar.HOUR_OF_DAY, 1);
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

        InsetsHelper.applyToContent(this);

        // Inicializar componentes comunes y específicos del layout
        eventBuilder = new CalendarEntry.EventBuilder();
        toolbarHelper = new ToolbarHelper(this, this, getMenuInflater(), R.id.toolbar_close_or_save, false);
        toolbarHelper.setOnToolbarListener(this);
        initializeComponents(layoutType);

        projectId = intent.getIntExtra(EXTRA_PROJECT_ID, 0);

        String prefillTitle = intent.getStringExtra(EXTRA_PREFILL_TITLE);
        if (prefillTitle != null && !prefillTitle.isEmpty() && titleView != null) {
            titleView.setText(prefillTitle);
        }

        //binding = ActivityCreateEventBinding.inflate(getLayoutInflater());
        //setContentView(binding.getRoot();

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
                    Toast.makeText(getApplication(), getString(R.string.picker_selected_format, selectedValue), Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(getApplication(), getString(R.string.picker_selected_format, selectedValue), Toast.LENGTH_SHORT).show();
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
        saveButton = btnSaveEvent;
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
                repetitionTextView = null;
                descriptionLayout = findViewById(R.id.event_description_layout);
                createEntryLinearLayout = findViewById(R.id.create_event_linear_layout);
                optionsCard = findViewById(R.id.options_card);

                startTimeView.setOnClickListener(this);
                endTimeView.setOnClickListener(this);
                endDateView.setOnClickListener(this);
                startDateView.setOnClickListener(this);
                notificationsView.setOnClickListener(this);
                descriptionLayout.setOnClickListener(this);
                // Position inside options_card where new notification rows are inserted:
                // 0=event_notifications_layout, 1=divider, 2=event_description_layout.
                // Insert at 1 so injected rows sit between the notifications row and the divider.
                indexLayout=1;

                eventBuilder.setEventType(TYPE_EVENT);

                toolbarTitle.setText(R.string.event);

                break;
            case TYPE_TASK:
                // Position inside options_card where new notification rows are inserted:
                // 0=task_repetition_layout, 1=divider, 2=task_notifications_layout,
                // 3=divider, 4=task_description_layout.
                // Insert at 3 so injected rows sit between the notifications row and the divider.
                indexLayout=3;
                titleView = findViewById(R.id.task_title);
                descriptionView = findViewById(R.id.task_description);
                startDateView = findViewById(R.id.task_start_date);
                startTimeView = findViewById(R.id.task_start_hour);

                endDateView = null;
                endTimeView = null;
                notificationsView = findViewById(R.id.task_notification_1);
                repetitionView = findViewById(R.id.task_repetition_layout);
                repetitionTextView = findViewById(R.id.task_repetition_text);
                repetitionTextView.setText(RepetitionOptions.labelResIdFor(eventBuilder.getEventRepetition()));
                descriptionLayout = findViewById(R.id.task_description_layout);
                createEntryLinearLayout = findViewById(R.id.create_task_linear_layout);
                optionsCard = findViewById(R.id.options_card);
                numberPickerMinutesView = findViewById(R.id.number_picker_minutes);
                numberPickerHoursView = findViewById(R.id.number_picker_hours);

                startTimeView.setOnClickListener(this);
                startDateView.setOnClickListener(this);
                notificationsView.setOnClickListener(this);
                repetitionView.setOnClickListener(this);
                descriptionLayout.setOnClickListener(this);

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
        setUpFormChrome();
    }

    /** u14: selector con píldora, atajos de fecha, error en línea y "Guardar" según el validador. */
    private void setUpFormChrome() {
        formTypeSelector = findViewById(R.id.form_type_selector);
        formTypeSelector.setItemEnabled(2, false); // Recordatorio: aún no existe
        formTypeSelector.setSelectedIndex(layoutType == TYPE_TASK ? 1 : 0);
        formTypeSelector.setOnSegmentSelectedListener((view, index) -> {
            final int target = index == 1 ? TYPE_TASK : TYPE_EVENT;
            if (target == layoutType) return;
            // Se deja correr el deslizamiento de la píldora antes de re-inflar el formulario.
            long delay = Motion.enabled(this) ? 280L : 0L;
            view.postDelayed(() -> {
                if (!isFinishing() && target != layoutType) switchLayout(target);
            }, delay);
        });

        formErrorView = findViewById(R.id.form_error);
        findViewById(R.id.form_shortcut_today).setOnClickListener(v -> applyShortcut(0));
        findViewById(R.id.form_shortcut_tomorrow).setOnClickListener(v -> applyShortcut(1));
        findViewById(R.id.form_shortcut_plus_hour).setOnClickListener(v -> applyShortcut(-1));

        titleView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { renderForm(); }
        });
        renderForm();
    }

    /** 0 = hoy, 1 = mañana (fecha de inicio); -1 = inicio dentro de 1 h. Un evento conserva su duración. */
    private void applyShortcut(int daysFromToday) {
        long duration = Math.max(0L, formEnd.getTimeInMillis() - formStart.getTimeInMillis());
        Calendar now = Calendar.getInstance();
        if (daysFromToday < 0) {
            now.add(Calendar.HOUR_OF_DAY, 1);
            now.set(Calendar.SECOND, 0);
            now.set(Calendar.MILLISECOND, 0);
            formStart.setTimeInMillis(now.getTimeInMillis());
        } else {
            now.add(Calendar.DAY_OF_MONTH, daysFromToday);
            formStart.set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        }
        formEnd.setTimeInMillis(formStart.getTimeInMillis() + duration);
        renderForm();
    }

    /** Pinta fechas y horas, muestra el error de fin anterior al inicio y habilita "Guardar". */
    private void renderForm() {
        if (startDateView == null || formStart == null) return;
        startDateView.setText(formatDate(formStart));
        startTimeView.setText(formatTime(formStart));
        boolean hasEnd = endDateView != null && endTimeView != null;
        if (hasEnd) {
            endDateView.setText(formatDate(formEnd));
            endTimeView.setText(formatTime(formEnd));
        }
        EventFormValidator.Result result = EventFormValidator.validate(
                titleView != null ? titleView.getText().toString() : "",
                hasEnd, formStart.getTimeInMillis(), formEnd.getTimeInMillis());
        if (formErrorView != null) {
            formErrorView.setVisibility(result.endBeforeStart ? View.VISIBLE : View.GONE);
        }
        if (saveButton != null) {
            saveButton.setEnabled(result.canSave());
            saveButton.setAlpha(result.canSave() ? 1f : 0.4f);
        }
    }

    private String formatDate(Calendar c) {
        return DateUtils.formatDateTime(this, c.getTimeInMillis(),
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_ALL);
    }

    private String formatTime(Calendar c) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(c.getTime());
    }


    private void saveAndQuit(){
        eventBuilder.setEventTitle(titleView.getText().toString());
        if (projectId != 0) {
            eventBuilder.setEventProjectId(projectId);
        }
        //eventBuilder.setEventDescription(editDescription.getText().toString());

        // Si el usuario nunca abrió el date picker el builder no tiene fecha y startMillis
        // quedaría a 0 — invisible para todas las queries por rango (mes del calendario y
        // lista de Home). Por defecto: la fecha que ya muestra el formulario (localCalendar,
        // preseleccionada o "ahora").
        eventBuilder.setEventStartDayAndHour((Calendar) formStart.clone());
        if (layoutType == TYPE_EVENT) {
            eventBuilder.setEventEndDayAndHour((Calendar) formEnd.clone());
        }

        if(layoutType==AddEventActivity.TYPE_TASK){
            if(numberPickerMinutesView.getValue()!=0 || numberPickerHoursView.getValue()!=0) {
                int hour = Integer.parseInt ((numberPickerHoursView.getDisplayedValues())[numberPickerHoursView.getValue()]);
                int minute = Integer.parseInt ((numberPickerMinutesView.getDisplayedValues())[numberPickerMinutesView.getValue()]);
                eventBuilder.setEventDuration(minute * 60000 + hour * 3600000);
            }
            // Recurrencia (spec tasks-recurrence): una tarea que se repite se guarda como PLANTILLA;
            // sus ocurrencias (con sus propios recordatorios) las crea el materializador, no esta fila.
            if (repetitionSpec != null && repetitionSpec.repeats()) {
                eventBuilder.setEventRepetition(repetitionSpec.repetition);
                eventBuilder.setEventRepetitionInterval(repetitionSpec.interval);
                eventBuilder.setEventRepetitionDays(repetitionSpec.daysMask);
                eventBuilder.setEventIsTemplate(true);
                // La plantilla lleva el flag; las ocurrencias lo heredan en el materializador
                // (spec recurrence-calendar-visibility).
                eventBuilder.setEventHiddenInCalendar(repetitionSpec.hidesFromCalendar());
            }
        }
        /**Gson gson = new Gson();
        String jsonEvent = gson.toJson(event);**/

        CalendarEntry calendarEntry = eventBuilder.build();
        boolean isTemplate = calendarEntry.isTemplate();
        ioExecutor.execute(() -> {
            try {
                long rowId = calendarEntryDAO.insert(calendarEntry);
                calendarEntry.setId((int) rowId);
                if (isTemplate) {
                    new RecurrenceMaterializer(getApplicationContext(), calendarEntryDAO)
                            .materializeTemplate((int) rowId);
                } else {
                    eventReminderScheduler.scheduleFor(calendarEntry);
                }
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
        repetitionPopup.setInitialSpec(repetitionSpec);
        repetitionPopup.show(getSupportFragmentManager(), "popup_tag");
    }

    @Override
    public void OnClosePopup(int type, Object value) {
        try {
            switch (type){
                case PopupHelper.REPETITION_POPUP:
                    repetitionSpec = (RepetitionSpec) value;
                    eventBuilder.setEventRepetition(repetitionSpec.repetition);
                    eventBuilder.setEventRepetitionInterval(repetitionSpec.interval);
                    eventBuilder.setEventRepetitionDays(repetitionSpec.daysMask);
                    if (repetitionTextView != null) {
                        repetitionTextView.setText(repetitionSpec.describe(this));
                    }
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
        final Calendar target = type == START ? formStart : formEnd;
        TimePickerDialog timePickerDialog = new TimePickerDialog(this, R.style.Theme_BBetter_PickerDialog,
                (view, hourOfDay, minute) -> {
                    Log.i(TAG, "onTimeSet: " + hourOfDay + ":" + minute);
                    long duration = Math.max(0L, formEnd.getTimeInMillis() - formStart.getTimeInMillis());
                    target.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    target.set(Calendar.MINUTE, minute);
                    if (type == START) {
                        formEnd.setTimeInMillis(formStart.getTimeInMillis() + duration);
                    }
                    renderForm();
                }, target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE), true);
        timePickerDialog.show();
    }

    private void openDatePicker(int type){
        final Calendar target = type == START ? formStart : formEnd;
        DatePickerDialog datePickerDialog = new DatePickerDialog(this, R.style.Theme_BBetter_PickerDialog,
                (view, year, month, dayOfMonth) -> {
                    Log.i(TAG, "onDateSet: " + dayOfMonth + "/" + month + "/" + year);
                    long duration = Math.max(0L, formEnd.getTimeInMillis() - formStart.getTimeInMillis());
                    target.set(year, month, dayOfMonth);
                    if (type == START) {
                        // El fin acompaña al inicio conservando la duración.
                        formEnd.setTimeInMillis(formStart.getTimeInMillis() + duration);
                    }
                    renderForm();
                }, target.get(Calendar.YEAR), target.get(Calendar.MONTH), target.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.show();
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
        notificationText.setText(NotificationOffsets.labelResIdFor(indexNotification));

        newView.findViewById(R.id.event_notification_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createdNotificationLayouts[indexNotification]=false;
                optionsCard.removeView(newView);
                eventBuilder.getEventNotifications()[indexNotification]=false;
            }
        });

        // Insert into the Options card just under the existing notifications row.
        optionsCard.addView(newView, indexLayout);
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
        renderForm();


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
