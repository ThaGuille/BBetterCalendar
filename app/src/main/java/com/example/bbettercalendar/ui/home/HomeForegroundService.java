package com.example.bbettercalendar.ui.home;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.example.bbettercalendar.R;

public class HomeForegroundService extends Service {


    /** Clase para detecar si la app esta en primer plano o en segundo plano
     *  y para iniciar el servicio en primer plano
     *  */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start the foreground service
        Notification notification = new NotificationCompat.Builder(this, "channelId")
                .setContentTitle("Service is running")
                .setContentText("Your application is active")
                .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();

        startForeground(1, notification);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
