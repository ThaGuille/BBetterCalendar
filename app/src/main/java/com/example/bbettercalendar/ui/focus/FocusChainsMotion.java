package com.example.bbettercalendar.ui.focus;

/**
 * Calendario de las cadenas de Focus (diseño A2g del prototipo «Cadenas en Focus»). Sin tipos de
 * Android: {@link FocusChains} lo consulta en cada fotograma y los tests lo comprueban en la JVM.
 *
 * <p>Entrada: las cuatro cadenas salen de sus esquinas casi a la vez (sup. izq., inf. der., sup. der.,
 * inf. izq., 70 ms entre una y otra), corren por la diagonal hasta el anillo, se pasan un poco al
 * golpearlo y se tensan. Cuando ya están las cuatro, cae el candado abierto en la esquina superior
 * derecha y se cierra de golpe. Salida: las cadenas vuelven a sus esquinas y el candado se apaga.
 *
 * <p>Los tiempos están cosidos al sonido {@code res/raw/focus_chains_lock.wav}: si cambian, hay que
 * regenerarlo con {@code .claude/scripts/gen_focus_chain_sounds.py}.
 */
final class FocusChainsMotion {

    /** Esquinas: 0 sup. izq., 1 sup. der., 2 inf. izq., 3 inf. der. */
    static final int CORNERS = 4;
    /** La esquina sin placa: ahí el candado es el enganche. */
    static final int LOCK_CORNER = 1;

    /** Lo que tarda cada cadena en llegar y asentarse. */
    static final long TRAVEL_MS = 900;
    /** Fracción del viaje en la que la placa golpea el anillo. */
    static final double IMPACT = 0.78;
    static final long STAGGER_MS = 70;
    /** Turno de salida de cada esquina (sup. izq., inf. der., sup. der., inf. izq.). */
    private static final int[] TURN = {0, 2, 3, 1};

    static final long LAST_HIT_MS = 3 * STAGGER_MS + Math.round(TRAVEL_MS * IMPACT);
    /** El candado aparece abierto cuando las cuatro cadenas ya están enganchadas. */
    static final long LOCK_APPEAR_MS = LAST_HIT_MS + 90;
    static final long LOCK_DROP_MS = 170;
    /** El arco del candado baja de golpe: es el «clic» del sonido. */
    static final long LOCK_SNAP_MS = LOCK_APPEAR_MS + 240;
    static final long LOCK_SNAP_DURATION_MS = 70;
    /** Fin de la entrada: aquí se ilumina el icono de bloqueo de la barra. */
    static final long ENTER_MS = LOCK_SNAP_MS + LOCK_SNAP_DURATION_MS;

    static final long EXIT_MS = 380;
    static final long EXIT_LOCK_FADE_MS = 120;

    private static final CubicBezier TRAVEL_IN = new CubicBezier(0.35, 0.3, 0.6, 0.9);
    private static final CubicBezier SETTLE = new CubicBezier(0, 0, 0.58, 1);      // ease-out
    private static final CubicBezier DROP = new CubicBezier(0.3, 0, 0.4, 1);
    private static final CubicBezier SNAP = new CubicBezier(0.6, 0, 0.9, 0.4);
    private static final CubicBezier EXIT = new CubicBezier(0.5, 0, 0.9, 0.5);

    private FocusChainsMotion() {
    }

    /**
     * Desplazamiento de la cadena de una esquina a lo largo de su diagonal, hacia el centro, {@code t} ms
     * después de empezar la entrada: {@code -retracted} (fuera de la pantalla) antes de salir,
     * {@code +overshoot} al golpear el anillo y 0 al quedar en su sitio.
     */
    static double enterShift(int corner, long t, double retracted, double overshoot) {
        double local = (t - TURN[corner] * STAGGER_MS) / (double) TRAVEL_MS;
        if (local <= 0) {
            return -retracted;
        }
        if (local >= 1) {
            return 0;
        }
        if (local < IMPACT) {
            return lerp(-retracted, overshoot, TRAVEL_IN.apply(local / IMPACT));
        }
        return lerp(overshoot, 0, SETTLE.apply((local - IMPACT) / (1 - IMPACT)));
    }

    /** Salida: de {@code from} (donde estaba la cadena) a {@code -retracted}, igual para las cuatro. */
    static double exitShift(long t, double from, double retracted) {
        return lerp(from, -retracted, EXIT.apply(clamp(t / (double) EXIT_MS)));
    }

    /** Opacidad del candado durante la entrada (0 hasta que aparece). */
    static double lockAlpha(long t) {
        return DROP.apply(clamp((t - LOCK_APPEAR_MS) / (double) LOCK_DROP_MS));
    }

    /** Lo que le queda por caer al candado, 1 = arriba del todo, 0 = en su sitio. */
    static double lockLift(long t) {
        return 1 - lockAlpha(t);
    }

    /** Cuánto sigue abierto el arco del candado, 1 = abierto, 0 = cerrado. */
    static double shackleOpen(long t) {
        return 1 - SNAP.apply(clamp((t - LOCK_SNAP_MS) / (double) LOCK_SNAP_DURATION_MS));
    }

    /** Opacidad del candado durante la salida, desde {@code from}. */
    static double exitLockAlpha(long t, double from) {
        return from * (1 - clamp(t / (double) EXIT_LOCK_FADE_MS));
    }

    private static double lerp(double a, double b, double f) {
        return a + (b - a) * f;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /** cubic-bezier(x1, y1, x2, y2) de CSS, resuelto por bisección (sin PathInterpolator: JVM). */
    static final class CubicBezier {
        private final double x1;
        private final double y1;
        private final double x2;
        private final double y2;

        CubicBezier(double x1, double y1, double x2, double y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        double apply(double x) {
            if (x <= 0) {
                return 0;
            }
            if (x >= 1) {
                return 1;
            }
            double lo = 0;
            double hi = 1;
            double u = x;
            for (int i = 0; i < 30; i++) {
                if (sample(x1, x2, u) < x) {
                    lo = u;
                } else {
                    hi = u;
                }
                u = (lo + hi) / 2;
            }
            return sample(y1, y2, u);
        }

        private static double sample(double a, double b, double u) {
            double v = 1 - u;
            return 3 * a * v * v * u + 3 * b * v * u * u + u * u * u;
        }
    }
}
