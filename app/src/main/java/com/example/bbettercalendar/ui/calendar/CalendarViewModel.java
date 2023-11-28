package com.example.bbettercalendar.ui.calendar;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class CalendarViewModel extends ViewModel {

    private final MutableLiveData<String> mText;

    public CalendarViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue(" ");
        /*Esta clase se usa para almacenar y administrar datos relacionados con la IU en un ciclo de vida consciente de los cambios.
        La clase ViewModel permite que los datos sobrevivan a los cambios de configuración, como las rotaciones de pantalla.
         */

        //datos a guardar aquí: dia inicio mes, dia seleccionado, eventos del mes, índices popsicion días... no vistas
    }

    public LiveData<String> getText() {
        return mText;
    }
}