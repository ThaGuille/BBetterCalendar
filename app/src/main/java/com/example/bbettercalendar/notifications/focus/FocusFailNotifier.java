package com.example.bbettercalendar.notifications.focus;

import android.content.Context;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.notifications.BBetterNotifier;
import com.example.bbettercalendar.notifications.NotificationChannels;
import com.example.bbettercalendar.notifications.NotificationSpec;

import dagger.hilt.android.qualifiers.ApplicationContext;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FocusFailNotifier {

    private static final int FOCUS_FAIL_NOTIFICATION_ID = 50_001;

    private final Context appContext;
    private final BBetterNotifier notifier;

    @Inject
    public FocusFailNotifier(@ApplicationContext Context appContext, BBetterNotifier notifier) {
        this.appContext = appContext;
        this.notifier = notifier;
    }

    public void fire() {
        NotificationSpec spec = new NotificationSpec.Builder(
                NotificationChannels.CHANNEL_FOCUS_ALERTS,
                FOCUS_FAIL_NOTIFICATION_ID)
                .title(appContext.getString(R.string.notif_focus_fail_title))
                .body(appContext.getString(R.string.notif_focus_fail_body))
                .openMainActivity(appContext)
                .build();

        notifier.notify(spec);
    }
}
