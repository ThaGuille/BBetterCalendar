package com.example.bbettercalendar.ui.social;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.social.SocialFeature;
import com.example.bbettercalendar.social.account.SocialPrefs;
import com.example.bbettercalendar.social.invite.InviteLinkRouting;

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
 * <p>Declarada en el manifest principal (L-u6, L.7): también en release. Sin FEATURE_SOCIAL es inerte:
 * {@link InviteLinkRouting} no deja guardar nada y solo se abre el launcher.
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
        if (code != null) {
            // En el serial de SocialPrefs: la lectura de MainActivity, pedida después, ya lo ve.
            socialPrefs.savePendingInvite(code);
            Log.i(TAG, "Pending invite saved: " + code);
        } else {
            Log.i(TAG, "Invite link ignored (social off or no valid code)");
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
        if (uri == null) {
            return null;
        }
        return InviteLinkRouting.codeToStore(SocialFeature.isAccountEnabled(), uri.getScheme(), uri.getHost(),
                uri.getPath());
    }
}
