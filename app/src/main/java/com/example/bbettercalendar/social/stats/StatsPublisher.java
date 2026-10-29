package com.example.bbettercalendar.social.stats;

import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.social.SocialFeature;
import com.example.bbettercalendar.social.account.SocialPrefs;
import com.example.bbettercalendar.social.common.SerialExecutor;
import com.example.bbettercalendar.social.profile.ProfileRepository;
import com.example.bbettercalendar.social.profile.ProfileState;
import com.example.bbettercalendar.stats.DayMinutes;
import com.example.bbettercalendar.stats.FocusEventDAO;
import com.example.bbettercalendar.stats.StreakCalculator;
import com.example.bbettercalendar.stats.WeeklyGoalStore;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Calcula y publica {@code stats/{uid}} (F3-u4, 03 §9, D-030, D-031).
 *
 * <p><b>Cuándo escribe.</b> Nunca sin {@code FEATURE_SOCIAL} ({@link SocialFeature#isAccountEnabled()}), una
 * sesión y el perfil propio en {@code READY} ({@link ProfileRepository#observeMine()}); el uid del perfil tiene
 * que ser el de la sesión. Disparadores:
 * <ul>
 *   <li>{@link #requestPublish()}: tras el insert de {@code FocusSessionRepository.recordFocusCompleted}, tras
 *       {@code recordFail} (solo recalcula: del fallo no se publica nada, P5) y tras
 *       {@code WeeklyGoalStore.setGoal}. Siempre escribe si el perfil está listo.</li>
 *   <li>Cada vez que el perfil pasa a {@code READY} (primer {@code READY} del proceso al arrancar, al iniciar
 *       sesión o al crear el perfil): escribe solo si lo calculado difiere de lo último publicado para ese uid
 *       ({@link SocialPrefs#readLastStatsPublished}, huella que empieza por {@code dayKey} y {@code weekKey}).
 *       Sin publicación previa (cuenta nueva) siempre escribe: es la primera publicación. Un nuevo
 *       {@code READY} del mismo perfil (editar el nombre) no hace nada.</li>
 * </ul>
 *
 * <p><b>Qué escribe.</b> Toda la historia local (D-031): {@code totalMinutes}, {@code weekStreak} y
 * {@code bestWeekStreak} salen de todo {@code focus_event}, sin fecha de corte por cuenta. Los valores los
 * calcula {@link StatsComputer} y el mapa (recortado a los rangos de las reglas) {@link StatsWrites}; cada
 * publicación es un {@code set()} completo (la última gana, decisión d2).
 *
 * <p><b>Hilos (regla #3).</b> Todo el cálculo corre en un único {@link SerialExecutor} sobre el
 * {@code @IoExecutor}; las ráfagas de {@link #requestPublish()} se funden en una sola tarea pendiente. Los
 * errores de escritura solo se registran en el log (la caché offline de Firestore ya pone en cola las
 * escrituras sin red); no hay UI. Firebase solo a través de {@link StatsBackend}.
 */
@Singleton
public class StatsPublisher {

    private static final String TAG = "StatsPublisher";

    /** Dónde van los errores de escritura (el log; un test los recoge). */
    interface ErrorLog {
        void log(@NonNull String message, @NonNull Throwable error);
    }

    private final LiveData<ProfileState> profileStates;
    private final StatsBackend backend;
    private final FocusEventDAO focusEvents;
    private final IntSupplier goal;
    private final SocialPrefs prefs;
    private final Executor serial;
    private final Supplier<LocalDate> today;
    private final BooleanSupplier enabled;
    private final ErrorLog errorLog;

    private final AtomicBoolean started = new AtomicBoolean(false);
    /** Hay una tarea de {@link #requestPublish()} en la cola que aún no ha empezado. */
    private final AtomicBoolean pending = new AtomicBoolean(false);
    /** uid del perfil mientras está en {@code READY}; null en otro estado. Solo se toca en el serial. */
    @Nullable
    private String readyUid;

    @Inject
    public StatsPublisher(ProfileRepository profiles, FirestoreStatsBackend backend, FocusEventDAO focusEvents,
                          WeeklyGoalStore goals, SocialPrefs prefs, @IoExecutor ExecutorService ioExecutor) {
        this(profiles.observeMine(), backend, focusEvents, goals::getGoalSync, prefs,
                new SerialExecutor(ioExecutor), LocalDate::now, SocialFeature::isAccountEnabled,
                (message, error) -> Log.w(TAG, message, error));
    }

    /** Para tests: sin Firebase ni Android (perfil por {@link LiveData}, ejecutor directo, reloj fijo). */
    StatsPublisher(@NonNull LiveData<ProfileState> profileStates, @NonNull StatsBackend backend,
                   @NonNull FocusEventDAO focusEvents, @NonNull IntSupplier goal, @NonNull SocialPrefs prefs,
                   @NonNull Executor serial, @NonNull Supplier<LocalDate> today, @NonNull BooleanSupplier enabled,
                   @NonNull ErrorLog errorLog) {
        this.profileStates = profileStates;
        this.backend = backend;
        this.focusEvents = focusEvents;
        this.goal = goal;
        this.prefs = prefs;
        this.serial = serial;
        this.today = today;
        this.enabled = enabled;
        this.errorLog = errorLog;
    }

    /**
     * Empieza a seguir el perfil propio (una vez por proceso). Lo llama {@code DBMigration.onCreate} solo con
     * {@code FEATURE_SOCIAL}; sin el flag no hace nada.
     */
    @MainThread
    public void start() {
        if (!enabled.getAsBoolean() || !started.compareAndSet(false, true)) {
            return;
        }
        profileStates.observeForever(state -> serial.execute(() -> onProfileState(state)));
    }

    /**
     * Recalcula y publica si el perfil está listo. Se puede llamar desde cualquier hilo; varias llamadas
     * seguidas antes de que empiece el cálculo se funden en una sola publicación.
     */
    public void requestPublish() {
        if (!enabled.getAsBoolean() || !pending.compareAndSet(false, true)) {
            return;
        }
        serial.execute(() -> {
            pending.set(false); // lo que llegue a partir de aquí puede ver datos nuevos: otra tarea
            String uid = readyUid;
            if (uid != null) {
                publish(uid, false);
            }
        });
    }

    // ---- en el serial ------------------------------------------------------------------------------------

    private void onProfileState(@Nullable ProfileState state) {
        boolean ready = state != null && state.status == ProfileState.Status.READY && state.profile != null;
        if (!ready) {
            readyUid = null;
            return;
        }
        String uid = state.profile.uid;
        if (uid.equals(readyUid)) {
            return; // el mismo perfil vuelve a emitir (nombre, color): nada que publicar
        }
        readyUid = uid;
        publish(uid, true);
    }

    private void publish(@NonNull String uid, boolean onlyIfChanged) {
        if (!uid.equals(backend.currentUid())) {
            return; // la sesión ya no es la del perfil (salió o cambió de cuenta)
        }
        FriendStats values;
        try {
            values = computeNow();
        } catch (RuntimeException e) {
            errorLog.log("stats compute failed", e);
            return;
        }
        String fingerprint = StatsWrites.fingerprint(values);
        if (onlyIfChanged && fingerprint.equals(prefs.readLastStatsPublished(uid))) {
            return;
        }
        backend.setStats(uid, StatsWrites.toMap(values), serial, new StatsBackend.Callback() {
            @Override
            public void onSuccess() {
                prefs.saveLastStatsPublished(uid, fingerprint);
            }

            @Override
            public void onError(@NonNull Throwable error) {
                errorLog.log("stats/" + uid + " write failed", error);
            }
        });
    }

    /** Los 10 valores de hoy con toda la historia local (D-031). E/S de Room: solo en el serial. */
    @NonNull
    private FriendStats computeNow() {
        LocalDate day = today.get();
        ZoneId zone = ZoneId.systemDefault(); // la misma zona que date(..., 'localtime') de SQLite
        LocalDate monday = StreakCalculator.weekStart(day);
        long start = monday.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1;
        List<DayMinutes> week = focusEvents.getFocusMinutesPerDayBetween(start, end);
        int total = focusEvents.sumFocusMinutesSince(0L);
        int g = goal.getAsInt();
        StreakCalculator.Result streak = StreakCalculator.compute(focusEvents.getActiveDaysSince(0L), g, day);
        return StatsComputer.compute(week, total, streak, g, day);
    }
}
