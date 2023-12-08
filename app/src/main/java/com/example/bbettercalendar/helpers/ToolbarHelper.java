package com.example.bbettercalendar.helpers;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.events.AddEventActivity;

import java.util.List;

public class ToolbarHelper implements MenuProvider, View.OnClickListener{

        OnToolBarListener listener;
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
        }

        public void setToolbarElements(List<View> elements){
                for(View element : elements){
                        element.setOnClickListener(this);
                }
        }

        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                if(isMenuResFile)
                        menuInflater.inflate(menuRes, menu);
                else {
                        //View customToolbarLayout = activity.getLayoutInflater().inflate(menuRes, toolbar, false);
                        //toolbar.addView(customToolbarLayout);
                }
                //Editar los iconos de la toolbar con sintaxis tipo - MenuItem favItem = menu.findItem(R.id.toolbarButtonFav);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                        case R.id.toolbarButtonFav:
                                //acciones para toolbar
                                break;
                        case R.id.go_back:
                                Log.i("ToolbarHelper", "onMenuItemSelected: go_back");
                                activity.finish();
                        default:
                                break;
                }
                return false;
        }

        @Override
        public void onClick(View view) {
                switch (view.getId()) {
                        case R.id.btnSaveEvent:
                                listener.onToolbarLoaded(AddEventActivity.CLOSE_AND_SAVE);
                                break;
                        case R.id.btnClose:
                                listener.onToolbarLoaded(AddEventActivity.CLOSE);
                                break;
                }
        }



        public void setOnToolbarListener(OnToolBarListener onToolBarListener) {
                this.listener = onToolBarListener;
        }
}
