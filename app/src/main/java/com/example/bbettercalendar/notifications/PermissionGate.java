package com.example.bbettercalendar.notifications;

import android.Manifest;
import android.content.Context;

import androidx.activity.result.ActivityResultLauncher;

import com.example.bbettercalendar.configuration.Configuration;
import com.example.bbettercalendar.configuration.ConfigurationManager;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PermissionGate {

    private static final long RETRY_AFTER_FIRST_DENY_MILLIS = TimeUnit.DAYS.toMillis(7);
    private static final long RETRY_AFTER_SECOND_DENY_MILLIS = TimeUnit.DAYS.toMillis(14);
    private static final int MAX_AUTO_ASKS = 3;

    private final ConfigurationManager configurationManager;

    @Inject
    public PermissionGate(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    public void maybeRequest(Context context, ActivityResultLauncher<String> launcher) {
        if (!PermissionHelper.requiresRuntimePostNotificationsPermission()) {
            return;
        }
        if (PermissionHelper.notificationsGranted(context)) {
            return;
        }
        Configuration config = configurationManager.getConfiguration();
        if (config == null) return;

        int askCount = config.getNotificationPermissionAskCount();
        long lastAsked = config.getNotificationPermissionLastAskedMillis();
        long now = System.currentTimeMillis();

        if (askCount >= MAX_AUTO_ASKS) {
            return;
        }

        boolean shouldAsk;
        if (askCount == 0) {
            shouldAsk = true;
        } else if (askCount == 1) {
            shouldAsk = (now - lastAsked) >= RETRY_AFTER_FIRST_DENY_MILLIS;
        } else {
            shouldAsk = (now - lastAsked) >= RETRY_AFTER_SECOND_DENY_MILLIS;
        }

        if (!shouldAsk) return;

        config.setNotificationPermissionAskCount(askCount + 1);
        config.setNotificationPermissionLastAskedMillis(now);
        configurationManager.updateConfiguration(config);

        launcher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }
}
