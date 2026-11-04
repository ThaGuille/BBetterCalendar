package com.example.bbettercalendar.social.firebase;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AppCheckPolicyTest {

    @Test
    public void sinFeatureSocial_ninguno() {
        assertEquals(AppCheckPolicy.Provider.NONE, AppCheckPolicy.decide(false, false, false));
        assertEquals(AppCheckPolicy.Provider.NONE, AppCheckPolicy.decide(false, false, true));
    }

    @Test
    public void contraElEmulador_ninguno() {
        assertEquals(AppCheckPolicy.Provider.NONE, AppCheckPolicy.decide(true, true, true));
        assertEquals(AppCheckPolicy.Provider.NONE, AppCheckPolicy.decide(true, true, false));
    }

    @Test
    public void debugContraProyectoReal_debug() {
        assertEquals(AppCheckPolicy.Provider.DEBUG, AppCheckPolicy.decide(true, false, true));
    }

    @Test
    public void releaseConFeatureSocial_playIntegrity() {
        assertEquals(AppCheckPolicy.Provider.PLAY_INTEGRITY, AppCheckPolicy.decide(true, false, false));
    }
}
