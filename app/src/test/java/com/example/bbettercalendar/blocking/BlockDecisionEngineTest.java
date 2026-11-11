package com.example.bbettercalendar.blocking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.stats.AppRule;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

// BlockDecisionEngine con apps siempre permitidas (mejora-visual u10): la lista solo libra del modo
// bloqueo total del pomodoro; el límite diario sigue mandando (D4); lista vacía = como antes.
public class BlockDecisionEngineTest {

    private static final String OWN = "io.github.thaguille.bbettercalendar";
    private static final String LAUNCHER = "com.android.launcher3";
    private static final String ASSIST = "com.google.android.googlequicksearchbox";
    private static final String DIALER = "com.android.dialer";
    private static final String MAPS = "com.google.android.apps.maps";
    private static final String VIDEO = "com.google.android.youtube";

    private static final Set<String> SYSTEM_EXEMPT =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(LAUNCHER, ASSIST)));

    private FakeEnv env;
    private BlockDecisionEngine engine;

    @Before
    public void setUp() {
        env = new FakeEnv();
        engine = new BlockDecisionEngine(env);
    }

    // ---- foco activo

    @Test
    public void focusAllowedApp_isNotBlockedDuringFocus() {
        env.focus = true;
        allow(DIALER);

        BlockDecisionEngine.Decision d = engine.decide(DIALER);

        assertFalse(d.block);
        assertFalse(d.focusMode);
    }

    @Test
    public void notAllowedApp_isBlockedDuringFocus() {
        env.focus = true;
        allow(DIALER);

        BlockDecisionEngine.Decision d = engine.decide(VIDEO);

        assertTrue(d.block);
        assertTrue(d.focusMode);
    }

    @Test
    public void focusAllowedAppOverItsDailyLimit_staysBlockedByTheLimitDuringFocus() {
        env.focus = true;
        allow(VIDEO);
        limit(VIDEO, 30);
        env.usage.put(VIDEO, TimeUnit.MINUTES.toMillis(45));

        BlockDecisionEngine.Decision d = engine.decide(VIDEO);

        assertTrue(d.block);
        // Lo bloquea su límite, no el escudo: la portada dice "límite" y muestra el uso de hoy.
        assertFalse(d.focusMode);
        assertEquals(TimeUnit.MINUTES.toMillis(45), d.usedMillis);
    }

    @Test
    public void focusAllowedAppUnderItsDailyLimit_isNotBlockedDuringFocus() {
        env.focus = true;
        allow(VIDEO);
        limit(VIDEO, 30);
        env.usage.put(VIDEO, TimeUnit.MINUTES.toMillis(10));

        assertFalse(engine.decide(VIDEO).block);
    }

    @Test
    public void emptyAllowList_behavesAsBefore() {
        env.focus = true;
        engine.setFocusExemptPackages(FocusAllowlist.focusExemptPackages(SYSTEM_EXEMPT,
                Collections.<String>emptySet()));

        // Como antes de u10: solo se libran nuestra app, el launcher y el asistente.
        assertFalse(engine.decide(OWN).block);
        assertFalse(engine.decide(LAUNCHER).block);
        assertFalse(engine.decide(ASSIST).block);
        BlockDecisionEngine.Decision dialer = engine.decide(DIALER);
        assertTrue(dialer.block);
        assertTrue(dialer.focusMode);
        BlockDecisionEngine.Decision video = engine.decide(VIDEO);
        assertTrue(video.block);
        assertTrue(video.focusMode);
    }

    @Test
    public void emptyAllowList_outsideFocusOnlyLimitsBlock() {
        engine.setFocusExemptPackages(FocusAllowlist.focusExemptPackages(SYSTEM_EXEMPT, null));
        limit(VIDEO, 30);
        env.usage.put(VIDEO, TimeUnit.MINUTES.toMillis(31));

        assertFalse(engine.decide(DIALER).block);
        BlockDecisionEngine.Decision video = engine.decide(VIDEO);
        assertTrue(video.block);
        assertFalse(video.focusMode);
    }

    @Test
    public void focusAllowList_keepsLauncherAndAssistantExempt() {
        env.focus = true;
        allow(DIALER);

        assertFalse(engine.decide(LAUNCHER).block);
        assertFalse(engine.decide(ASSIST).block);
        assertFalse(engine.decide(OWN).block);
    }

    @Test
    public void focusAllowList_removingAnAppBlocksItAgainWithoutReconnecting() {
        env.focus = true;
        allow(DIALER, MAPS);
        assertFalse(engine.decide(DIALER).block);

        // El servicio recalcula el conjunto al cambiar la lista (mismo motor, sin reconectar).
        allow(MAPS);

        BlockDecisionEngine.Decision d = engine.decide(DIALER);
        assertTrue(d.block);
        assertTrue(d.focusMode);
        assertFalse(engine.decide(MAPS).block);
    }

    @Test
    public void focusAllowList_doesNotMatterOutsideFocus() {
        allow(DIALER);

        assertFalse(engine.decide(DIALER).block);
        assertFalse(engine.decide(VIDEO).block);
    }

    @Test
    public void focusAllowList_nullExemptSetIsTreatedAsEmpty() {
        env.focus = true;
        engine.setFocusExemptPackages(null);

        assertTrue(engine.decide(DIALER).block);
        assertFalse(engine.decide(OWN).block);
    }

    // ---- helpers

    private void allow(String... packages) {
        engine.setFocusExemptPackages(FocusAllowlist.focusExemptPackages(SYSTEM_EXEMPT,
                Arrays.asList(packages)));
    }

    private void limit(String pkg, int minutes) {
        AppRule rule = new AppRule(pkg, true);
        rule.dailyLimitMinutes = minutes;
        rule.enforceAtLimit = true;
        engine.setEnforcedRules(Collections.singletonList(rule));
    }

    private static final class FakeEnv implements BlockDecisionEngine.Env {
        boolean focus;
        boolean enforcement = true;
        final Map<String, Long> usage = new HashMap<>();

        @Override
        public boolean isFocusBlockActive() {
            return focus;
        }

        @Override
        public boolean isEnforcementEnabled() {
            return enforcement;
        }

        @Override
        public String ownPackageName() {
            return OWN;
        }

        @Override
        public long usageTodayMillis(String packageName) {
            Long v = usage.get(packageName);
            return v == null ? 0L : v;
        }
    }
}
