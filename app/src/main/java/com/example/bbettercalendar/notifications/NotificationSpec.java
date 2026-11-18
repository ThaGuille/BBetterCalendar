package com.example.bbettercalendar.notifications;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;

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
    /**
     * F5-u6 (D-034 d7): texto de la versión pública (pantalla de bloqueo segura). Null = sin versión
     * pública (el sistema aplica su visibilidad por defecto).
     */
    public final String publicTitle;
    public final String publicBody;
    /** Botones de acción de la notificación (vacío = sin acciones). */
    public final List<NotificationCompat.Action> actions;
    /** Sale sin sonido ni vibración (amigos durante una concentración, plan sistema-notificaciones B.4). */
    public final boolean silent;
    /** Pre-O vibration pattern (from O the channel's wins); null = the default. */
    public final long[] vibrate;
    /** {@code NotificationCompat.CATEGORY_*}; null = none. */
    public final String category;

    private NotificationSpec(Builder b) {
        this.channelId = b.channelId;
        this.notificationId = b.notificationId;
        this.title = b.title;
        this.body = b.body;
        this.smallIconRes = b.smallIconRes;
        this.contentIntent = b.contentIntent;
        this.autoCancel = b.autoCancel;
        this.publicTitle = b.publicTitle;
        this.publicBody = b.publicBody;
        this.actions = b.actions;
        this.silent = b.silent;
        this.vibrate = b.vibrate;
        this.category = b.category;
    }

    public static final class Builder {
        private final String channelId;
        private final int notificationId;
        private String title = "";
        private String body = "";
        @DrawableRes private int smallIconRes = R.drawable.ic_stat_bbetter;
        private PendingIntent contentIntent;
        private boolean autoCancel = true;
        private String publicTitle;
        private String publicBody;
        private final List<NotificationCompat.Action> actions = new ArrayList<>();
        private boolean silent;
        private long[] vibrate;
        private String category;

        public Builder(@NonNull String channelId, int notificationId) {
            this.channelId = channelId;
            this.notificationId = notificationId;
        }

        public Builder title(String title) { this.title = title; return this; }
        public Builder body(String body) { this.body = body; return this; }
        public Builder smallIcon(@DrawableRes int res) { this.smallIconRes = res; return this; }
        public Builder contentIntent(PendingIntent pi) { this.contentIntent = pi; return this; }
        public Builder autoCancel(boolean v) { this.autoCancel = v; return this; }
        public Builder silent(boolean v) { this.silent = v; return this; }
        public Builder vibrate(long[] pattern) { this.vibrate = pattern; return this; }
        public Builder category(String category) { this.category = category; return this; }

        /** F5-u6 (d7): lo que se ve con el móvil bloqueado en vez de título y cuerpo (sin datos personales). */
        public Builder publicVersion(String title, String body) {
            this.publicTitle = title;
            this.publicBody = body;
            return this;
        }

        public Builder addAction(NotificationCompat.Action action) {
            if (action != null) this.actions.add(action);
            return this;
        }

        public Builder openMainActivity(Context context) {
            Intent intent = new Intent(context, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
            this.contentIntent = PendingIntent.getActivity(context, notificationId, intent, flags);
            return this;
        }

        /**
         * Como {@link #openMainActivity(Context)} pero pidiendo a MainActivity que navegue al
         * detalle del proyecto (spec project-deadlines-progress). La construcción del Intent se
         * queda aquí, donde ya vivía; MainActivity sólo consume el extra.
         */
        public Builder openProjectDetail(Context context, int projectId) {
            Intent intent = new Intent(context, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra(MainActivity.EXTRA_OPEN_PROJECT_ID, projectId);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
            this.contentIntent = PendingIntent.getActivity(context, notificationId, intent, flags);
            return this;
        }

        public NotificationSpec build() {
            return new NotificationSpec(this);
        }
    }
}
