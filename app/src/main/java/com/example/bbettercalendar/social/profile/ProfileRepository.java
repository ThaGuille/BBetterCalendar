package com.example.bbettercalendar.social.profile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.social.account.AgeGate;
import com.example.bbettercalendar.social.common.FirestoreDocumentSource;
import com.example.bbettercalendar.social.common.ListenerException;
import com.example.bbettercalendar.social.common.ListenerSource;
import com.example.bbettercalendar.social.common.MainLooperScheduler;
import com.example.bbettercalendar.social.common.ProcessForeground;
import com.example.bbettercalendar.social.common.Scheduler;
import com.example.bbettercalendar.social.common.SerialExecutor;
import com.example.bbettercalendar.social.common.WatchedListener;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.util.Date;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Datos del perfil propio (F1-u3, 02 §3, 03 §5, D-019, D-022): observar, crear, editar y renombrar.
 *
 * <p>Contrato con {@code firebase/firestore.rules} (F1-u1): crear, renombrar y liberar van en un solo
 * {@link WriteBatch} y las reglas leen el estado final; los mapas salen de {@link ProfileWrites}.
 *
 * <p>Regla #3: el listener de Firestore y los de cada tarea corren en un {@link SerialExecutor} propio
 * encima del {@code @IoExecutor} (F2-u1: uno por repositorio); el estado se publica con {@code postValue}.
 * Los {@link Callback} llegan en ese hilo de E/S: quien los reciba debe usar {@code postValue}.
 *
 * <p>F2-u1: el perfil propio se escucha con un {@link WatchedListener} (generación + reenganche), así
 * que un evento de una sesión anterior nunca pisa el {@code SIGNED_OUT} ni deja un {@code LOADING}
 * colgado, y tras un error el listener vuelve solo (espera creciente, o al volver a primer plano).
 */
@Singleton
public class ProfileRepository {

    public interface Callback {
        void onSuccess();

        void onError(@NonNull Throwable error);
    }

    public interface HandleFreeCallback {
        void onResult(boolean free);

        void onError(@NonNull Throwable error);
    }

    public interface TermsCallback {
        /** {@code termsVersion} guardado en {@code users/{uid}}, o null si no existe el documento. */
        void onResult(@Nullable Integer termsVersion);

        void onError(@NonNull Throwable error);
    }

    /** Fallos que se detectan en el cliente antes de escribir. Los de Firestore llegan tal cual. */
    public static final class ProfileException extends Exception {

        public enum Code {
            NOT_SIGNED_IN, INVALID_NAME, INVALID_HANDLE, INVALID_COLOR, INVALID_AGE_BAND,
            NO_PROFILE, SAME_HANDLE, RENAME_TOO_SOON
        }

        @NonNull
        public final Code code;

        public ProfileException(@NonNull Code code) {
            super(code.name());
            this.code = code;
        }
    }

    public static final int AVATAR_COLORS = 8;

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;
    /** F2-u1: un solo {@link SerialExecutor} por repositorio para el listener y los callbacks. */
    private final Executor ioExecutor;
    private final MutableLiveData<ProfileState> mine;
    private final WatchedListener<String, DocumentSnapshot> watcher;

    @Inject
    public ProfileRepository(FirebaseAuth auth, FirebaseFirestore db,
                             @IoExecutor ExecutorService ioExecutor) {
        this(auth, db, new SerialExecutor(ioExecutor), new MainLooperScheduler(),
                new FirestoreDocumentSource(uid -> db.collection(ProfileWrites.PROFILES).document(uid)));
        // Tras un error no reintentable, el listener vuelve a engancharse al volver a primer plano.
        ProcessForeground.observe(watcher::onForeground);
    }

    /** Para tests: el listener del perfil llega por la interfaz propia {@link ListenerSource}. */
    ProfileRepository(@NonNull FirebaseAuth auth, @NonNull FirebaseFirestore db, @NonNull Executor serial,
                      @NonNull Scheduler scheduler, @NonNull ListenerSource<String, DocumentSnapshot> source) {
        this.auth = auth;
        this.db = db;
        this.ioExecutor = serial;
        this.mine = new MutableLiveData<>(auth.getCurrentUser() == null
                ? ProfileState.signedOut() : ProfileState.loading());
        this.watcher = new WatchedListener<>(serial, scheduler, source, new ProfileSink());
        // Se dispara al registrarse y en cada cambio de sesión: desengancha y vuelve a enganchar.
        auth.addAuthStateListener(a -> watch(a.getCurrentUser()));
    }

    // ---- observar --------------------------------------------------------------------------------------

    /** Perfil propio en vivo; sigue a la sesión (sin sesión → {@code SIGNED_OUT}). */
    public LiveData<ProfileState> observeMine() {
        return mine;
    }

    private void watch(@Nullable FirebaseUser user) {
        if (user == null) {
            watcher.detach();
        } else {
            watcher.attach(user.getUid()); // misma sesión ya enganchada: no hace nada
        }
    }

    /** Traduce los estados del {@link WatchedListener} a {@link ProfileState}; corre en el serial. */
    private final class ProfileSink implements WatchedListener.Sink<DocumentSnapshot> {

        @Override
        public void onLoading() {
            mine.postValue(ProfileState.loading());
        }

        @Override
        public void onDetached() {
            mine.postValue(ProfileState.signedOut());
        }

        @Override
        public void onError(@NonNull ListenerException error) {
            mine.postValue(ProfileState.error(error.original()));
        }

        @Override
        public void onValue(@NonNull DocumentSnapshot snap) {
            if (snap.exists()) {
                Profile p = toProfile(snap.getId(), snap);
                mine.postValue(p == null ? ProfileState.error(new IllegalStateException("malformed profile"))
                        : ProfileState.ready(p));
            } else if (snap.getMetadata().isFromCache()) {
                mine.postValue(ProfileState.loading()); // la caché no sabe; esperamos al servidor
            } else {
                mine.postValue(ProfileState.none());
            }
        }
    }

    @Nullable
    static Profile toProfile(@NonNull String uid, @NonNull DocumentSnapshot snap) {
        String name = snap.getString(ProfileWrites.DISPLAY_NAME);
        String handle = snap.getString(ProfileWrites.HANDLE);
        Long color = snap.getLong(ProfileWrites.AVATAR_COLOR);
        if (name == null || handle == null || color == null) {
            return null;
        }
        // ESTIMATE: justo tras escribir, el timestamp del servidor aún está pendiente.
        return new Profile(uid, name, handle, color.intValue(),
                date(snap, ProfileWrites.CREATED_AT), date(snap, ProfileWrites.HANDLE_CHANGED_AT));
    }

    @Nullable
    private static Date date(@NonNull DocumentSnapshot snap, @NonNull String field) {
        Timestamp t = snap.getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
        return t == null ? null : t.toDate();
    }

    // ---- @usuario --------------------------------------------------------------------------------------

    /**
     * true si {@code handles/{h}} no existe (lectura del servidor, no de la caché). El {@code @usuario}
     * se normaliza antes; uno inválido es un error {@code INVALID_HANDLE}.
     */
    public void isHandleFree(@Nullable String rawHandle, @NonNull HandleFreeCallback callback) {
        String h = HandleValidator.normalizeHandle(rawHandle);
        if (!HandleValidator.isValidHandle(h)) {
            ioExecutor.execute(() -> callback.onError(
                    new ProfileException(ProfileException.Code.INVALID_HANDLE)));
            return;
        }
        handleRef(h).get(Source.SERVER)
                .addOnSuccessListener(ioExecutor, snap -> callback.onResult(!snap.exists()))
                .addOnFailureListener(ioExecutor, callback::onError);
    }

    /** Desde cuándo puede renombrar este perfil (D-019, D-022); ahora si nunca lo ha hecho. */
    @NonNull
    public Date canRenameAt(@NonNull Profile profile) {
        return profile.canRenameAt(new Date());
    }

    // ---- escribir --------------------------------------------------------------------------------------

    /**
     * Crea {@code users/{uid}}, {@code profiles/{uid}} y {@code handles/{h}} en un solo lote. Si el
     * {@code @usuario} ya es de otro, las reglas rechazan el lote entero (PERMISSION_DENIED).
     */
    public void create(@Nullable String rawName, @Nullable String rawHandle, int avatarColor,
                       @NonNull String ageBand, int termsVersion, @NonNull Callback callback) {
        String uid = currentUid();
        String name = HandleValidator.normalizeDisplayName(rawName);
        String handle = HandleValidator.normalizeHandle(rawHandle);
        ProfileException invalid = uid == null ? new ProfileException(ProfileException.Code.NOT_SIGNED_IN)
                : !HandleValidator.isValidDisplayName(name) ? new ProfileException(ProfileException.Code.INVALID_NAME)
                : !HandleValidator.isValidHandle(handle) ? new ProfileException(ProfileException.Code.INVALID_HANDLE)
                : !validColor(avatarColor) ? new ProfileException(ProfileException.Code.INVALID_COLOR)
                : !validAgeBand(ageBand) ? new ProfileException(ProfileException.Code.INVALID_AGE_BAND)
                : null;
        if (invalid != null) {
            fail(callback, invalid);
            return;
        }
        WriteBatch batch = db.batch();
        batch.set(userRef(uid), ProfileWrites.userCreate(ageBand, termsVersion));
        batch.set(profileRef(uid), ProfileWrites.profileCreate(name, handle, avatarColor));
        batch.set(handleRef(handle), ProfileWrites.handleCreate(uid, name, avatarColor));
        commit(batch, callback);
    }

    /** Cambia nombre y color del perfil y, en el mismo lote, los campos copiados en {@code handles/}. */
    public void update(@Nullable String rawName, int avatarColor, @NonNull Callback callback) {
        String name = HandleValidator.normalizeDisplayName(rawName);
        if (!HandleValidator.isValidDisplayName(name)) {
            fail(callback, new ProfileException(ProfileException.Code.INVALID_NAME));
            return;
        }
        if (!validColor(avatarColor)) {
            fail(callback, new ProfileException(ProfileException.Code.INVALID_COLOR));
            return;
        }
        withCurrentProfile(callback, current -> {
            WriteBatch batch = db.batch();
            batch.update(profileRef(current.uid), ProfileWrites.profileUpdate(name, avatarColor));
            batch.update(handleRef(current.handle), ProfileWrites.handleUpdate(name, avatarColor));
            commit(batch, callback);
        });
    }

    /**
     * Renombra el {@code @usuario} (D-019): en un lote, el perfil apunta al nuevo con
     * {@code handleChangedAt = serverTimestamp()}, se reserva {@code handles/{nuevo}} y se libera
     * {@code handles/{viejo}}. El límite de 30 días se mira aquí para dar un error claro, pero quien
     * manda son las reglas (hora del servidor).
     */
    public void rename(@Nullable String rawNewHandle, @NonNull Callback callback) {
        String newHandle = HandleValidator.normalizeHandle(rawNewHandle);
        if (!HandleValidator.isValidHandle(newHandle)) {
            fail(callback, new ProfileException(ProfileException.Code.INVALID_HANDLE));
            return;
        }
        withCurrentProfile(callback, current -> {
            if (newHandle.equals(current.handle)) {
                callback.onError(new ProfileException(ProfileException.Code.SAME_HANDLE));
                return;
            }
            if (!current.canRenameNow(new Date())) {
                callback.onError(new ProfileException(ProfileException.Code.RENAME_TOO_SOON));
                return;
            }
            WriteBatch batch = db.batch();
            batch.update(profileRef(current.uid), ProfileWrites.profileRename(newHandle));
            batch.set(handleRef(newHandle),
                    ProfileWrites.handleCreate(current.uid, current.displayName, current.avatarColor));
            batch.delete(handleRef(current.handle));
            commit(batch, callback);
        });
    }

    /** Lee {@code termsVersion} de {@code users/{uid}} para {@code SocialEntryRouter} (re-aceptación). */
    public void readTermsVersion(@NonNull TermsCallback callback) {
        String uid = currentUid();
        if (uid == null) {
            ioExecutor.execute(() -> callback.onError(
                    new ProfileException(ProfileException.Code.NOT_SIGNED_IN)));
            return;
        }
        userRef(uid).get()
                .addOnSuccessListener(ioExecutor, snap -> {
                    Long v = snap.exists() ? snap.getLong(ProfileWrites.TERMS_VERSION) : null;
                    callback.onResult(v == null ? null : v.intValue());
                })
                .addOnFailureListener(ioExecutor, callback::onError);
    }

    /** Vuelve a aceptar los términos: {@code termsVersion} nuevo y {@code termsAcceptedAt} del servidor. */
    public void acceptTerms(int termsVersion, @NonNull Callback callback) {
        String uid = currentUid();
        if (uid == null) {
            fail(callback, new ProfileException(ProfileException.Code.NOT_SIGNED_IN));
            return;
        }
        userRef(uid).update(ProfileWrites.termsAccept(termsVersion))
                .addOnSuccessListener(ioExecutor, v -> callback.onSuccess())
                .addOnFailureListener(ioExecutor, callback::onError);
    }

    // ---- utilidades ------------------------------------------------------------------------------------

    private interface ProfileAction {
        void run(@NonNull Profile current);
    }

    /** Lee el perfil propio del servidor (no de lo último observado) y sigue con él en el hilo de E/S. */
    private void withCurrentProfile(@NonNull Callback callback, @NonNull ProfileAction action) {
        String uid = currentUid();
        if (uid == null) {
            fail(callback, new ProfileException(ProfileException.Code.NOT_SIGNED_IN));
            return;
        }
        profileRef(uid).get(Source.SERVER)
                .addOnSuccessListener(ioExecutor, snap -> {
                    Profile current = snap.exists() ? toProfile(uid, snap) : null;
                    if (current == null) {
                        callback.onError(new ProfileException(ProfileException.Code.NO_PROFILE));
                    } else {
                        action.run(current);
                    }
                })
                .addOnFailureListener(ioExecutor, callback::onError);
    }

    private void commit(@NonNull WriteBatch batch, @NonNull Callback callback) {
        batch.commit()
                .addOnSuccessListener(ioExecutor, v -> callback.onSuccess())
                .addOnFailureListener(ioExecutor, callback::onError);
    }

    private void fail(@NonNull Callback callback, @NonNull Throwable error) {
        ioExecutor.execute(() -> callback.onError(error));
    }

    private static boolean validColor(int color) {
        return color >= 0 && color < AVATAR_COLORS;
    }

    private static boolean validAgeBand(@NonNull String band) {
        return AgeGate.BAND_TEEN.equals(band) || AgeGate.BAND_ADULT.equals(band);
    }

    @Nullable
    private String currentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    private DocumentReference userRef(@NonNull String uid) {
        return db.collection(ProfileWrites.USERS).document(uid);
    }

    private DocumentReference profileRef(@NonNull String uid) {
        return db.collection(ProfileWrites.PROFILES).document(uid);
    }

    private DocumentReference handleRef(@NonNull String handle) {
        return db.collection(ProfileWrites.HANDLES).document(handle);
    }
}
