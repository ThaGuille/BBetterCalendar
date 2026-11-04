package com.example.bbettercalendar.social.push;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Selección pura de los avisos de Amigos (L-u7): de los ids de las notificaciones activas de la app, los que son
 * de Amigos, es decir, los de [40_000, 49_999] ({@link FriendsPushPayload#NOTIFICATION_ID_BASE}).
 */
public final class FriendsNotifications {

    private FriendsNotifications() {
    }

    public static boolean isFriendsId(int id) {
        return id >= FriendsPushPayload.NOTIFICATION_ID_BASE
                && id < FriendsPushPayload.NOTIFICATION_ID_BASE + FriendsPushPayload.NOTIFICATION_ID_SPAN;
    }

    /** Los ids de Amigos entre los activos, en el mismo orden; vacío si no hay ninguno. */
    @NonNull
    public static List<Integer> idsToCancel(@NonNull int[] activeIds) {
        List<Integer> out = new ArrayList<>();
        for (int id : activeIds) {
            if (isFriendsId(id)) {
                out.add(id);
            }
        }
        return out;
    }
}
