package com.example.bbettercalendar.notifications;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.example.bbettercalendar.MainActivity;
import com.example.bbettercalendar.R;

public final class NotificationSpec {

    public final String channelId;
    public final int notificationId;
    public final String title;
    public final String body;
    @DrawableRes public final int smallIconRes;
    public final PendingIntent contentIntent;
    public final boolean autoCancel;

    private NotificationSpec(Builder b) {
        this.channelId = b.channelId;
        this.notificationId = b.notificationId;
        this.title = b.title;
        this.body = b.body;
        this.smallIconRes = b.smallIconRes;
        this.contentIntent = b.contentIntent;
        this.autoCancel = b.autoCancel;
    }

    public static final class Builder {
        private final String channelId;
        private final int notificationId;
        private String title = "";
        private String body = "";
        @DrawableRes private int smallIconRes = R.drawable.ic_notifications_black_24dp;
        private PendingIntent contentIntent;
        private boolean autoCancel = true;

        public Builder(@NonNull String channelId, int notificationId) {
            this.channelId = channelId;
            this.notificationId = notificationId;
        }

        public Builder title(String title) { this.title = title; return this; }
        public Builder body(String body) { this.body = body; return this; }
        public Builder smallIcon(@DrawableRes int res) { this.smallIconRes = res; return this; }
        public Builder contentIntent(PendingIntent pi) { this.contentIntent = pi; return this; }
        public Builder autoCancel(boolean v) { this.autoCancel = v; return this; }

        public Builder openMainActivity(Context context) {
            Intent intent = new Intent(context, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
            this.contentIntent = PendingIntent.getActivity(context, notificationId, intent, flags);
            return this;
        }

        public NotificationSpec build() {
            return new NotificationSpec(this);
        }
    }
}
