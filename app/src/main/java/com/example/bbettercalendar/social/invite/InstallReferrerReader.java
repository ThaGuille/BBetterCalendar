package com.example.bbettercalendar.social.invite;

import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.installreferrer.api.InstallReferrerClient;
import com.android.installreferrer.api.InstallReferrerStateListener;
import com.android.installreferrer.api.ReferrerDetails;
import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.social.SocialFeature;
import com.example.bbettercalendar.social.account.SocialPrefs;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Invitación por Install Referrer (F2-u6, 03 §11): quien instala desde el enlace a Play de la página
 * {@code /i/<código>} ({@code referrer=invite%3D<código>}) llega a la app con una invitación pendiente, igual
 * que con el App Link.
 *
 * <ul>
 *   <li>Solo con {@link SocialFeature#isAccountEnabled()} (en release no hace nada) y una sola vez por
 *       instalación: la bandera {@code install_referrer_checked} de {@link SocialPrefs} se pone con una
 *       respuesta definitiva de Play (OK, o el servicio no existe en el dispositivo). Si el servicio no está
 *       disponible o se desconecta, se vuelve a probar en el siguiente arranque.</li>
 *   <li>El código sale de {@link InviteCodes#fromReferrer}; uno válido se guarda con
 *       {@link SocialPrefs#savePendingInvite} (caduca a los 7 días) y lo recoge la hoja «Añadir a X» (F2-u8).</li>
 * </ul>
 * Solo se prueba con JUnit (el parser) hasta el gate L: instalar desde Play necesita la ficha publicada.
 * Regla #3: la llamada al servicio de Play ({@code getInstallReferrer}) y las preferencias van en el
 * {@code @IoExecutor}.
 */
@Singleton
public class InstallReferrerReader {

    private static final String TAG = "InstallReferrerReader";

    private final Context appContext;
    private final SocialPrefs prefs;
    private final ExecutorService io;
    /** Una conexión por proceso como mucho, aunque se llame más de una vez. */
    private final AtomicBoolean started = new AtomicBoolean(false);

    @Inject
    public InstallReferrerReader(@ApplicationContext Context context, SocialPrefs prefs,
                                 @IoExecutor ExecutorService io) {
        this.appContext = context;
        this.prefs = prefs;
        this.io = io;
    }

    /** Lee el referrer si aún no se leyó en esta instalación. Se llama al arrancar la app; no bloquea. */
    public void readOnce() {
        if (!SocialFeature.isAccountEnabled() || !started.compareAndSet(false, true)) {
            return;
        }
        prefs.readReferrerChecked(checked -> {
            if (!checked) {
                connect();
            }
        });
    }

    private void connect() {
        InstallReferrerClient client = InstallReferrerClient.newBuilder(appContext).build();
        try {
            client.startConnection(new InstallReferrerStateListener() {
                @Override
                public void onInstallReferrerSetupFinished(int responseCode) {
                    // Llega en el hilo principal: la llamada al servicio y las preferencias, en E/S.
                    io.execute(() -> onSetupFinished(client, responseCode));
                }

                @Override
                public void onInstallReferrerServiceDisconnected() {
                    // Sin reintento en este arranque: la bandera no se puso, se prueba en el siguiente.
                }
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "Install Referrer unavailable", e);
        }
    }

    private void onSetupFinished(@NonNull InstallReferrerClient client, int responseCode) {
        try {
            if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                ReferrerDetails details = client.getInstallReferrer();
                handleReferrer(details == null ? null : details.getInstallReferrer());
                prefs.markReferrerChecked();
            } else if (responseCode == InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED) {
                prefs.markReferrerChecked(); // sin Play Store: nunca habrá referrer
            } else {
                Log.i(TAG, "Install Referrer not ready (" + responseCode + "), retrying next launch");
            }
        } catch (RemoteException | RuntimeException e) {
            Log.w(TAG, "Install Referrer failed, retrying next launch", e);
        } finally {
            try {
                client.endConnection();
            } catch (RuntimeException ignored) {
                // ya desconectado
            }
        }
    }

    private void handleReferrer(@Nullable String referrer) {
        String code = InviteCodes.fromReferrer(referrer);
        if (code != null) {
            Log.i(TAG, "Pending invite from Install Referrer: " + code);
            prefs.savePendingInvite(code);
        }
    }
}
