package com.example.bbettercalendar.ui.progress;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.TopBarHelper;

/**
 * u12: destination shell ({@code navigation_all_progress}) — top bar with back, no bottom nav
 * (MainActivity hides it for non-top-level destinations). Its screen unit fills the content.
 */
public class AllProgressFragment extends Fragment {

    public AllProgressFragment() {
        super(R.layout.fragment_all_progress);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TopBarHelper.bind(this, view, R.string.title_all_progress);
    }
}
