package com.example.bbettercalendar.social.interaction;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.social.common.SerialExecutor;
import com.example.bbettercalendar.social.stats.WeekKey;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Enviar un ánimo o un empujón a un amigo (F5-u5; 02 §8, 03 §7; D-034, D-035).
 *
 * <ul>
 *   <li>{@link #send}: llama a {@code sendCheer} / {@code sendNudge} con el {@code dayKey} del que envía
 *       ({@link WeekKey#dayKey}, su fecha local de hoy) y traduce la respuesta con {@link InteractionMapping}.
 *       El límite de 1 al día por amigo y tipo es del servidor: su {@code ALREADY_SENT} es la autoridad.</li>
 *   <li>Registro local de «ya enviado hoy» ({@link SentTodayLog}, fichero propio de preferencias): se anota con
 *       {@code SENT} y con {@code ALREADY_SENT}, va por mi uid y se purga de otros días y otros uids en cada
 *       lectura y escritura. {@link #clearLocalLog()} borra el fichero entero (cierre de sesión y borrado de
 *       cuenta).</li>
 *   <li>Elegibilidad del empujón: {@link NudgeEligibility}, pura; no está aquí.</li>
 * </ul>
 *
 * <p>Regla #3: todo corre en un {@link SerialExecutor} sobre el {@code @IoExecutor}; los callbacks se entregan
 * en él (quien publique en LiveData usa {@code postValue}).
 */
@Singleton
public class InteractionRepository {

    /** El resultado de {@link #send}, en el serial. */
    public interface Callback {
        void onResult(@NonNull InteractionResult result);
    }

    /** El resultado de {@link #sentToday}, en el serial. */
    public interface SentCallback {
        void onResult(boolean sentToday);
    }

    /** La fecha civil de hoy en la zona del dispositivo; en tests, una fija. */
    interface Today {
        @NonNull
        LocalDate date();
    }

    private final InteractionBackend backend;
    private final Executor serial;
    private final SentTodayLog log;
    private final Today today;

    @Inject
    public InteractionRepository(FunctionsInteractionBackend backend, @ApplicationContext Context context,
                                 @IoExecutor ExecutorService ioExecutor) {
        this(backend, new SerialExecutor(ioExecutor), new PrefsSentLogStore(context), LocalDate::now);
    }

    /** Para tests: Firebase llega solo por {@link InteractionBackend}; el registro, por un {@link SentLogStore}. */
    InteractionRepository(@NonNull InteractionBackend backend, @NonNull Executor serial,
                          @NonNull SentLogStore store, @NonNull Today today) {
        this.backend = backend;
        this.serial = serial;
        this.log = new SentTodayLog(store);
        this.today = today;
    }

    /** Envía un ánimo o un empujón a {@code friendUid}. Sin sesión da {@code ERROR} sin llamar a nada. */
    public void send(@NonNull InteractionType type, @NonNull String friendUid, @NonNull Callback callback) {
        serial.execute(() -> {
            String me = backend.currentUid();
            if (me == null) {
                callback.onResult(InteractionResult.ERROR);
                return;
            }
            String dayKey = WeekKey.dayKey(today.date());
            backend.call(type.callable, friendUid, dayKey, serial, new InteractionBackend.Result() {
                @Override
                public void onSuccess(@Nullable Map<String, Object> data) {
                    InteractionResult result = InteractionMapping.fromData(data);
                    if (result == InteractionResult.SENT || result == InteractionResult.ALREADY_SENT) {
                        log.record(me, friendUid, type, dayKey);
                    }
                    callback.onResult(result);
                }

                @Override
                public void onError(@NonNull InteractionBackend.Failure failure) {
                    callback.onResult(InteractionMapping.fromFailure(failure));
                }
            });
        });
    }

    /** ¿Ya se envió hoy ese tipo a ese amigo desde esta cuenta? (sin sesión: false). */
    public void sentToday(@NonNull InteractionType type, @NonNull String friendUid, @NonNull SentCallback callback) {
        serial.execute(() -> {
            String me = backend.currentUid();
            callback.onResult(me != null && log.contains(me, friendUid, type, WeekKey.dayKey(today.date())));
        });
    }

    /** Borra el registro local entero (fichero incluido): cierre de sesión y borrado de cuenta. */
    public void clearLocalLog() {
        serial.execute(log::clear);
    }
}
