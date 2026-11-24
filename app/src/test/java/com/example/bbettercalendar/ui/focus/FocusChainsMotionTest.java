package com.example.bbettercalendar.ui.focus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FocusChainsMotionTest {

    private static final double OFF = 300;
    private static final double OVER = 5;
    private static final double EPS = 1e-6;

    @Test
    public void cadaCadenaEstaFueraHastaSuTurno() {
        // sup. izq. sale primero; inf. der. a los 70 ms; sup. der. a los 140; inf. izq. a los 210
        assertEquals(-OFF, FocusChainsMotion.enterShift(0, 0, OFF, OVER), EPS);
        assertEquals(-OFF, FocusChainsMotion.enterShift(3, 70, OFF, OVER), EPS);
        assertEquals(-OFF, FocusChainsMotion.enterShift(1, 140, OFF, OVER), EPS);
        assertEquals(-OFF, FocusChainsMotion.enterShift(2, 210, OFF, OVER), EPS);
        assertTrue(FocusChainsMotion.enterShift(3, 71, OFF, OVER) > -OFF);
    }

    @Test
    public void golpeaElAnilloPasandoseYLuegoSeTensa() {
        long impact = Math.round(FocusChainsMotion.TRAVEL_MS * FocusChainsMotion.IMPACT);
        assertEquals(OVER, FocusChainsMotion.enterShift(0, impact, OFF, OVER), 1e-3);
        double settling = FocusChainsMotion.enterShift(0, impact + 100, OFF, OVER);
        assertTrue(settling > 0 && settling < OVER);
        assertEquals(0, FocusChainsMotion.enterShift(0, FocusChainsMotion.TRAVEL_MS, OFF, OVER), EPS);
    }

    @Test
    public void elViajeAvanzaSinRetroceder() {
        double last = -OFF;
        long impact = Math.round(FocusChainsMotion.TRAVEL_MS * FocusChainsMotion.IMPACT);
        for (long t = 0; t <= impact; t += 10) {
            double s = FocusChainsMotion.enterShift(0, t, OFF, OVER);
            assertTrue("t=" + t, s >= last - EPS);
            last = s;
        }
    }

    @Test
    public void todasEnSuSitioAlTerminarLaEntrada() {
        for (int c = 0; c < FocusChainsMotion.CORNERS; c++) {
            assertEquals(0, FocusChainsMotion.enterShift(c, FocusChainsMotion.ENTER_MS, OFF, OVER), EPS);
        }
    }

    @Test
    public void elCandadoLlegaCuandoEstanLasCuatroYSeCierraDespues() {
        assertTrue(FocusChainsMotion.LOCK_APPEAR_MS > FocusChainsMotion.LAST_HIT_MS);
        assertEquals(0, FocusChainsMotion.lockAlpha(FocusChainsMotion.LOCK_APPEAR_MS), EPS);
        assertEquals(1, FocusChainsMotion.lockAlpha(
                FocusChainsMotion.LOCK_APPEAR_MS + FocusChainsMotion.LOCK_DROP_MS), EPS);
        assertEquals(0, FocusChainsMotion.lockLift(
                FocusChainsMotion.LOCK_APPEAR_MS + FocusChainsMotion.LOCK_DROP_MS), EPS);
        // Abierto mientras cae y hasta el clic; cerrado al terminar la entrada
        assertEquals(1, FocusChainsMotion.shackleOpen(FocusChainsMotion.LOCK_SNAP_MS), EPS);
        assertEquals(0, FocusChainsMotion.shackleOpen(FocusChainsMotion.ENTER_MS), EPS);
    }

    @Test
    public void laSalidaParteDeDondeEstabaYAcabaFuera() {
        assertEquals(2.5, FocusChainsMotion.exitShift(0, 2.5, OFF), EPS);
        assertEquals(-OFF, FocusChainsMotion.exitShift(FocusChainsMotion.EXIT_MS, 2.5, OFF), EPS);
        assertEquals(0.4, FocusChainsMotion.exitLockAlpha(0, 0.4), EPS);
        assertEquals(0, FocusChainsMotion.exitLockAlpha(FocusChainsMotion.EXIT_LOCK_FADE_MS, 1), EPS);
    }

    @Test
    public void cubicBezierCoincideConLosExtremosYLaLinea() {
        FocusChainsMotion.CubicBezier linear = new FocusChainsMotion.CubicBezier(0, 0, 1, 1);
        assertEquals(0.3, linear.apply(0.3), 1e-4);
        assertEquals(0, linear.apply(-1), EPS);
        assertEquals(1, linear.apply(2), EPS);
    }
}
