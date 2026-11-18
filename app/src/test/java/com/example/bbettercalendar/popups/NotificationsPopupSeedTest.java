package com.example.bbettercalendar.popups;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotSame;

import org.junit.Test;

/** La semilla del popup de avisos es una copia de longitud 7 de la seleccion de la actividad. */
public class NotificationsPopupSeedTest {

    @Test
    public void copy_keepsSelectionAndDoesNotShareTheArray() {
        boolean[] src = {false, true, false, false, false, false, false};
        boolean[] out = NotificationsPopup.copyToSeven(src);
        assertArrayEquals(src, out);
        assertNotSame(src, out);
    }

    @Test
    public void copy_nullAndShortArraysArePaddedWithFalse() {
        assertArrayEquals(new boolean[7], NotificationsPopup.copyToSeven(null));
        assertArrayEquals(new boolean[]{true, false, false, false, false, false, false},
                NotificationsPopup.copyToSeven(new boolean[]{true}));
    }
}
