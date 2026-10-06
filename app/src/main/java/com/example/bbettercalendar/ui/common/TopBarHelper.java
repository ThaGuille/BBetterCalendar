package com.example.bbettercalendar.ui.common;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.bbettercalendar.R;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * u12: wires the shared secondary-screen top bar ({@code @layout/view_top_bar}, id
 * {@code R.id.topBar}): title + back arrow that pops the nav back stack (same as system back).
 * The bottom nav is hidden for these destinations by MainActivity, not here.
 */
public final class TopBarHelper {

    private TopBarHelper() { }

    /** Binds the top bar found under {@code root}; returns it so callers can add a subtitle/menu. */
    @Nullable
    public static MaterialToolbar bind(@NonNull Fragment fragment, @NonNull View root,
                                       @StringRes int title) {
        return bind(fragment, root, fragment.getString(title));
    }

    @Nullable
    public static MaterialToolbar bind(@NonNull Fragment fragment, @NonNull View root,
                                       @Nullable CharSequence title) {
        MaterialToolbar toolbar = root.findViewById(R.id.topBar);
        if (toolbar == null) {
            return null;
        }
        toolbar.setTitle(title);
        toolbar.setNavigationOnClickListener(v -> {
            // navigateUp devuelve false si ya no hay nada debajo (p. ej. destino abierto como
            // raíz): entonces se delega en el back del sistema.
            if (!NavHostFragment.findNavController(fragment).navigateUp()) {
                fragment.requireActivity().getOnBackPressedDispatcher().onBackPressed();
            }
        });
        return toolbar;
    }
}
