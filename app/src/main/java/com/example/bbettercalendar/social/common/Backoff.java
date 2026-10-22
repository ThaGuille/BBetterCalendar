package com.example.bbettercalendar.social.common;

/**
 * Espera creciente de los reintentos de {@link WatchedListener} (F2-u1): 1, 2, 4… segundos hasta un
 * máximo de 60 s, como mucho {@link #MAX_ATTEMPTS} intentos seguidos; {@link #reset()} con el primer
 * evento bueno. Puro: no sabe de hilos ni de relojes (el tiempo lo pone el {@link Scheduler}).
 */
public final class Backoff {

    public static final long BASE_MS = 1_000L;
    public static final long MAX_MS = 60_000L;
    public static final int MAX_ATTEMPTS = 6;

    private int attempts;

    /** Espera del intento {@code attempt} (0 = el primero): {@code BASE_MS · 2^attempt}, como mucho {@code MAX_MS}. */
    public static long delayFor(int attempt) {
        if (attempt <= 0) {
            return BASE_MS;
        }
        if (attempt >= 30) {
            return MAX_MS;
        }
        return Math.min(MAX_MS, BASE_MS << attempt);
    }

    /** Espera antes del siguiente intento y lo cuenta; -1 si ya se agotaron los intentos. */
    public long nextDelayMs() {
        if (attempts >= MAX_ATTEMPTS) {
            return -1L;
        }
        return delayFor(attempts++);
    }

    /** Intentos ya gastados desde el último {@link #reset()}. */
    public int attempts() {
        return attempts;
    }

    public void reset() {
        attempts = 0;
    }
}
