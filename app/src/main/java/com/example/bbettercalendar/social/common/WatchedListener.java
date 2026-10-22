package com.example.bbettercalendar.social.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Un listener de Firestore por clave (uid, consulta) que no publica nada viejo y que se reengancha
 * (F2-u1). Corrige los dos fallos de F1 en {@code ProfileRepository}: el {@code LOADING} colgado por
 * eventos desordenados y el {@code READY} publicado después de {@code SIGNED_OUT}.
 *
 * <ul>
 *   <li><b>Generación:</b> {@link #attach} y {@link #detach} suben un contador en el hilo que llama. Cada
 *       evento y cada cambio de estado (también el {@code onLoading} de {@code attach} y el
 *       {@code onDetached} de {@code detach}) pasa por el mismo {@link SerialExecutor} y se descarta si su
 *       generación ya no es la actual.</li>
 *   <li><b>Error reintentable</b> ({@link #RETRYABLE}): publica el error y se reengancha con
 *       {@link Backoff} (1, 2, 4… s hasta 60 s, como mucho 6 intentos; el primer evento bueno del
 *       servidor lo reinicia, no uno de la caché). Agotados los intentos, espera como en el caso siguiente.</li>
 *   <li><b>Error no reintentable</b> ({@code PERMISSION_DENIED}, {@code FAILED_PRECONDITION} y cualquier
 *       otro código): publica el error y espera a un cambio de sesión ({@link #attach} con otra clave, o
 *       {@code detach} + {@code attach}) o a que la app vuelva a primer plano ({@link #onForeground()},
 *       {@code ProcessLifecycleOwner} {@code ON_START} vía {@link ProcessForeground}).</li>
 * </ul>
 *
 * <p>El {@link Sink} se llama siempre en el {@link SerialExecutor} recibido (regla #3: quien publique en
 * LiveData usa {@code postValue}). Como todo pasa por esa cola, un {@code onDetached} encolado después de
 * un evento siempre se publica después, aunque el evento ya hubiera pasado la comprobación.
 */
public final class WatchedListener<K, T> {

    /** Códigos con los que vale la pena reintentar solo. */
    public static final Set<String> RETRYABLE = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "UNAVAILABLE", "INTERNAL", "UNKNOWN", "RESOURCE_EXHAUSTED")));
    /** Códigos que no se arreglan reintentando (sesión o reglas): se espera a sesión o primer plano. */
    public static final Set<String> WAIT_FOR_SESSION = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "PERMISSION_DENIED", "FAILED_PRECONDITION")));

    /** Destino de los estados. Siempre en el {@link SerialExecutor}, nunca con generación vieja. */
    public interface Sink<T> {
        /** Recién enganchado a una clave nueva; aún sin respuesta. */
        void onLoading();

        void onValue(@NonNull T value);

        void onError(@NonNull ListenerException error);

        /** Desenganchado ({@link #detach()}): p. ej. sin sesión. */
        void onDetached();
    }

    private enum Mode { IDLE, ACTIVE, RETRY_SCHEDULED, WAITING }

    private final Executor serial;
    private final Scheduler scheduler;
    private final ListenerSource<K, T> source;
    private final Sink<T> sink;

    // Guardados por "this".
    private final Backoff backoff = new Backoff();
    private long generation;
    @Nullable
    private K key;
    @NonNull
    private Mode mode = Mode.IDLE;
    @Nullable
    private ListenerSource.Registration registration;
    @Nullable
    private Scheduler.Cancellable pendingRetry;

    public WatchedListener(@NonNull Executor serial, @NonNull Scheduler scheduler,
                           @NonNull ListenerSource<K, T> source, @NonNull Sink<T> sink) {
        this.serial = serial;
        this.scheduler = scheduler;
        this.source = source;
        this.sink = sink;
    }

    /** true si {@code code} se reintenta con espera creciente; si no, se espera a sesión o primer plano. */
    public static boolean isRetryable(@Nullable String code) {
        return code != null && RETRYABLE.contains(code);
    }

    /**
     * Escucha {@code key}. Con la misma clave ya enganchada (o con un reintento en marcha) no hace nada;
     * con otra, o tras un error que espera, desengancha lo anterior, publica {@code onLoading} y engancha.
     */
    public void attach(@NonNull K newKey) {
        synchronized (this) {
            if (newKey.equals(key) && (mode == Mode.ACTIVE || mode == Mode.RETRY_SCHEDULED)) {
                return;
            }
            long gen = resetLocked();
            key = newKey;
            mode = Mode.ACTIVE;
            backoff.reset();
            // El onLoading se encola ANTES de enganchar: ningún evento de este listener puede adelantarlo.
            serial.execute(() -> {
                if (isCurrent(gen)) {
                    sink.onLoading();
                }
            });
            registerLocked(newKey, gen);
        }
    }

    /** Deja de escuchar y publica {@code onDetached}; los eventos que aún lleguen se descartan. */
    public void detach() {
        synchronized (this) {
            long gen = resetLocked();
            key = null;
            mode = Mode.IDLE;
            serial.execute(() -> {
                if (isCurrent(gen)) {
                    sink.onDetached();
                }
            });
        }
    }

    /**
     * La app vuelve a primer plano: si se esperaba tras un error (no reintentable, o reintentos agotados),
     * vuelve a enganchar la misma clave con la espera reiniciada. Si no, no hace nada.
     */
    public void onForeground() {
        synchronized (this) {
            if (mode != Mode.WAITING || key == null) {
                return;
            }
            long gen = ++generation;
            mode = Mode.ACTIVE;
            backoff.reset();
            registerLocked(key, gen);
        }
    }

    // ---- interno -----------------------------------------------------------------------------------------

    private synchronized boolean isCurrent(long gen) {
        return gen == generation;
    }

    /** Nueva generación: quita el listener y el reintento pendientes. Devuelve la generación nueva. */
    private long resetLocked() {
        generation++;
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        if (pendingRetry != null) {
            pendingRetry.cancel();
            pendingRetry = null;
        }
        return generation;
    }

    private void registerLocked(@NonNull K k, long gen) {
        registration = source.listen(k, serial, (value, fromCache, error) -> deliver(gen, value, fromCache, error));
    }

    /** En el {@link SerialExecutor}. */
    private void deliver(long gen, @Nullable T value, boolean fromCache, @Nullable ListenerException error) {
        synchronized (this) {
            if (gen != generation) {
                return; // de una generación anterior: otra clave, otra sesión o un reintento posterior
            }
            if (error == null) {
                if (value == null) {
                    return;
                }
                // Solo un valor del servidor prueba que la conexión va: el de la caché llega antes del
                // error al reenganchar, y si reiniciara la espera los reintentos irían a 1 s para siempre.
                if (!fromCache) {
                    backoff.reset();
                }
            } else {
                onErrorLocked(gen, error);
            }
        }
        if (error == null) {
            sink.onValue(value);
        } else {
            sink.onError(error);
        }
    }

    private void onErrorLocked(long gen, @NonNull ListenerException error) {
        // Tras un error, Firestore ya no entrega más eventos de este listener.
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        long delay = isRetryable(error.code) ? backoff.nextDelayMs() : -1L;
        if (delay < 0) {
            mode = Mode.WAITING;
            return;
        }
        mode = Mode.RETRY_SCHEDULED;
        pendingRetry = scheduler.schedule(delay, () -> serial.execute(() -> retry(gen)));
    }

    /** En el {@link SerialExecutor}: vuelve a enganchar la misma clave si nada ha cambiado mientras. */
    private synchronized void retry(long gen) {
        if (gen != generation || mode != Mode.RETRY_SCHEDULED || key == null) {
            return;
        }
        pendingRetry = null;
        long next = ++generation;
        mode = Mode.ACTIVE;
        registerLocked(key, next);
    }
}
