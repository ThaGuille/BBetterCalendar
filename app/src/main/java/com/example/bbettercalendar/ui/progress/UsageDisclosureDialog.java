package com.example.bbettercalendar.ui.progress;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.database.AppDatabase;
import com.example.bbettercalendar.stats.ConsentRecord;
import com.example.bbettercalendar.usage.UsageAccess;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Divulgación prominente de Usage Access (requisito de Play: disclosure + consentimiento afirmativo
// ANTES del deep-link a Ajustes). En "Continue" persiste el ConsentRecord y abre Ajustes -> Acceso
// a uso; en "Not now" sólo se cierra. El Fragment decide si mostrarla (sólo si aún no se consintió).
public class UsageDisclosureDialog extends DialogFragment {

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity(), R.style.RoundedDialog);
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.popup_usage_disclosure, null);
        builder.setView(view);

        view.findViewById(R.id.usage_disclosure_continue).setOnClickListener(v -> {
            persistConsent();
            openUsageAccessSettings();
            dismiss();
        });
        view.findViewById(R.id.usage_disclosure_cancel).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.usage_disclosure_privacy_link)
                .setOnClickListener(v -> openPrivacyPolicy());

        return builder.create();
    }

    // Abre la política de privacidad alojada (GitHub Pages) en el navegador. No cierra el diálogo:
    // el usuario debería poder leerla y volver a decidir Continue / Not now.
    private void openPrivacyPolicy() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.privacy_policy_url))));
        } catch (ActivityNotFoundException e) {
            // Sin navegador disponible; no bloquea el flujo de consentimiento.
        }
    }

    // Inserta el acuse fuera del hilo principal (regla #3). Executor de un uso, cerrado tras encolar.
    private void persistConsent() {
        Context appContext = requireContext().getApplicationContext();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> AppDatabase.getDatabase(appContext).consentRecordDao()
                .upsert(new ConsentRecord(ConsentRecord.KEY_USAGE_ACCESS,
                        System.currentTimeMillis(), ConsentRecord.USAGE_ACCESS_DISCLOSURE_VERSION)));
        executor.shutdown();
    }

    private void openUsageAccessSettings() {
        try {
            startActivity(UsageAccess.usageAccessSettingsIntent());
        } catch (ActivityNotFoundException e) {
            // Algunos dispositivos no exponen esta pantalla; el estado seguirá LOCKED y el usuario
            // puede conceder el acceso manualmente desde Ajustes.
        }
    }
}
