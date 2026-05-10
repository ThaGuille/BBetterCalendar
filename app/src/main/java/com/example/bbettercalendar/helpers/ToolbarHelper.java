package com.example.bbettercalendar.helpers;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuProvider;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.calendarEntries.AddEventActivity;
import com.google.android.material.color.MaterialColors;

import java.util.List;

public class ToolbarHelper implements MenuProvider, View.OnClickListener{

        private final String TAG = "toolbarHelperTAG";
        OnToolBarListener listener;
        OnToolbarCalendarListener calendarListener;
        OnToolbarHomeListener homeListener;
        Context context;
        Activity activity;
        MenuInflater menuInflater;
        int menuRes;
        boolean isMenuResFile;
        public final static int FINISH =1;
        Toolbar toolbar;

        public ToolbarHelper(Context context, Activity activity, MenuInflater menuInflater, int menuRes, boolean isMenuResFile) {
                this.context = context;
                this.activity = activity;
                this.menuInflater = menuInflater;
                this.menuRes = menuRes;
                this.isMenuResFile = isMenuResFile;
                toolbar = activity.findViewById(menuRes);
                if(toolbar!=null){
                        TypedValue typedValue = new TypedValue();
                        int colorSecondary = MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorSecondary);
                        context.getTheme().resolveAttribute(colorSecondary, typedValue, true);
                        int color = typedValue.data;
                        toolbar.setBackgroundColor(color);
                }
        }

        public void setToolbarElements(List<View> elements){
                for(View element : elements){
                        element.setOnClickListener(this);
                }
        }

        public void pruebaBiggerIcon(){

        }

        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                if(isMenuResFile) {
                        menuInflater.inflate(menuRes, menu);
                }
                else {
                        //View customToolbarLayout = activity.getLayoutInflater().inflate(menuRes, toolbar, false);
                        //toolbar.addView(customToolbarLayout);
                }
                // Obtener el color del tema y establecerlo como fondo de la toolbar
                //MenuItem itemMaxStreak = menu.findItem(R.id.toolbarMaxStreak);

                //Editar los iconos de la toolbar con sintaxis tipo - MenuItem favItem = menu.findItem(R.id.toolbarButtonFav);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                int id = menuItem.getItemId();
                if (id == R.id.toolbarButtonSwitchCalendar) {
                        if (calendarListener!=null) calendarListener.switchFragment();
                } else if (id == R.id.go_back) {
                        Log.i(TAG, "onMenuItemSelected: go_back");
                        activity.finish();
                } else if (id == R.id.toolbarTimer) {
                        if (homeListener!=null) homeListener.onToolbarTimerClick();
                        Log.i(TAG, "toolbar ohme timer");
                }
                return false;
        }

        @Override
        public void onClick(View view) {
                int id = view.getId();
                if (id == R.id.btnSaveEvent) {
                        listener.onToolbarLoaded(AddEventActivity.CLOSE_AND_SAVE);
                } else if (id == R.id.btnClose) {
                        listener.onToolbarLoaded(AddEventActivity.CLOSE);
                }
        }



        public void setOnToolbarListener(OnToolBarListener onToolBarListener) {
                this.listener = onToolBarListener;
        }
        public void setOnToolbarCalendarListener(OnToolbarCalendarListener onToolbarCalendarListener) {
                this.calendarListener = onToolbarCalendarListener;
        }
        public void setOnToolbarHomeListener(OnToolbarHomeListener onToolbarHomeListener) {
                this.homeListener = onToolbarHomeListener;
        }
}
