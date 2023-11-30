package com.example.bbettercalendar.helpers;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;

import com.example.bbettercalendar.R;

public class ToolbarHelper implements MenuProvider{

        OnToolBarListener listener;
        Context context;
        Activity activity;
        MenuInflater menuInflater;
        int menuRes;
        public final static int FINISH =1;

        public ToolbarHelper(Context context, Activity activity, MenuInflater menuInflater, int menuRes) {
                this.context = context;
                this.activity = activity;
                this.menuInflater = menuInflater;
                this.menuRes = menuRes;
        }

        public void setOnToolbarListener(OnToolBarListener onToolBarListener) {
            this.listener = onToolBarListener;
        }

        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(menuRes, menu);
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
}
