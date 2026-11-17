package com.example.bbettercalendar.ui.home;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import com.example.bbettercalendar.notifications.focus.FocusOngoingNotification;

public class HomeForegroundService extends Service {


    /** Clase para detecar si la app esta en primer plano o en segundo plano
     *  y para iniciar el servicio en primer plano
     *
     *  Plan sistema-notificaciones (B.2): la notificación enseña la fase y una cuenta atrás en vivo.
     *  El contenido lo decide FocusSessionViewModel en cada transición (FocusOngoingNotification);
     *  aquí sólo se lee el último al arrancar. Sin contenido (reinicio STICKY tras matar el proceso)
     *  sale la genérica de siempre.
     *  */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = FocusOngoingNotification.build(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(FocusOngoingNotification.ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(FocusOngoingNotification.ID, notification);
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
