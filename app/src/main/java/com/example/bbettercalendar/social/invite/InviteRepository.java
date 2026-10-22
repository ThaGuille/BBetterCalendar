package com.example.bbettercalendar.social.invite;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.social.common.ListenerException;
import com.example.bbettercalendar.social.common.SerialExecutor;
import com.example.bbettercalendar.social.friends.FirestoreFriendsBackend;
import com.example.bbettercalendar.social.friends.FriendCard;
import com.example.bbettercalendar.social.friends.FriendRequestWrites;
import com.example.bbettercalendar.social.friends.FriendsBackend;
import com.example.bbettercalendar.social.friends.FriendsMapping;
import com.example.bbettercalendar.social.profile.ProfileWrites;

import java.security.SecureRandom;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Invitaciones en Firestore (F2-u6, D-028 §3): el código propio y resolver el de un enlace.
 *
 * <ul>
 *   <li>{@link #ensureMyCode}: lee {@code users/me.inviteCode} del servidor; si no hay, genera uno y escribe
 *       en un lote {@code invites/{código}} y {@code users/me.inviteCode} (las reglas de F2-u3 leen el estado
 *       final). Un código que ya es de otra persona choca: el lote sería un update de su {@code invites/}, que
 *       las reglas deniegan. Se mira antes con un {@code get} y, si aun así el lote se deniega (carrera), se
 *       vuelve a empezar leyendo {@code users/me}; como mucho {@link #MAX_COLLISION_RETRIES} reintentos.</li>
 *   <li>{@link #resolve}: {@code invites/{código}} → {@code handles/{handle}}; si el {@code @usuario} ya no
 *       existe o es de otra persona (renombró sin el lote de F2-u6, o borró la cuenta), la invitación caducó.</li>
 * </ul>
 * Firebase solo a través de {@link FriendsBackend} (la interfaz propia de F2-u5), así que los tests lo simulan
 * con Mockito. Regla #3: un {@link SerialExecutor} propio sobre el {@code @IoExecutor}; los callbacks llegan en
 * ese hilo de E/S (quien los reciba usa {@code postValue}).
 */
@Singleton
public class InviteRepository {

    public interface CodeCallback {
        /** El código propio, ya guardado en {@code invites/} y en {@code users/me}. */
        void onCode(@NonNull String code);

        void onError(@NonNull Throwable error);
    }

    public interface ResolveCallback {
        void onResolved(@NonNull Resolution resolution);

        void onError(@NonNull Throwable error);
    }

    /** Lo que es un código de invitación para quien lo abre. */
    public static final class Resolution {

        public enum Kind {
            /** De otra persona y vigente: {@link #card} es su tarjeta de {@code handles/}. */
            VALID,
            /** Es tu propio enlace. */
            SELF,
            /** No existe, no tiene formato de código o su {@code @usuario} ya no es de quien lo creó. */
            EXPIRED
        }

        @NonNull
        public final Kind kind;
        /** El código normalizado; null si no tenía formato de código. */
        @Nullable
        public final String code;
        /** Con {@link Kind#VALID}, la tarjeta de quien invita. */
        @Nullable
        public final FriendCard card;

        Resolution(@NonNull Kind kind, @Nullable String code, @Nullable FriendCard card) {
            this.kind = kind;
            this.code = code;
            this.card = card;
        }
    }

    /** Fallos que se detectan en el cliente. Los de Firestore llegan como {@link ListenerException}. */
    public static final class InviteException extends Exception {

        public enum Code {
            NOT_SIGNED_IN,
            /** Sin {@code users/me} o sin {@code profiles/me}: el código lleva el {@code @usuario} del perfil. */
            NO_PROFILE,
            /** Todos los intentos chocaron con códigos de otras personas. */
            NO_FREE_CODE
        }

        @NonNull
        public final Code code;

        public InviteException(@NonNull Code code) {
            super(code.name());
            this.code = code;
        }
    }

    /** Reintentos tras el primer intento cuando el código ya existe (plan F2-u6: como mucho 3). */
    public static final int MAX_COLLISION_RETRIES = 3;
    static final String PERMISSION_DENIED = "PERMISSION_DENIED";
    /** Formato de {@code handles/{h}} en las reglas; otro valor no sería una ruta de documento válida. */
    private static final Pattern HANDLE_FORMAT = Pattern.compile("^[a-z0-9_.]{3,20}$");

    private final FriendsBackend backend;
    private final Executor serial;
    private final SecureRandom random;

    @Inject
    public InviteRepository(FirestoreFriendsBackend backend, @IoExecutor ExecutorService ioExecutor) {
        this(backend, new SerialExecutor(ioExecutor), new SecureRandom());
    }

    /** Para tests: Firebase solo por {@link FriendsBackend}; el azar se inyecta. */
    InviteRepository(@NonNull FriendsBackend backend, @NonNull Executor serial, @NonNull SecureRandom random) {
        this.backend = backend;
        this.serial = serial;
        this.random = random;
    }

    /** El enlace de un código: {@code https://bbetter-dbcda.web.app/i/<código>}. */
    @NonNull
    public static String linkFor(@NonNull String code) {
        return InviteLinkFactory.link(code);
    }

    // ---- código propio -------------------------------------------------------------------------------------

    /** Devuelve el código propio y lo crea si aún no existe. */
    public void ensureMyCode(@NonNull CodeCallback callback) {
        serial.execute(() -> {
            String me = backend.currentUid();
            if (me == null) {
                callback.onError(new InviteException(InviteException.Code.NOT_SIGNED_IN));
                return;
            }
            readMyCode(me, 0, null, callback);
        });
    }

    /** Paso 1: {@code users/me.inviteCode}. {@code handle} se lee una vez y se reutiliza en los reintentos. */
    private void readMyCode(@NonNull String me, int retries, @Nullable String handle, @NonNull CodeCallback callback) {
        backend.getFromServer(FriendRequestWrites.userPath(me), serial, new FriendsBackend.Result<FriendsBackend.Doc>() {
            @Override
            public void onSuccess(@Nullable FriendsBackend.Doc doc) {
                if (doc == null || !doc.exists()) {
                    callback.onError(new InviteException(InviteException.Code.NO_PROFILE));
                    return;
                }
                Object existing = doc.data.get(InviteWrites.INVITE_CODE);
                if (existing instanceof String && InviteCodes.isValid((String) existing)) {
                    callback.onCode((String) existing);
                } else if (handle != null) {
                    tryCode(me, handle, retries, callback);
                } else {
                    readHandle(me, callback);
                }
            }

            @Override
            public void onError(@NonNull Throwable error) {
                callback.onError(error);
            }
        });
    }

    /** Paso 2: el {@code @usuario} del perfil, que las reglas comparan con {@code invites/{código}.handle}. */
    private void readHandle(@NonNull String me, @NonNull CodeCallback callback) {
        backend.getFromServer(FriendRequestWrites.profilePath(me), serial, new FriendsBackend.Result<FriendsBackend.Doc>() {
            @Override
            public void onSuccess(@Nullable FriendsBackend.Doc doc) {
                Object handle = doc == null || doc.data == null ? null : doc.data.get(ProfileWrites.HANDLE);
                if (handle instanceof String) {
                    tryCode(me, (String) handle, 0, callback);
                } else {
                    callback.onError(new InviteException(InviteException.Code.NO_PROFILE));
                }
            }

            @Override
            public void onError(@NonNull Throwable error) {
                callback.onError(error);
            }
        });
    }

    /** Paso 3: un código al azar; si ya existe, otro (como mucho {@link #MAX_COLLISION_RETRIES} veces más). */
    private void tryCode(@NonNull String me, @NonNull String handle, int retries, @NonNull CodeCallback callback) {
        String code = InviteCodes.generate(random);
        backend.getFromServer(InviteWrites.invitePath(code), serial, new FriendsBackend.Result<FriendsBackend.Doc>() {
            @Override
            public void onSuccess(@Nullable FriendsBackend.Doc doc) {
                if (doc != null && doc.exists()) {
                    collision(me, handle, retries, false, callback);
                } else {
                    writeCode(me, handle, code, retries, callback);
                }
            }

            @Override
            public void onError(@NonNull Throwable error) {
                callback.onError(error);
            }
        });
    }

    /** Paso 4: {@code invites/{código}} y {@code users/me.inviteCode} en un solo lote. */
    private void writeCode(@NonNull String me, @NonNull String handle, @NonNull String code, int retries,
                           @NonNull CodeCallback callback) {
        backend.batch()
                .set(InviteWrites.invitePath(code), InviteWrites.inviteCreate(me, handle))
                .update(FriendRequestWrites.userPath(me), InviteWrites.userInviteCode(code))
                .commit(serial, new FriendsBackend.Result<Void>() {
                    @Override
                    public void onSuccess(@Nullable Void value) {
                        callback.onCode(code);
                    }

                    @Override
                    public void onError(@NonNull Throwable error) {
                        if (isPermissionDenied(error)) {
                            // Otro lo creó entre el get y el lote, u otra llamada ya guardó un código: se
                            // vuelve a leer users/me (si ya tiene código, se devuelve ese).
                            collision(me, handle, retries, true, callback);
                        } else {
                            callback.onError(error);
                        }
                    }
                });
    }

    private void collision(@NonNull String me, @NonNull String handle, int retries, boolean reread,
                           @NonNull CodeCallback callback) {
        if (retries >= MAX_COLLISION_RETRIES) {
            callback.onError(new InviteException(InviteException.Code.NO_FREE_CODE));
        } else if (reread) {
            readMyCode(me, retries + 1, handle, callback);
        } else {
            tryCode(me, handle, retries + 1, callback);
        }
    }

    // ---- resolver -----------------------------------------------------------------------------------------

    /** Qué es {@code rawCode} (de un App Link o del referrer) para quien tiene sesión. */
    public void resolve(@Nullable String rawCode, @NonNull ResolveCallback callback) {
        serial.execute(() -> {
            String me = backend.currentUid();
            if (me == null) {
                callback.onError(new InviteException(InviteException.Code.NOT_SIGNED_IN));
                return;
            }
            String code = InviteCodes.normalize(rawCode);
            if (code == null) {
                callback.onResolved(new Resolution(Resolution.Kind.EXPIRED, null, null));
                return;
            }
            backend.getFromServer(InviteWrites.invitePath(code), serial, new FriendsBackend.Result<FriendsBackend.Doc>() {
                @Override
                public void onSuccess(@Nullable FriendsBackend.Doc doc) {
                    Object uid = doc == null || doc.data == null ? null : doc.data.get(InviteWrites.UID);
                    Object handle = doc == null || doc.data == null ? null : doc.data.get(InviteWrites.HANDLE);
                    if (!(uid instanceof String) || !(handle instanceof String)
                            || !HANDLE_FORMAT.matcher((String) handle).matches()) {
                        callback.onResolved(new Resolution(Resolution.Kind.EXPIRED, code, null));
                    } else if (me.equals(uid)) {
                        callback.onResolved(new Resolution(Resolution.Kind.SELF, code, null));
                    } else {
                        resolveHandle(code, (String) uid, (String) handle, callback);
                    }
                }

                @Override
                public void onError(@NonNull Throwable error) {
                    callback.onError(error);
                }
            });
        });
    }

    private void resolveHandle(@NonNull String code, @NonNull String uid, @NonNull String handle,
                               @NonNull ResolveCallback callback) {
        backend.getFromServer(FriendRequestWrites.handlePath(handle), serial, new FriendsBackend.Result<FriendsBackend.Doc>() {
            @Override
            public void onSuccess(@Nullable FriendsBackend.Doc doc) {
                FriendCard card = doc == null ? null : FriendsMapping.handleCard(doc);
                if (card == null || !card.uid.equals(uid)) {
                    callback.onResolved(new Resolution(Resolution.Kind.EXPIRED, code, null));
                } else {
                    callback.onResolved(new Resolution(Resolution.Kind.VALID, code, card));
                }
            }

            @Override
            public void onError(@NonNull Throwable error) {
                callback.onError(error);
            }
        });
    }

    static boolean isPermissionDenied(@NonNull Throwable error) {
        return error instanceof ListenerException && PERMISSION_DENIED.equals(((ListenerException) error).code);
    }
}
