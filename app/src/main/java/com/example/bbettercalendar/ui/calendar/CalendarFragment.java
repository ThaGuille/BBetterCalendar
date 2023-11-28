package com.example.bbettercalendar.ui.calendar;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ReplacementSpan;
import android.text.style.ScaleXSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.bbettercalendar.configuration.Configuration;
import com.example.bbettercalendar.databinding.FragmentCalendarBinding;
import com.example.bbettercalendar.helpers.ScreenHelper;

import java.util.Calendar;
import java.util.Locale;

import javax.inject.Inject;

public class CalendarFragment extends Fragment {

    @Inject
    Configuration config;

    private FragmentCalendarBinding binding;
    // Guillem -> això s'haurà de canviar per el valor estàtic que es crearà en una classe a part
    private float screenWidth;
    private float screenHeight;
    private Calendar calendar;
    private TextView calendarDayInitials;
    private final int daysMargin = 70;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        CalendarViewModel calendarViewModel =
                new ViewModelProvider(this).get(CalendarViewModel.class);

        binding = FragmentCalendarBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textNotifications;
        calendarDayInitials = binding.calendarDayInitials;
        View[] calendarHorizontalLines = new View[6];
        View[] calendarVerticalLines = new View[6];
        calendar = Calendar.getInstance(Locale.getDefault());
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

        calendarViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);

        //función similar para posicionar las líneas del calendario
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
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
}