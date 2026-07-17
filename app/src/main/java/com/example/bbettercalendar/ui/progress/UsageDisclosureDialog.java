package com.example.bbettercalendar.ui.progress;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.stats.ConsentRecord;
import com.example.bbettercalendar.stats.ConsentRecordDAO;
import com.example.bbettercalendar.usage.UsageAccess;

import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

// Divulgación prominente de Usage Access (requisito de Play: disclosure + consentimiento afirmativo
// ANTES del deep-link a Ajustes). En "Continue" persiste el ConsentRecord y abre Ajustes -> Acceso
// a uso; en "Not now" sólo se cierra. El Fragment decide si mostrarla (sólo si aún no se consintió).
@AndroidEntryPoint
public class UsageDisclosureDialog extends DialogFragment {

    @Inject ConsentRecordDAO consentRecordDao;
    @Inject @IoExecutor ExecutorService executor;

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

    // Inserta el acuse fuera del hilo principal (regla #3), en el executor de IO compartido.
    private void persistConsent() {
        executor.execute(() -> consentRecordDao.upsert(new ConsentRecord(ConsentRecord.KEY_USAGE_ACCESS,
                System.currentTimeMillis(), ConsentRecord.USAGE_ACCESS_DISCLOSURE_VERSION)));
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
