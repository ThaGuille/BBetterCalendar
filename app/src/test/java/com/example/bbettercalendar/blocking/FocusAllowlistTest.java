package com.example.bbettercalendar.blocking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// FocusAllowlist (mejora-visual u10): lista de apps siempre permitidas en SharedPreferences y el
// conjunto exento que el servicio entrega al motor.
public class FocusAllowlistTest {

    private static final String DIALER = "com.android.dialer";
    private static final String MAPS = "com.google.android.apps.maps";
    private static final String LAUNCHER = "com.android.launcher3";
    private static final String ASSIST = "com.google.android.googlequicksearchbox";

    private FakeSharedPreferences prefs;

    @Before
    public void setUp() {
        prefs = new FakeSharedPreferences();
    }

    @Test
    public void focusAllowlist_isEmptyByDefault() {
        assertTrue(FocusAllowlist.get(prefs).isEmpty());
    }

    @Test
    public void focusAllowlist_setThenGetRoundTrips() {
        FocusAllowlist.set(prefs, Arrays.asList(DIALER, MAPS));
        assertEquals(set(DIALER, MAPS), FocusAllowlist.get(prefs));
    }

    @Test
    public void focusAllowlist_setDropsNullAndBlankPackages() {
        FocusAllowlist.set(prefs, Arrays.asList(DIALER, null, "", "  "));
        assertEquals(set(DIALER), FocusAllowlist.get(prefs));
    }

    @Test
    public void focusAllowlist_getReturnsACopyNotThePreferenceSet() {
        FocusAllowlist.set(prefs, Collections.singleton(DIALER));
        Set<String> first = FocusAllowlist.get(prefs);
        try {
            first.add(MAPS);
        } catch (UnsupportedOperationException expected) {
            // inmutable: tampoco puede tocar el conjunto guardado
        }
        assertEquals(set(DIALER), FocusAllowlist.get(prefs));
    }

    @Test
    public void focusAllowlist_setEmptyClearsTheList() {
        FocusAllowlist.set(prefs, Collections.singleton(DIALER));
        FocusAllowlist.set(prefs, Collections.<String>emptySet());
        assertTrue(FocusAllowlist.get(prefs).isEmpty());
    }

    @Test
    public void focusAllowlistObserve_firesWithTheNewListOnChange() {
        List<Set<String>> seen = new ArrayList<>();
        FocusAllowlist.observe(prefs, seen::add);

        FocusAllowlist.set(prefs, Collections.singleton(DIALER));
        FocusAllowlist.set(prefs, Collections.<String>emptySet());

        assertEquals(2, seen.size());
        assertEquals(set(DIALER), seen.get(0));
        assertTrue(seen.get(1).isEmpty());
    }

    @Test
    public void focusAllowlistObserve_ignoresOtherKeysOfTheSameFile() {
        List<Set<String>> seen = new ArrayList<>();
        FocusAllowlist.observe(prefs, seen::add);

        prefs.edit().putBoolean("focus_shield", true).apply();
        FocusAllowlist.markSuggestionDone(prefs);

        assertTrue(seen.isEmpty());
    }

    @Test
    public void focusAllowlistObserve_cancelStopsListening() {
        List<Set<String>> seen = new ArrayList<>();
        FocusAllowlist.Subscription sub = FocusAllowlist.observe(prefs, seen::add);
        sub.cancel();

        FocusAllowlist.set(prefs, Collections.singleton(DIALER));

        assertTrue(seen.isEmpty());
        assertEquals(0, prefs.listenerCount());
    }

    @Test
    public void focusAllowlistSuggestion_offeredUntilMarkedDone() {
        assertFalse(FocusAllowlist.isSuggestionDone(prefs));
        FocusAllowlist.markSuggestionDone(prefs);
        assertTrue(FocusAllowlist.isSuggestionDone(prefs));
    }

    @Test
    public void focusAllowlistExempt_isSystemExemptUnionUserList() {
        Set<String> exempt = FocusAllowlist.focusExemptPackages(set(LAUNCHER, ASSIST), set(DIALER));
        assertEquals(set(LAUNCHER, ASSIST, DIALER), exempt);
    }

    @Test
    public void focusAllowlistExempt_emptyListKeepsOnlyLauncherAndAssistant() {
        assertEquals(set(LAUNCHER, ASSIST),
                FocusAllowlist.focusExemptPackages(set(LAUNCHER, ASSIST), Collections.<String>emptySet()));
        assertEquals(set(LAUNCHER, ASSIST),
                FocusAllowlist.focusExemptPackages(set(LAUNCHER, ASSIST), null));
    }

    @Test
    public void focusAllowlistExempt_neverNullAndIgnoresNulls() {
        assertTrue(FocusAllowlist.focusExemptPackages(null, null).isEmpty());
        assertEquals(set(DIALER),
                FocusAllowlist.focusExemptPackages(null, Arrays.asList(null, DIALER)));
    }

    private static Set<String> set(String... pkgs) {
        return new HashSet<>(Arrays.asList(pkgs));
    }
}
