package com.example.bbettercalendar.ui.common.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.ui.common.motion.ScreenTransitions.Kind;

import org.junit.Test;

/** Decisions of the screen transitions (u15): which family plays for each pair of destinations. */
public class ScreenTransitionsTest {

    @Test
    public void tabToTab_fadesThrough() {
        assertEquals(Kind.FADE_THROUGH, ScreenTransitions.classify(true, true));
    }

    @Test
    public void tabToSecondary_andBack_useTheZAxis() {
        assertEquals(Kind.AXIS_Z, ScreenTransitions.classify(true, false));
        assertEquals(Kind.AXIS_Z, ScreenTransitions.classify(false, true));
    }

    @Test
    public void secondaryToSecondary_usesTheXAxis() {
        assertEquals(Kind.AXIS_X, ScreenTransitions.classify(false, false));
    }

    @Test
    public void enter_savedKindWinsOverPending_soARecreatedScreenKeepsItsTransition() {
        assertEquals(Kind.CONTAINER, ScreenTransitions.resolveEnter(Kind.CONTAINER, Kind.AXIS_Z));
        assertEquals(Kind.AXIS_X, ScreenTransitions.resolveEnter(null, Kind.AXIS_X));
    }

    @Test
    public void enter_withoutSavedOrPending_isFadeThrough() {
        assertEquals(Kind.FADE_THROUGH, ScreenTransitions.resolveEnter(null, null));
    }

    @Test
    public void onlyAContainerEntryKeepsItsReturn() {
        assertTrue(ScreenTransitions.keepsReturn(Kind.CONTAINER));
        for (Kind kind : new Kind[] {Kind.FADE_THROUGH, Kind.AXIS_Z, Kind.AXIS_X}) {
            assertFalse(kind.name(), ScreenTransitions.keepsReturn(kind));
        }
        assertFalse(ScreenTransitions.keepsReturn(null));
    }

    @Test
    public void parse_roundTripsEveryKind_andRejectsUnknownNames() {
        for (Kind kind : Kind.values()) {
            assertEquals(kind, ScreenTransitions.parse(kind.name()));
        }
        assertNull(ScreenTransitions.parse(null));
        assertNull(ScreenTransitions.parse("SLIDE"));
    }

    @Test
    public void projectCardName_isUniquePerProject() {
        assertEquals("bb_project_card_7", ScreenTransitions.projectCardName(7));
        assertFalse(ScreenTransitions.projectCardName(1).equals(ScreenTransitions.projectCardName(11)));
    }
}
