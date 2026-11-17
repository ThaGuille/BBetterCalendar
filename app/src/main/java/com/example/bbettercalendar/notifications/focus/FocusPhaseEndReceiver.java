package com.example.bbettercalendar.notifications.focus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Suena la alarma de fin de fase ({@link FocusPhaseAlarm}). Corre en el hilo principal.
 *
 * <ul>
 *   <li>Sesión viva en el proceso: se le pide que avance. La transición, el aviso (sólo en segundo
 *       plano) y la alarma de la fase siguiente salen de ahí, así que nunca hay dos avisos.</li>
 *   <li>Sin sesión viva (el proceso había muerto y la alarma lo ha arrancado): se publica el aviso
 *       con los extras de la alarma, si la app no está a la vista. La sesión se termina de cerrar al
 *       restaurarse (SavedStateHandle) cuando el usuario vuelva.</li>
 * </ul>
 */
public class FocusPhaseEndReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        FocusPhaseAlarm.LiveSession live = FocusPhaseAlarm.liveSession();
        if (live != null) {
            live.onPhaseAlarm();
            return;
        }
        if (intent == null || FocusPhaseAlarm.isAppInForeground()) {
            return;
        }
        FocusPhaseAlarm.Notice notice =
                FocusPhaseAlarm.noticeFrom(intent.getStringExtra(FocusPhaseAlarm.EXTRA_NOTICE));
        if (notice == null) {
            return;
        }
        FocusPhaseEndNotifier.post(context, notice,
                intent.getIntExtra(FocusPhaseAlarm.EXTRA_NEXT_MINUTES, 0));
    }
}
