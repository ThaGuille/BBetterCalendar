package com.example.bbettercalendar.social.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AgeGateTest {

    @Test
    public void minAgeIs14() {
        assertEquals(14, AgeGate.SOCIAL_MIN_AGE);
    }

    @Test
    public void bornIn2011IsAllowedIn2026() {
        assertTrue(AgeGate.isAllowed(2011, 2026));
    }

    @Test
    public void bornIn2012IsNotAllowedIn2026() {
        assertFalse(AgeGate.isAllowed(2012, 2026));
    }

    @Test
    public void thresholdMovesWithTheCurrentYear() {
        assertFalse(AgeGate.isAllowed(2012, 2026));
        assertTrue(AgeGate.isAllowed(2012, 2027));
    }

    @Test
    public void bornIn2007IsAdultBandIn2026() {
        assertEquals("18+", AgeGate.ageBand(2007, 2026));
    }

    @Test
    public void bornIn2008IsTeenBandIn2026() {
        assertEquals("14-17", AgeGate.ageBand(2008, 2026));
    }

    @Test
    public void bandAtLowerBoundaryIsTeen() {
        assertEquals("14-17", AgeGate.ageBand(2011, 2026));
    }

    @Test
    public void ageIsConservative() {
        assertEquals(14, AgeGate.age(2011, 2026));
    }
}
