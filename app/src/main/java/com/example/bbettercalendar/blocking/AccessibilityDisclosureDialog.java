package com.example.bbettercalendar.blocking;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.database.AppDatabase;
import com.example.bbettercalendar.stats.ConsentRecord;
import com.example.bbettercalendar.ui.progress.ProgressViewModel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Divulgación prominente del bloqueo por Accesibilidad (requisito de Play: disclosure + consentimiento
// afirmativo ANTES del deep-link a Ajustes). Texto en lenguaje llano: el servicio SÓLO detecta la app
// en primer plano y cubre las apps que TÚ elegiste limitar; nunca lee ni transmite el contenido de la
// pantalla. Se muestra SIEMPRE que el servicio no esté activado (no sólo la 1ª vez): también hace de
// guía de "qué tocar en Ajustes". En "Enable" persiste el ConsentRecord, ARMA el enforce de la app que
// disparó el diálogo (para que el bloqueo funcione en cuanto se active el servicio) y abre Ajustes ->
// Accesibilidad; en "Not now" no cambia nada (no se arma nada sin consentimiento afirmativo).
public class AccessibilityDisclosureDialog extends DialogFragment {

    private static final String ARG_PACKAGE = "package";

    // packageName: la app cuyo toggle 🚫 disparó el diálogo (se arma su enforce en "Enable").
    public static AccessibilityDisclosureDialog newInstance(String packageName) {
        AccessibilityDisclosureDialog dialog = new AccessibilityDisclosureDialog();
        Bundle args = new Bundle();
        args.putString(ARG_PACKAGE, packageName);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity(), R.style.RoundedDialog);
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.popup_accessibility_disclosure, null);
        builder.setView(view);

        view.findViewById(R.id.accessibility_disclosure_continue).setOnClickListener(v -> {
            persistConsent();
            armEnforce();
            openAccessibilitySettings();
            dismiss();
        });
        view.findViewById(R.id.accessibility_disclosure_cancel).setOnClickListener(v -> dismiss());

        return builder.create();
    }

    // Arma enforceAtLimit para la app que disparó el diálogo, reutilizando el MISMO ProgressViewModel
    // del fragmento padre (mismo scope). El servicio aún no está activo -> la fila se pinta "pendiente"
    // (ámbar), no "activa" (rojo), hasta que el usuario active el servicio en Ajustes.
    private void armEnforce() {
        String packageName = getArguments() != null ? getArguments().getString(ARG_PACKAGE) : null;
        if (packageName == null) return;
        androidx.fragment.app.Fragment parent = getParentFragment();
        if (parent == null) return;
        new ViewModelProvider(parent).get(ProgressViewModel.class)
                .setEnforceAtLimit(packageName, true);
    }

    // Inserta el acuse fuera del hilo principal (regla #3). Executor de un uso, cerrado tras encolar.
    private void persistConsent() {
        Context appContext = requireContext().getApplicationContext();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> AppDatabase.getDatabase(appContext).consentRecordDao()
                .upsert(new ConsentRecord(ConsentRecord.KEY_ACCESSIBILITY_BLOCKING,
                        System.currentTimeMillis(), ConsentRecord.ACCESSIBILITY_DISCLOSURE_VERSION)));
        executor.shutdown();
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(AccessibilityAccess.accessibilitySettingsIntent());
        } catch (ActivityNotFoundException e) {
            // Algunos dispositivos no exponen esta pantalla; el usuario puede activarlo manualmente.
        }
    }
}
