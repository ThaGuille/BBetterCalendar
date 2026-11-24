package com.example.bbettercalendar.ui.focus;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ciclos de una tarea (rediseño de Proyectos, D-P11): lo que le falta a la meta de una tarea
 * ({@code goal - attributed}, nunca negativo) repartido en bloques de concentración de la duración
 * configurada, con el último acortado a lo que quede, y un descanso entre dos bloques seguidos sólo
 * si el descanso está activado (nunca después del último). Ejemplo: 120 min con ciclos de 25 + 5 =
 * 25, 25, 25, 25 y 20 con cuatro descansos de 5.
 *
 * <p>Puro (sin Android) e inmutable; {@link FocusTimerStateMachine#startPlan(long[])} corre sus
 * bloques. Todo en minutos enteros.
 */
public final class TaskCyclePlan {

    private static final long MINUTE_MILLIS = 60_000L;

    private final List<Integer> focusBlocks;
    private final int restMinutes;
    private final boolean rests;

    private TaskCyclePlan(List<Integer> focusBlocks, int restMinutes, boolean rests) {
        this.focusBlocks = Collections.unmodifiableList(focusBlocks);
        this.restMinutes = restMinutes;
        this.rests = rests;
    }

    /**
     * @param goalMinutes       meta de la tarea ({@code CalendarEntry.targetMinutes})
     * @param attributedMinutes minutos ya atribuidos a la tarea
     * @param workMinutes       concentración configurada; 0 o menos = un solo bloque con todo lo que falta
     * @param restMinutes       descanso configurado; 0 o menos = sin descansos
     * @param restEnabled       {@code Configuration.homeIsRestEnabled}
     */
    @NonNull
    public static TaskCyclePlan of(int goalMinutes, int attributedMinutes, int workMinutes,
                                   int restMinutes, boolean restEnabled) {
        int remaining = Math.max(goalMinutes - Math.max(attributedMinutes, 0), 0);
        List<Integer> blocks = new ArrayList<>();
        if (remaining > 0) {
            int block = workMinutes > 0 ? workMinutes : remaining;
            int left = remaining;
            while (left > 0) {
                int next = Math.min(block, left);
                blocks.add(next);
                left -= next;
            }
        }
        boolean rests = restEnabled && restMinutes > 0;
        return new TaskCyclePlan(blocks, rests ? restMinutes : 0, rests);
    }

    /** Minutos de cada bloque de concentración, en orden. Vacío si la meta ya se alcanzó. */
    @NonNull
    public List<Integer> getFocusBlocks() {
        return focusBlocks;
    }

    /** Descansos entre bloques, en orden: uno menos que bloques, o ninguno sin descanso. */
    @NonNull
    public List<Integer> getRests() {
        if (!rests || focusBlocks.size() < 2) {
            return Collections.emptyList();
        }
        return Collections.nCopies(focusBlocks.size() - 1, restMinutes);
    }

    public int getBlockCount() {
        return focusBlocks.size();
    }

    /** Nada que enfocar: la tarea ya tiene su meta (o no la tiene). */
    public boolean isEmpty() {
        return focusBlocks.isEmpty();
    }

    /** Lo que falta de la meta: la suma de los bloques. */
    public int getRemainingMinutes() {
        int sum = 0;
        for (int minutes : focusBlocks) {
            sum += minutes;
        }
        return sum;
    }

    /** Los bloques en milisegundos, para {@link FocusTimerStateMachine#startPlan(long[])}. */
    @NonNull
    public long[] getFocusBlocksMillis() {
        long[] millis = new long[focusBlocks.size()];
        for (int i = 0; i < millis.length; i++) {
            millis[i] = focusBlocks.get(i) * MINUTE_MILLIS;
        }
        return millis;
    }
}
