package com.example.bbettercalendar.ui.focus;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.TopBarHelper;

/**
 * u12: destination shell ({@code navigation_cycle_complete}) — top bar with back, no bottom nav
 * (MainActivity hides it for non-top-level destinations). Its screen unit fills the content.
 */
public class CycleCompleteFragment extends Fragment {

    public CycleCompleteFragment() {
        super(R.layout.fragment_cycle_complete);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TopBarHelper.bind(this, view, R.string.title_cycle_complete);
    }
}
