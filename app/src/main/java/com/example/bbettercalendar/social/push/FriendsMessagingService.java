package com.example.bbettercalendar.social.push;

import androidx.annotation.NonNull;

import com.example.bbettercalendar.BuildConfig;
import com.example.bbettercalendar.social.SocialFeature;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import javax.inject.Inject;

import dagger.Lazy;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * Recibe los push de amigos de FCM (F5-u6, D-034). No hace nada sin {@code FEATURE_SOCIAL}
 * ({@link SocialFeature#isAccountEnabled()}). Un push de datos se lee con {@link FriendsPushPayload#parse} y se
 * enseña con {@link FriendsPushHandler#show}, que descarta lo que no es de la sesión actual. Un token nuevo va a
 * {@link PushTokenRegistrar#onNewToken(String)}.
 */
@AndroidEntryPoint
public class FriendsMessagingService extends FirebaseMessagingService {

    // Lazy: sin FEATURE_SOCIAL (release) el registrador nunca se crea.
    @Inject Lazy<PushTokenRegistrar> registrar;

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        if (!SocialFeature.isAccountEnabled()) {
            return;
        }
        FriendsPushHandler.show(this, FriendsPushPayload.parse(message.getData()));
    }

    @Override
    public void onNewToken(@NonNull String token) {
        // En las compilaciones de emulador el token registrado es el falso (d8): uno real de FCM no se guarda.
        if (!SocialFeature.isAccountEnabled() || BuildConfig.USE_FIREBASE_EMULATOR) {
            return;
        }
        registrar.get().onNewToken(token);
    }
}
