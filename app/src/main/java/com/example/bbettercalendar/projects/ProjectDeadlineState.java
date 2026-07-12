package com.example.bbettercalendar.projects;

// Deriva el estado visual del soft deadline de un proyecto — reutiliza el lenguaje visual de
// app-limits (bb_accent_reward ámbar / bb_danger rojo, ver AppUsageAdapter) en vez de tokens
// nuevos (spec projects-mvp). Puro/estático: sin acceso a BD, lo llaman list y detail por igual.
public enum ProjectDeadlineState {
    NONE,
    APPROACHING,
    PASSED;

    private static final long APPROACHING_WINDOW_MILLIS = 3L * 24 * 60 * 60 * 1000; // 3 días

    public static ProjectDeadlineState from(long softDeadlineMillis, long nowMillis) {
        if (softDeadlineMillis <= 0L) {
            return NONE;
        }
        if (softDeadlineMillis < nowMillis) {
            return PASSED;
        }
        if (softDeadlineMillis - nowMillis <= APPROACHING_WINDOW_MILLIS) {
            return APPROACHING;
        }
        return NONE;
    }
}
