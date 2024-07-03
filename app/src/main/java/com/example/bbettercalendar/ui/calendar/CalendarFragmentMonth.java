package com.example.bbettercalendar.ui.calendar;

import android.app.ActionBar;
import android.content.Intent;
import android.os.Bundle;
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
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.configuration.Configuration;
import com.example.bbettercalendar.database.AppDatabase;
import com.example.bbettercalendar.databinding.FragmentCalendarMonthBinding;
import com.example.bbettercalendar.calendarEntries.AddEventActivity;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.helpers.OnToolBarListener;
import com.example.bbettercalendar.helpers.OnToolbarCalendarListener;
import com.example.bbettercalendar.helpers.ScreenHelper;
import com.example.bbettercalendar.helpers.ToolbarHelper;

import java.util.Calendar;
import java.util.Locale;

import javax.inject.Inject;

public class CalendarFragmentMonth extends Fragment implements OnToolBarListener, OnToolbarCalendarListener, View.OnClickListener {

    @Inject
    Configuration config;
    private String TAG = "CalendarFragmentTag";

    private FragmentCalendarMonthBinding binding;
    // Guillem -> això s'haurà de canviar per el valor estàtic que es crearà en una classe a part
    private float screenWidth;
    private float screenHeight;
    private Calendar calendar;
    private TextView calendarDayInitials;
    private final int daysMargin = 70;
    private OnToolBarListener onToolBarListener;
    private ToolbarHelper toolbarHelper;
    CalendarEntryDAO calendarEntryDAO;
    ActionBar actionBar;
    private CalendarController calendarController;

    private ActivityResultLauncher<Intent> someActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    calendarController.handleOnActivityResult(result);
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
        getActivity().setTheme(R.style.ThemeChatGPTBlue);

        binding = FragmentCalendarMonthBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        //db = Room.databaseBuilder(getActivity().getApplicationContext(), AppDatabase.class, "eventDB").build();
        calendarEntryDAO = AppDatabase.getDatabase(getContext().getApplicationContext()).eventDao();
        calendarController = new CalendarController(getActivity(), getContext());

        //final TextView textView = binding.text_notifications;
        calendarDayInitials = binding.calendarDayInitials;
        View[] calendarHorizontalLines = new View[6];
        View[] calendarVerticalLines = new View[6];
        calendar = Calendar.getInstance(Locale.getDefault());
        toolbarHelper = new ToolbarHelper(getContext(), getActivity(), getActivity().getMenuInflater(), R.menu.toolbar, true);
        toolbarHelper.setOnToolbarListener(this);
        toolbarHelper.setOnToolbarCalendarListener(this);
        binding.calendarAddEventButton.setOnClickListener(this);

        screenWidth = getResources().getDisplayMetrics().widthPixels;
        calendarController.positionWeekDaysText(calendarDayInitials, screenWidth, 0);
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


        //calendarViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);

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
                //replaceFragmentMonthForWeek();
                addEvent();
                break;
            default:
                break;
        }
    }

    //todo reactivar función
    private void addEvent(){
        Intent intent = new Intent(getActivity(), AddEventActivity.class);
        someActivityResultLauncher.launch(intent);
    }
        //añadir evento

    @Override
    public void switchFragment(){
        NavController navController = Navigation.findNavController(getActivity(), R.id.nav_host_fragment_activity_main);
        navController.navigate(R.id.action_navigation_calendar_month_to_navigation_calendar_week);
    }


}