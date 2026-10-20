package com.example.bbettercalendar.ui.social;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.TopBarHelper;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * F1-u4: pantalla amable para quien está por debajo del umbral (04 §1.4). Lo social no está
 * disponible y el resto de la app funciona igual; el año queda guardado y no se vuelve a preguntar.
 */
@AndroidEntryPoint
public class UnderageFragment extends Fragment {

    public UnderageFragment() {
        super(R.layout.fragment_underage);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TopBarHelper.bind(this, view, R.string.underage_top_title);
        view.findViewById(R.id.underageBackButton).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
    }
}
