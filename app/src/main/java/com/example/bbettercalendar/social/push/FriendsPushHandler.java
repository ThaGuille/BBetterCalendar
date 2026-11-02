package com.example.bbettercalendar.social.push;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.MainActivity;
import com.example.bbettercalendar.notifications.BBetterNotifier;
import com.example.bbettercalendar.notifications.NotificationChannels;
import com.example.bbettercalendar.notifications.NotificationSpec;
import com.example.bbettercalendar.social.SocialFeature;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.function.BooleanSupplier;

import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.components.SingletonComponent;

/**
 * Enseña un push de amigos en el canal «Amigos» (F5-u6, D-034). {@link #show(Context, FriendsPushPayload)} es la
 * ÚNICA entrada: la usan {@link FriendsMessagingService} y la ruta de depuración de MainActivity (u7).
 *
 * <p>Se descarta (no se enseña nada) sin {@code FEATURE_SOCIAL}, sin sesión o si el push es para otro uid
 * ({@code to}): un token que quedó atrás tras un cierre de sesión fallido no enseña avisos de otra cuenta (revisión
 * r1). Sin el permiso POST_NOTIFICATIONS tampoco: lo comprueba {@link BBetterNotifier}.
 *
 * <p>La notificación se cancela sola al tocarla y abre MainActivity con {@link #EXTRA_OPEN_INBOX_ID} (la bandeja con
 * ese elemento leído; lo atiende u7). Id en [40_000, 49_999] ({@link FriendsPushPayload#notificationId()}).
 */
public final class FriendsPushHandler {

    /** Extra de MainActivity: id del elemento de la bandeja que hay que abrir (y marcar leído). */
    public static final String EXTRA_OPEN_INBOX_ID = "open_inbox_id";

    /** Quien publica de verdad la notificación (en la app, {@link BBetterNotifier}). */
    interface Sink {
        void post(@NonNull FriendsPushPayload payload);
    }

    /** Público solo porque el componente que genera Hilt (en otro paquete) lo implementa. */
    @EntryPoint
    @InstallIn(SingletonComponent.class)
    public interface Deps {
        BBetterNotifier notifier();

        FirebaseAuth auth();
    }

    private FriendsPushHandler() { }

    /** Enseña {@code payload} si toca (ver la clase). Desde cualquier hilo. */
    public static void show(@NonNull Context context, @Nullable FriendsPushPayload payload) {
        if (payload == null || !SocialFeature.isAccountEnabled()) {
            return;
        }
        Context app = context.getApplicationContext();
        Deps deps = EntryPointAccessors.fromApplication(app, Deps.class);
        FirebaseUser user = deps.auth().getCurrentUser();
        deliver(payload, user == null ? null : user.getUid(), SocialFeature::isAccountEnabled,
                p -> deps.notifier().notify(spec(app, p)));
    }

    /**
     * La decisión, sin Android: publica {@code payload} una vez en {@code sink} solo con el flag, una sesión y
     * {@code to} igual a ella.
     *
     * @return true si se publicó
     */
    static boolean deliver(@Nullable FriendsPushPayload payload, @Nullable String signedInUid,
                           @NonNull BooleanSupplier enabled, @NonNull Sink sink) {
        if (payload == null || !enabled.getAsBoolean() || signedInUid == null || !payload.isFor(signedInUid)) {
            return false;
        }
        sink.post(payload);
        return true;
    }

    @NonNull
    private static NotificationSpec spec(@NonNull Context app, @NonNull FriendsPushPayload payload) {
        FriendsPushPayload.Content content = payload.compose(app::getString);
        int id = payload.notificationId();
        Intent open = new Intent(app, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_OPEN_INBOX_ID, payload.inboxId);
        PendingIntent tap = PendingIntent.getActivity(app, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationSpec.Builder(NotificationChannels.CHANNEL_FRIENDS, id)
                .title(content.title)
                .body(content.body)
                .publicVersion(content.publicTitle, content.publicBody)
                .contentIntent(tap)
                .autoCancel(true)
                .build();
    }
}
