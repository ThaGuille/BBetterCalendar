package com.example.bbettercalendar.ui.screentime;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.TopBarHelper;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * u12: destination shell ({@code navigation_app_detail}) — top bar with back, no bottom nav
 * (MainActivity hides it for non-top-level destinations). Its screen unit fills the content.
 */
public class AppDetailFragment extends Fragment {

    public AppDetailFragment() {
        super(R.layout.fragment_app_detail);
    }

    /** Nav argument (mobile_navigation.xml): package of the app whose detail is shown. */
    public static final String ARG_PACKAGE_NAME = "packageName";

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MaterialToolbar toolbar = TopBarHelper.bind(this, view, R.string.title_app_detail);
        String packageName = getArguments() != null
                ? getArguments().getString(ARG_PACKAGE_NAME) : null;
        if (toolbar != null) {
            toolbar.setSubtitle(packageName);
        }
    }
}
