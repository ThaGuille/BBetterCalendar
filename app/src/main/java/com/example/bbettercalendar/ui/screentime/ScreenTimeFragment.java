package com.example.bbettercalendar.ui.screentime;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.TopBarHelper;

/**
 * u12: destination shell ({@code navigation_screen_time}) — top bar with back, no bottom nav
 * (MainActivity hides it for non-top-level destinations). Its screen unit fills the content.
 */
public class ScreenTimeFragment extends Fragment {

    public ScreenTimeFragment() {
        super(R.layout.fragment_screen_time);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TopBarHelper.bind(this, view, R.string.title_screen_time);
    }
}
