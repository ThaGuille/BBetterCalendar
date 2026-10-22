package com.example.bbettercalendar.ui.social;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.social.SocialFeature;
import com.example.bbettercalendar.social.account.SocialPrefs;
import com.example.bbettercalendar.social.invite.InviteCodes;
import com.example.bbettercalendar.social.invite.InviteLinkFactory;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Destino del App Link de invitación {@code https://bbetter-dbcda.web.app/i/<código>} (F2-u6, 03 §11). Sin UI
 * ({@code Theme.NoDisplay}, {@code noHistory}, su propia tarea vacía): guarda el código como invitación
 * pendiente en {@link SocialPrefs} (caduca a los 7 días) y abre la app con el intent del launcher
 * ({@code getLaunchIntentForPackage}). La hoja «Añadir a X» la enseña MainActivity (F2-u8).
 *
 * <p>No pasa por {@code SplashActivity} con un VIEW intent: con la tarea viva crearía una segunda
 * MainActivity (el caso de u14). El intent del launcher trae la tarea existente al frente, y si la Splash
 * llega a crearse encima de una MainActivity viva, ya sabe cerrarse sola.
 *
 * <p>El intent-filter vive solo en {@code app/src/debug/AndroidManifest.xml} hasta L.7 (D-025): en release
 * la actividad no está declarada.
 */
@AndroidEntryPoint
public class InviteLinkActivity extends ComponentActivity {

    private static final String TAG = "InviteLinkActivity";

    @Inject
    SocialPrefs socialPrefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String code = codeOf(getIntent());
        if (code != null && SocialFeature.isAccountEnabled()) {
            // En el serial de SocialPrefs: la lectura de MainActivity, pedida después, ya lo ve.
            socialPrefs.savePendingInvite(code);
            Log.i(TAG, "Pending invite saved: " + code);
        } else {
            Log.i(TAG, "Invite link without a valid code: " + getIntent().getData());
        }
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(launch);
        }
        // Theme.NoDisplay exige terminar antes de onResume.
        finish();
    }

    @Nullable
    private static String codeOf(@Nullable Intent intent) {
        Uri uri = intent == null ? null : intent.getData();
        if (uri == null || !InviteLinkFactory.SCHEME.equals(uri.getScheme())
                || !InviteLinkFactory.HOST.equalsIgnoreCase(uri.getHost())) {
            return null;
        }
        return InviteCodes.fromLink(uri.getPath());
    }
}
