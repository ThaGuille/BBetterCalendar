package com.example.bbettercalendar.popups;

import android.app.Dialog;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * mejora-visual u14: las hojas inferiores (Timer, Racha, Avisos, Repeticion, Descripcion) se abren
 * ya expandidas: colapsadas, el peek (~16:9 del ancho) dejaria el boton principal fuera de la
 * zona visible. Arrastrar hacia abajo sigue cerrandolas (hideable por defecto).
 */
final class SheetBehavior {

    private SheetBehavior() { }

    static void expand(@Nullable Dialog dialog) {
        if (dialog instanceof BottomSheetDialog) {
            BottomSheetBehavior<FrameLayout> behavior = ((BottomSheetDialog) dialog).getBehavior();
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }
}
