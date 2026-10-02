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
        return from(softDeadlineMillis, nowMillis, false);
    }

    /**
     * Igual que {@link #from(long, long)}, pero un proyecto terminado no está ni "vencido" ni
     * "por vencer" (spec frontend-structure-cleanup: "Test 1 sept" salía Overdue con 1/1 hecho).
     */
    public static ProjectDeadlineState from(long softDeadlineMillis, long nowMillis, boolean finished) {
        if (finished || softDeadlineMillis <= 0L) {
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

    /** Terminado = marcado como completado, o con items y todos hechos. */
    public static boolean isFinished(int status, int doneCount, int totalCount) {
        return status == Project.STATUS_COMPLETED || (totalCount > 0 && doneCount >= totalCount);
    }
}
