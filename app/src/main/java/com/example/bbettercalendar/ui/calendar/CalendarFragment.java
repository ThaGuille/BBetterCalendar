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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.room.Room;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.configuration.Configuration;
import com.example.bbettercalendar.database.AppDatabase;
import com.example.bbettercalendar.databinding.FragmentCalendarBinding;
import com.example.bbettercalendar.events.AddEventActivity;
import com.example.bbettercalendar.events.Event;
import com.example.bbettercalendar.events.EventDao;
import com.example.bbettercalendar.helpers.OnToolBarListener;
import com.example.bbettercalendar.helpers.ScreenHelper;
import com.example.bbettercalendar.helpers.ToolbarHelper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

public class CalendarFragment extends Fragment implements OnToolBarListener, View.OnClickListener {

    @Inject
    Configuration config;
    private String TAG = "CalendarFragmentTag";

    private FragmentCalendarBinding binding;
    // Guillem -> això s'haurà de canviar per el valor estàtic que es crearà en una classe a part
    private float screenWidth;
    private float screenHeight;
    private Calendar calendar;
    private TextView calendarDayInitials;
    private final int daysMargin = 70;
    private OnToolBarListener onToolBarListener;
    private ToolbarHelper toolbarHelper;
    AppDatabase db;
    EventDao eventDao;

    private ActivityResultLauncher<Intent> someActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    handleOnActivityResult(result);
                }
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        CalendarViewModel calendarViewModel =
                new ViewModelProvider(this).get(CalendarViewModel.class);
        getActivity().setTheme(R.style.ThemeColorGreen);

        binding = FragmentCalendarBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        //db = Room.databaseBuilder(getActivity().getApplicationContext(), AppDatabase.class, "eventDB").build();
        eventDao = AppDatabase.getDatabase(getContext().getApplicationContext()).eventDao();

        final TextView textView = binding.textNotifications;
        calendarDayInitials = binding.calendarDayInitials;
        View[] calendarHorizontalLines = new View[6];
        View[] calendarVerticalLines = new View[6];
        calendar = Calendar.getInstance(Locale.getDefault());
        toolbarHelper = new ToolbarHelper(getContext(), getActivity(), getActivity().getMenuInflater(), R.menu.toolbar, true);
        toolbarHelper.setOnToolbarListener(this);
        binding.calendarAddEventButton.setOnClickListener(this);

        screenWidth = getResources().getDisplayMetrics().widthPixels;
        positionWeedDaysText();
        /**obtiene las líneas del calendario**/
        for(int i=0;i<6;i++){
            String lineName = "calendarHorizontalLine" + i;
            calendarHorizontalLines[i] = root.findViewById(getResources().getIdentifier(lineName, "id", getActivity().getPackageName()));
            lineName = "calendarVerticalLine" + i;
            calendarVerticalLines[i] = root.findViewById(getResources().getIdentifier(lineName, "id", getActivity().getPackageName()));
        }

        //heightPixels: 2482, rootHeight: 2314, top+bot: 230,   nav:142, status:88
        /**Cuando la vista carga, obtenemos su altura y posicionamos las líneas**/
        root.post(new Runnable() {
            @Override
            public void run() {
                screenHeight = root.getMeasuredHeight() - ScreenHelper.getNavigationBarHeight(getActivity()) - daysMargin;
                screenWidth = root.getMeasuredWidth();
                Log.i("month calendar", "root height: " + screenHeight);
                positionLines(calendarHorizontalLines, calendarVerticalLines);
            }
        });

        positionMonthDays();
        setTopMenu();


        calendarViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);

        //función similar para posicionar las líneas del calendario
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void setTopMenu() {
        getActivity().addMenuProvider(toolbarHelper, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    /**posiciona los días del mes**/
    private void positionMonthDays(){
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        //devolverá un valor entero que representa el día de la semana, donde 1 corresponde a Domingo, 2 a Lunes, y así sucesivamente hasta 7 para Sábado.
        //Recuerda que los meses en Calendar están indexados comenzando desde 0, por lo que Enero es 0, Febrero es 1, y así sucesivamente.

        //ahora hay que colocar un textview por cada día del mes con su numero, y posicionarlo en el lugar correcto. Los textViews se deberán poder reutilizar al cambiar de mes
        //para ello, se creará un array de textViews, y se irán reutilizando. Se creará un array de textViews para cada semana, y se irán reutilizando.
    }

    /**posiciona las líneas del calendario**/
    private void positionLines(View[] calendarHorizontalLines, View[] calendarVerticalLines) {
        float horizontalLineSeparation = screenHeight/6;
        float verticalLineSeparation = screenWidth/7;

        for(int i=0;i<6;i++){
            calendarHorizontalLines[i].setY(horizontalLineSeparation*(i) + daysMargin);
            calendarVerticalLines[i].setX(verticalLineSeparation*(i+1));
            calendarVerticalLines[i].setY(0);
        }
    }

    //Función para posicionar los días de la semana de manera uniforme a lo largo de screenWidth
    private void positionWeedDaysText(){
        int firstDayOfWeek = calendar.getFirstDayOfWeek();
        String dayInitials = "";
        if(firstDayOfWeek == 1){
            dayInitials = "SMTWTFS";
        }else if(firstDayOfWeek == 2){
            dayInitials ="MTWTFSU";
        }else if(firstDayOfWeek == 3) {
            dayInitials = "TWTFSUM";
        }
        SpannableStringBuilder spannableBuilder = new SpannableStringBuilder(dayInitials);

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

        /* Guillem:  cambiamos  int numberOfSpaces = dayInitials.length() - 1;

        for (int i = dayInitials.length(); i > -1; i--) {
            spannableBuilder.insert(i, " ");
            spannableBuilder.setSpan(new ScaleXSpan(scaleX - 1), i, i + 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        }*/

        calendarDayInitials.setText(spannableBuilder);
    }

    private void handleOnActivityResult(ActivityResult result) {
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
            Log.i(TAG,"dya y hora: "+ dateFormat.format(event.getStartDayAndHour().getTime()));
        else
            Log.i(TAG,"dya y hora: null");
        if(event.getDescription()!=null && !event.getDescription().isEmpty()){
            Log.i(TAG, "event description: "+event.getDescription());
        }
        Log.i(TAG, "-------------------------------------------");
    }

    @Override
    public void onToolbarLoaded(int result) {
        switch (result){
            case ToolbarHelper.FINISH:
                break;
            default:
                break;
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.calendarAddEventButton:
                addEvent();
                break;
            default:
                break;
        }
    }

    private void addEvent(){
        Intent intent = new Intent(getActivity(), AddEventActivity.class);
        someActivityResultLauncher.launch(intent);
    }
        //añadir evento
}