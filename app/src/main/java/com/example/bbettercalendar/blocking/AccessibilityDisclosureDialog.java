package com.example.bbettercalendar.blocking;

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

import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

// Divulgación prominente del bloqueo por Accesibilidad (requisito de Play: disclosure + consentimiento
// afirmativo ANTES del deep-link a Ajustes). Texto en lenguaje llano: el servicio SÓLO detecta la app
// en primer plano y cubre las apps que TÚ elegiste limitar; nunca lee ni transmite el contenido de la
// pantalla. Se muestra SIEMPRE que el servicio no esté activado (no sólo la 1ª vez): también hace de
// guía de "qué tocar en Ajustes". En "Enable" persiste el ConsentRecord, avisa al host para que ARME lo
// que disparó el diálogo (para que el bloqueo funcione en cuanto se active el servicio) y abre Ajustes ->
// Accesibilidad; en "Not now" no cambia nada (no se arma nada sin consentimiento afirmativo).
@AndroidEntryPoint
public class AccessibilityDisclosureDialog extends DialogFragment {

    @Inject ConsentRecordDAO consentRecordDao;
    @Inject @IoExecutor ExecutorService executor;

    // Implementado por el host (Focus, Screen time, detalle de app): arma lo que disparó el diálogo
    // (modo bloqueo total del pomodoro o el enforceAtLimit de una app).
    public interface OnConsentGrantedListener {
        void onAccessibilityConsentGranted();
    }

    // El host se entera del consentimiento vía OnConsentGrantedListener. (La variante con paquete,
    // que armaba el enforce vía ProgressViewModel, desapareció con la banda de uso de Progress.)
    public static AccessibilityDisclosureDialog newInstance() {
        return new AccessibilityDisclosureDialog();
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
        view.findViewById(R.id.accessibility_disclosure_privacy_link)
                .setOnClickListener(v -> openPrivacyPolicy());

        return builder.create();
    }

    // Abre la política de privacidad alojada (GitHub Pages) en el navegador. No cierra el diálogo:
    // el usuario debería poder leerla y volver a decidir Enable / Not now.
    private void openPrivacyPolicy() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.privacy_policy_url))));
        } catch (ActivityNotFoundException e) {
            // Sin navegador disponible; no bloquea el flujo de consentimiento.
        }
    }

    // El host se entera vía OnConsentGrantedListener y arma lo que le corresponda. El servicio aún no
    // está activo -> el toggle se pinta "pendiente" (ámbar), no "activo" (rojo), hasta que el usuario
    // active el servicio en Ajustes.
    private void armEnforce() {
        androidx.fragment.app.Fragment parent = getParentFragment();
        if (parent instanceof OnConsentGrantedListener) {
            ((OnConsentGrantedListener) parent).onAccessibilityConsentGranted();
        }
    }

    // Inserta el acuse fuera del hilo principal (regla #3), en el executor de IO compartido.
    private void persistConsent() {
        executor.execute(() -> consentRecordDao.upsert(new ConsentRecord(ConsentRecord.KEY_ACCESSIBILITY_BLOCKING,
                System.currentTimeMillis(), ConsentRecord.ACCESSIBILITY_DISCLOSURE_VERSION)));
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(AccessibilityAccess.accessibilitySettingsIntent());
        } catch (ActivityNotFoundException e) {
            // Algunos dispositivos no exponen esta pantalla; el usuario puede activarlo manualmente.
        }
    }
}
