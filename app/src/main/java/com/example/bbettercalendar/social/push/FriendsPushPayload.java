package com.example.bbettercalendar.social.push;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.social.inbox.InboxItem;

import java.util.Map;

/**
 * Un push de amigos ya leído (F5-u6, D-034): el mapa de datos que envía la Function ({@code PushSender} de u3), sin
 * Android ni Firebase. Push solo de datos, todas las claves son cadenas: {@code to} (uid del destinatario),
 * {@code type} ({@code cheer} | {@code nudge} | {@code request}), {@code inboxId}, {@code fromName}, {@code fromHandle}.
 *
 * <p>El texto lo compone el móvil, localizado ({@link #compose(Strings)}): con el nombre del emisor y una versión
 * pública sin él para la pantalla de bloqueo segura (decisión d7). El id de la notificación sale del {@code inboxId}
 * ({@link #notificationId()}): el mismo aviso repetido sustituye al anterior en vez de apilarse.
 */
public final class FriendsPushPayload {

    public static final String KEY_TO = "to";
    public static final String KEY_TYPE = "type";
    public static final String KEY_INBOX_ID = "inboxId";
    public static final String KEY_FROM_NAME = "fromName";
    public static final String KEY_FROM_HANDLE = "fromHandle";

    /**
     * Ids de notificación de amigos: [40_000, 49_999]. Libre: el servicio en primer plano usa 1, Focus 50_001 y
     * 50_002, los límites de uso 60_000+, los proyectos 70_000+ y los eventos 100_000+.
     */
    public static final int NOTIFICATION_ID_BASE = 40_000;
    public static final int NOTIFICATION_ID_SPAN = 10_000;

    /** Cadenas localizadas (en la app, {@code Context.getString}; en los tests, plantillas fijas). */
    public interface Strings {
        @NonNull
        String get(@StringRes int id, @NonNull Object... args);
    }

    /** Lo que enseña la notificación: privado (con nombre) y público (sin él, d7). */
    public static final class Content {
        @NonNull public final String title;
        @NonNull public final String body;
        @NonNull public final String publicTitle;
        @NonNull public final String publicBody;

        Content(@NonNull String title, @NonNull String body, @NonNull String publicTitle,
                @NonNull String publicBody) {
            this.title = title;
            this.body = body;
            this.publicTitle = publicTitle;
            this.publicBody = publicBody;
        }
    }

    @NonNull public final InboxItem.Type type;
    /** uid del destinatario: si no es la sesión actual, el aviso no se enseña (token de otra cuenta). */
    @NonNull public final String to;
    @NonNull public final String inboxId;
    @NonNull public final String fromName;
    @NonNull public final String fromHandle;

    public FriendsPushPayload(@NonNull InboxItem.Type type, @NonNull String to, @NonNull String inboxId,
                              @NonNull String fromName, @NonNull String fromHandle) {
        this.type = type;
        this.to = to;
        this.inboxId = inboxId;
        this.fromName = fromName;
        this.fromHandle = fromHandle;
    }

    /**
     * Lee el mapa de datos del push. Null (no se enseña nada) si el tipo no es del contrato o falta o está vacía
     * cualquiera de las cinco claves.
     */
    @Nullable
    public static FriendsPushPayload parse(@Nullable Map<String, String> data) {
        if (data == null) {
            return null;
        }
        InboxItem.Type type = InboxItem.Type.fromWire(data.get(KEY_TYPE));
        String to = nonEmpty(data.get(KEY_TO));
        String inboxId = nonEmpty(data.get(KEY_INBOX_ID));
        String fromName = nonEmpty(data.get(KEY_FROM_NAME));
        String fromHandle = nonEmpty(data.get(KEY_FROM_HANDLE));
        if (type == null || to == null || inboxId == null || fromName == null || fromHandle == null) {
            return null;
        }
        return new FriendsPushPayload(type, to, inboxId, fromName, fromHandle);
    }

    @Nullable
    private static String nonEmpty(@Nullable String s) {
        return s == null || s.trim().isEmpty() ? null : s;
    }

    /** true si el aviso es para la sesión {@code signedInUid} (null = sin sesión: nunca). */
    public boolean isFor(@Nullable String signedInUid) {
        return to.equals(signedInUid);
    }

    /** Id de la notificación en [40_000, 49_999], estable para el mismo {@code inboxId}. */
    public int notificationId() {
        return NOTIFICATION_ID_BASE + Math.floorMod(inboxId.hashCode(), NOTIFICATION_ID_SPAN);
    }

    /** Título y cuerpo según el tipo, con el nombre del emisor, y la versión pública sin él (d7). */
    @NonNull
    public Content compose(@NonNull Strings strings) {
        int title;
        int body;
        switch (type) {
            case CHEER:
                title = R.string.push_cheer_title;
                body = R.string.push_cheer_body;
                break;
            case NUDGE:
                title = R.string.push_nudge_title;
                body = R.string.push_nudge_body;
                break;
            case REQUEST:
            default:
                title = R.string.push_request_title;
                body = R.string.push_request_body;
                break;
        }
        return new Content(
                strings.get(title, fromName, fromHandle),
                strings.get(body, fromName, fromHandle),
                strings.get(R.string.push_public_title),
                strings.get(R.string.push_public_body));
    }
}
