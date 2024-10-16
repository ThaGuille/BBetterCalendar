package com.example.bbettercalendar.ui.calendar;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.database.AppDatabase;
import com.example.bbettercalendar.databinding.FragmentCalendarWeekBinding;
import com.example.bbettercalendar.calendarEntries.AddEventActivity;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.calendarEntries.CalendarEntryDAO;
import com.example.bbettercalendar.helpers.OnToolBarListener;
import com.example.bbettercalendar.helpers.OnToolbarCalendarListener;
import com.example.bbettercalendar.helpers.ScreenHelper;
import com.example.bbettercalendar.helpers.ToolbarHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;


public class CalendarFragmentWeek extends Fragment implements View.OnClickListener, OnToolBarListener, OnToolbarCalendarListener {

    private String TAG = "CalendarFragmentTag";

    private FragmentCalendarWeekBinding binding;
    // Guillem -> això s'haurà de canviar per el valor estàtic que es crearà en una classe a part
    private float screenWidth;
    private float screenHeight;
    private Calendar calendar;
    private TextView calendarDayInitials;
    private final int daysMargin = 70;
    private OnToolBarListener onToolBarListener;
    private ToolbarHelper toolbarHelper;
    private CalendarEntryDAO calendarEntryDAO;
    private CalendarController calendarController;

    private RecyclerView recyclerView;
    private static CalendarWeekAdapter adapter;
    private static List<CalendarEntry> mCalendarEntryList = new ArrayList<>();

    View[] calendarVerticalLines = new View[6];
    List<View> calendarHorizontalLines = new ArrayList<>();
    private int hourInterval = 2;

    private ActivityResultLauncher<Intent> someActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    //todo método común-> handleOnActivityResult(result);
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

        binding = FragmentCalendarWeekBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        //db = Room.databaseBuilder(getActivity().getApplicationContext(), AppDatabase.class, "eventDB").build();
        calendarEntryDAO = AppDatabase.getDatabase(getContext().getApplicationContext()).eventDao();
        calendarController = new CalendarController(getActivity(), getContext());
        recyclerView = binding.weekCalendarTopRecyclerView;
        adapter = new CalendarWeekAdapter(getContext(), mCalendarEntryList);
        recyclerView.setAdapter(adapter);
        //todo ver si uso linear layout, grid o relative
        // sistema del calendari: o scrollviews separades vinculades per codi (hores/linies horizontals/events)
        //                        o hores + linies en item.addItemDecoration(new LineItemDecoration())
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        //final TextView textView = binding.text_notifications;
        View[] calendarHorizontalLines = new View[6];
        View[] calendarVerticalLines = new View[6];
        calendar = Calendar.getInstance(Locale.getDefault());
        toolbarHelper = new ToolbarHelper(getContext(), getActivity(), getActivity().getMenuInflater(), R.menu.toolbar, true);
        toolbarHelper.setOnToolbarListener(this);
        toolbarHelper.setOnToolbarCalendarListener(this);
        binding.calendarAddEventButton2.setOnClickListener(this);

        screenWidth = getResources().getDisplayMetrics().widthPixels;
        //todo método semi común -> positionWeekDaysText();
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
                try{
                    RelativeLayout r = root.findViewById(R.id.calendarWeekLayout2);
                    positionHours(root);
                    //positionLines(calendarVerticalLines, r);
                }
                catch (Exception e){
                    Log.e(CalendarController.TAG , "error: " + e);
                }
            }
        });

        //calendarController.positionWeekDaysText(calendarDayInitials, screenWidth, 60);
        setTopMenu();

        //calendarViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);

        //función similar para posicionar las líneas del calendario
        return root;
    }

    private void setTopMenu() {
        getActivity().addMenuProvider(toolbarHelper, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }
    private void positionWeekDays(){
        //No debería hacer falta, como mucho para elegir si se empiza en domingo o lunes
    }

    private void positionHours(View root){

        LinearLayout hourLayout = root.findViewById(R.id.calendarHourLayout);
        for(int i=0; i<24/hourInterval; i++) {
            int styleResId = R.style.AppTheme_CalendarHours;
            //Creamos un textView con un estilo predefinido en styles.xml
            TextView hourTextView = new TextView(new ContextThemeWrapper(getContext(), styleResId), null, styleResId);
            //Si la hora es menor que 10, se añade un 0 delante
            String hora = i<10 ? ("0" + i + ":00") : (i + ":00");
            hourTextView.setText(hora);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) hourTextView.getLayoutParams();
            // Configurar la altura y los márgenes del TextView
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, // Ancho
                    LinearLayout.LayoutParams.WRAP_CONTENT // Altura (70dp convertido a píxeles)
            );
            //params.height = (int) ((screenHeight/2)/(24/hourInterval));   //creo que hay que pasarlo a dp
            layoutParams.height = 120;
            layoutParams.setMarginStart(5);
            //layoutParams.bottomMargin = 50;
            hourTextView.setLayoutParams(layoutParams);
            hourLayout.addView(hourTextView);
        }
    }

    /**posiciona las líneas del calendario**/
    //todo reactivar la llamada y posicionar las líneas horizontales según el comentario de abajo
    private void positionLines(View[] calendarVerticalLines, RelativeLayout root) {

        //En pantalla se deben mostart 8 "espacios" horizontales. Si el intervalo de horas es 1, se muestran 8 horas, si es 2, se muestran 4, etc.
        float horizontalLineSeparation = screenHeight/(8/hourInterval);
        for(int i=0; i<24; i+=hourInterval){

            View horizontalLine = new View(getContext());
            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, ScreenHelper.convertDpToPixels(1, getContext()));
            horizontalLine.setLayoutParams(layoutParams);
            horizontalLine.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.black));
            horizontalLine.setY(horizontalLineSeparation*((i/hourInterval)+1));

            try{
                root.addView(horizontalLine);
            }catch (Exception e){
                Log.e(CalendarController.TAG, "error: " + e);
            }

            calendarHorizontalLines.add(horizontalLine);

        }


        float verticalLineSeparation = screenWidth/7;
        for(int i=0;i<6;i++){
            calendarVerticalLines[i].setX(verticalLineSeparation*(i+1));
            calendarVerticalLines[i].setY(0);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    //todo todos los siguientes mñetodos deberían ser comunes:
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
    @Override
    public void switchFragment(){
        NavController navController = Navigation.findNavController(getActivity(), R.id.nav_host_fragment_activity_main);
        navController.navigate(R.id.action_navigation_calendar_week_to_navigation_calendar_month);
    }

    private void addEvent(){
        Intent intent = new Intent(getActivity(), AddEventActivity.class);
        intent.putExtra("entry", AddEventActivity.TYPE_TASK);
        someActivityResultLauncher.launch(intent);
    }
}
