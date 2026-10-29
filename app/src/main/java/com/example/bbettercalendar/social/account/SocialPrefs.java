package com.example.bbettercalendar.social.account;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.social.common.SerialExecutor;
import com.example.bbettercalendar.social.invite.InviteCodes;
import com.example.bbettercalendar.social.invite.PendingInvite;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Preferencias locales del sistema social (F1-u2): año de nacimiento (se guarda una sola vez) y
 * nombre local de Home. SharedPreferences, nada de Room. Lecturas y escrituras van en el
 * {@code @IoExecutor}; las escrituras usan {@code apply()}.
 *
 * <p>F2-u6: también la invitación pendiente ({@code pending_invite} + fecha; caduca a los 7 días,
 * {@link PendingInvite}) que dejan {@code InviteLinkActivity} (App Link) e {@code InstallReferrerReader}
 * (Play), y la bandera de que el referrer ya se leyó. Todas las tareas pasan por un {@link SerialExecutor}
 * sobre el {@code @IoExecutor}: una lectura pedida después de una escritura la ve siempre (la actividad
 * del enlace guarda el código y relanza la app, que lo lee al volver a primer plano).
 */
@Singleton
public class SocialPrefs {

    public static final String FILE_NAME = "bb_social_prefs";
    private static final String KEY_BIRTH_YEAR = "birth_year";
    private static final String KEY_LOCAL_NAME = "local_name";
    // F2-u6
    public static final String KEY_PENDING_INVITE = "pending_invite";
    public static final String KEY_PENDING_INVITE_AT = "pending_invite_at";
    private static final String KEY_REFERRER_CHECKED = "install_referrer_checked";
    // F3-u4: última publicación de stats/ (una sola cuenta: la de la última publicación)
    private static final String KEY_STATS_LAST_UID = "stats_last_uid";
    private static final String KEY_STATS_LAST_PUBLISHED = "stats_last_published";

    private final Context appContext;
    private final Executor io;

    @Inject
    public SocialPrefs(@ApplicationContext Context context, @IoExecutor ExecutorService io) {
        this.appContext = context;
        this.io = new SerialExecutor(io);
    }

    private SharedPreferences prefs() {
        return appContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    /** Entrega el año guardado (o null) en el hilo de E/S; el receptor debe usar postValue. */
    public void readBirthYear(Consumer<Integer> callback) {
        io.execute(() -> {
            SharedPreferences p = prefs();
            callback.accept(p.contains(KEY_BIRTH_YEAR) ? Integer.valueOf(p.getInt(KEY_BIRTH_YEAR, 0)) : null);
        });
    }

    /** Guarda el año solo si aún no hay uno: no se puede editar desde la UI. */
    public void saveBirthYearOnce(int birthYear) {
        io.execute(() -> {
            SharedPreferences p = prefs();
            if (!p.contains(KEY_BIRTH_YEAR)) {
                p.edit().putInt(KEY_BIRTH_YEAR, birthYear).apply();
            }
        });
    }

    /** Entrega el nombre local (o null) en el hilo de E/S. */
    public void readLocalName(Consumer<String> callback) {
        io.execute(() -> callback.accept(prefs().getString(KEY_LOCAL_NAME, null)));
    }

    /** Guarda el nombre local; null o vacío lo borra. */
    public void saveLocalName(@Nullable String name) {
        io.execute(() -> {
            SharedPreferences.Editor e = prefs().edit();
            if (name == null || name.trim().isEmpty()) {
                e.remove(KEY_LOCAL_NAME);
            } else {
                e.putString(KEY_LOCAL_NAME, name.trim());
            }
            e.apply();
        });
    }

    // ---- F2-u6: invitación pendiente ---------------------------------------------------------------

    /**
     * Guarda {@code code} como invitación pendiente con la hora actual (la de antes se sustituye). Un
     * código sin formato no se guarda.
     */
    public void savePendingInvite(@Nullable String code) {
        String c = InviteCodes.normalize(code);
        if (c == null) {
            return;
        }
        long now = System.currentTimeMillis();
        io.execute(() -> prefs().edit()
                .putString(KEY_PENDING_INVITE, c)
                .putLong(KEY_PENDING_INVITE_AT, now)
                .apply());
    }

    /**
     * Entrega la invitación pendiente vigente (o null) en el hilo de E/S. Una caducada (7 días) se borra.
     * Leerla no la borra: quien la use llama a {@link #clearPendingInvite()}.
     */
    public void readPendingInvite(Consumer<String> callback) {
        io.execute(() -> {
            SharedPreferences p = prefs();
            String code = InviteCodes.normalize(p.getString(KEY_PENDING_INVITE, null));
            long at = p.getLong(KEY_PENDING_INVITE_AT, 0L);
            if (code != null && PendingInvite.isFresh(at, System.currentTimeMillis())) {
                callback.accept(code);
                return;
            }
            if (p.contains(KEY_PENDING_INVITE) || p.contains(KEY_PENDING_INVITE_AT)) {
                p.edit().remove(KEY_PENDING_INVITE).remove(KEY_PENDING_INVITE_AT).apply();
            }
            callback.accept(null);
        });
    }

    public void clearPendingInvite() {
        io.execute(() -> prefs().edit().remove(KEY_PENDING_INVITE).remove(KEY_PENDING_INVITE_AT).apply());
    }

    /** true (en el hilo de E/S) si el referrer de Play ya se leyó: {@code InstallReferrerReader} va una vez. */
    public void readReferrerChecked(Consumer<Boolean> callback) {
        io.execute(() -> callback.accept(prefs().getBoolean(KEY_REFERRER_CHECKED, false)));
    }

    public void markReferrerChecked() {
        io.execute(() -> prefs().edit().putBoolean(KEY_REFERRER_CHECKED, true).apply());
    }

    // ---- F3-u4: última publicación de stats/{uid} ----------------------------------------------------------

    /**
     * Huella de lo último que se publicó en {@code stats/{uid}} para {@code uid} ({@code dayKey|weekKey|valores},
     * {@code StatsWrites.fingerprint}), o null si nunca se publicó o la última fue de otra cuenta. Síncrona: la
     * llama {@code StatsPublisher} desde su serial sobre el {@code @IoExecutor}, nunca el hilo principal.
     */
    @WorkerThread
    @Nullable
    public String readLastStatsPublished(@NonNull String uid) {
        SharedPreferences p = prefs();
        return uid.equals(p.getString(KEY_STATS_LAST_UID, null)) ? p.getString(KEY_STATS_LAST_PUBLISHED, null) : null;
    }

    /** Guarda la huella de la última publicación de {@code uid}. Síncrona como la lectura ({@code apply()}). */
    @WorkerThread
    public void saveLastStatsPublished(@NonNull String uid, @NonNull String fingerprint) {
        prefs().edit()
                .putString(KEY_STATS_LAST_UID, uid)
                .putString(KEY_STATS_LAST_PUBLISHED, fingerprint)
                .apply();
    }
}
