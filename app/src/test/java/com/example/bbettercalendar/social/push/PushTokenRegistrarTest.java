package com.example.bbettercalendar.social.push;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;
import androidx.lifecycle.MutableLiveData;

import com.example.bbettercalendar.social.common.Scheduler;
import com.example.bbettercalendar.social.common.SerialExecutor;
import com.example.bbettercalendar.social.profile.Profile;
import com.example.bbettercalendar.social.profile.ProfileState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

/**
 * F5-u6: {@link PushTokenRegistrar} contra un {@link PushTokenBackend} falso en memoria: {@code users/{uid}} con su
 * lista {@code fcmTokens} y el token local del dispositivo. Se comprueba lo que queda en la lista (lo que verían las
 * Functions) y, al cerrar sesión, el orden: el token sale antes de que quien llama cierre la sesión.
 */
public class PushTokenRegistrarTest {

    private static final ProfileState ALICE = ProfileState.ready(new Profile("alice", "Alice", "alice", 0, null, null));
    private static final ProfileState BOB = ProfileState.ready(new Profile("bob", "Bob", "bob", 1, null, null));

    /** Firestore + Auth + FCM en memoria. Las respuestas son síncronas salvo con {@link #hold}. */
    private static final class FakeBackend implements PushTokenBackend {
        final Map<String, List<String>> users = new HashMap<>();
        final List<String> events = new ArrayList<>();
        @Nullable String uid;
        @Nullable String deviceToken;
        int tokenSeq;
        int writes;
        boolean hold;

        @Nullable
        @Override
        public String currentUid() {
            return uid;
        }

        @Override
        public void fetchToken(@NonNull Executor executor, @NonNull TokenCallback callback) {
            if (deviceToken == null) {
                deviceToken = "tok" + (++tokenSeq);
            }
            String t = deviceToken;
            executor.execute(() -> callback.onToken(t));
        }

        @Override
        public void deleteToken(@NonNull Executor executor, @NonNull Callback callback) {
            if (hold) {
                return;
            }
            events.add("deleteLocal " + deviceToken);
            deviceToken = null;
            executor.execute(callback::onSuccess);
        }

        @Override
        public void updateTokens(@NonNull String forUid, @NonNull UnaryOperator<List<String>> change,
                                 @NonNull Executor executor, @NonNull Callback callback) {
            if (hold) {
                return;
            }
            List<String> current = users.get(forUid);
            if (current != null) {
                List<String> next = change.apply(new ArrayList<>(current));
                if (!next.equals(current)) {
                    users.put(forUid, next);
                    writes++;
                    events.add("write " + forUid + " " + next);
                }
            }
            executor.execute(callback::onSuccess);
        }
    }

    /** Programador con reloj falso: nada corre hasta {@link #advance}. */
    private static final class ManualScheduler implements Scheduler {
        private final List<long[]> times = new ArrayList<>();
        private final List<Runnable> tasks = new ArrayList<>();
        private long now;

        @NonNull
        @Override
        public Cancellable schedule(long delayMs, @NonNull Runnable task) {
            long[] at = {now + delayMs};
            times.add(at);
            tasks.add(task);
            return () -> at[0] = Long.MAX_VALUE;
        }

        void advance(long ms) {
            now += ms;
            for (int i = 0; i < tasks.size(); i++) {
                if (times.get(i)[0] <= now) {
                    times.get(i)[0] = Long.MAX_VALUE;
                    tasks.get(i).run();
                }
            }
        }
    }

    private MutableLiveData<ProfileState> profiles;
    private FakeBackend backend;
    private ManualScheduler scheduler;
    private final List<Throwable> logged = new ArrayList<>();
    private boolean enabled = true;

    @Before
    public void setUp() {
        ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            @Override
            public void executeOnDiskIO(@NonNull Runnable runnable) {
                runnable.run();
            }

            @Override
            public void postToMainThread(@NonNull Runnable runnable) {
                runnable.run();
            }

            @Override
            public boolean isMainThread() {
                return true;
            }
        });
        profiles = new MutableLiveData<>(ProfileState.loading());
        backend = new FakeBackend();
        backend.uid = "alice";
        backend.users.put("alice", new ArrayList<>());
        scheduler = new ManualScheduler();
    }

    @After
    public void tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    private PushTokenRegistrar started() {
        PushTokenRegistrar r = new PushTokenRegistrar(profiles, backend, new SerialExecutor(Runnable::run), scheduler,
                () -> enabled, (message, error) -> logged.add(error));
        r.start();
        return r;
    }

    private static List<String> tokens(String... t) {
        return new ArrayList<>(Arrays.asList(t));
    }

    // ---- añadir --------------------------------------------------------------------------------------------

    @Test
    public void readyProfileAddsThisDevicesToken() {
        started();
        assertEquals(Collections.emptyList(), backend.users.get("alice"));

        profiles.setValue(ALICE);

        assertEquals(tokens("tok1"), backend.users.get("alice"));
    }

    @Test
    public void appStartWithSessionAndProfileRegistersOnceEvenIfTheProfileEmitsAgain() {
        profiles.setValue(ALICE); // sesión y perfil ya existen al arrancar
        started();
        profiles.setValue(ProfileState.ready(new Profile("alice", "Alicia", "alice", 3, null, null)));

        assertEquals(tokens("tok1"), backend.users.get("alice"));
        assertEquals(1, backend.writes);
    }

    @Test
    public void tokenAlreadyPresentIsNotDuplicated() {
        backend.deviceToken = "tokA";
        backend.users.put("alice", tokens("old1", "tokA", "old2"));
        started();

        profiles.setValue(ALICE);

        assertEquals(tokens("old1", "tokA", "old2"), backend.users.get("alice"));
        assertEquals(0, backend.writes);
    }

    @Test
    public void fullListDropsTheOldestBeforeAdding() {
        List<String> ten = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ten.add("t" + i);
        }
        backend.users.put("alice", ten);
        backend.deviceToken = "fresh";
        started();

        profiles.setValue(ALICE);

        assertEquals(tokens("t1", "t2", "t3", "t4", "t5", "t6", "t7", "t8", "t9", "fresh"),
                backend.users.get("alice"));
    }

    @Test
    public void listOverTheCapIsTrimmedToNinePlusTheNewToken() {
        // FcmTokens directamente: una lista anómala de 12 (escrita a mano) queda en 10.
        List<String> twelve = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            twelve.add("t" + i);
        }
        List<String> out = FcmTokens.with(twelve, "fresh");

        assertEquals(10, out.size());
        assertEquals("t3", out.get(0));
        assertEquals("fresh", out.get(9));
        assertEquals(tokens("a", "b"), FcmTokens.clean(Arrays.asList("a", 7, "", null, "b", "a")));
    }

    @Test
    public void onNewTokenReplacesTheOldOne() {
        backend.users.put("alice", tokens("otherPhone"));
        PushTokenRegistrar r = started();
        profiles.setValue(ALICE);
        assertEquals(tokens("otherPhone", "tok1"), backend.users.get("alice"));

        r.onNewToken("rotated");

        assertEquals(tokens("otherPhone", "rotated"), backend.users.get("alice"));
    }

    @Test
    public void onNewTokenWithoutReadyProfileWritesNothing() {
        PushTokenRegistrar r = started(); // perfil aún LOADING

        r.onNewToken("rotated");

        assertEquals(0, backend.writes);
        assertEquals(Collections.emptyList(), backend.users.get("alice"));
    }

    @Test
    public void noSessionWritesNothing() {
        backend.uid = null; // el perfil aún dice READY pero Auth ya no tiene sesión
        PushTokenRegistrar r = started();

        profiles.setValue(ALICE);
        r.onNewToken("rotated");

        assertEquals(0, backend.writes);
    }

    @Test
    public void flagOffNeverTouchesTokens() {
        enabled = false;
        PushTokenRegistrar r = started();
        AtomicInteger done = new AtomicInteger();

        profiles.setValue(ALICE);
        r.onNewToken("rotated");
        r.beforeSignOut(done::incrementAndGet);

        assertEquals(0, backend.writes);
        assertTrue(backend.events.isEmpty());
        assertNull(backend.deviceToken);
        assertEquals(1, done.get());
    }

    // ---- quitar --------------------------------------------------------------------------------------------

    @Test
    public void signOutRemovesTheTokenAndTheLocalOneBeforeCallingBack() {
        backend.users.put("alice", tokens("otherPhone"));
        PushTokenRegistrar r = started();
        profiles.setValue(ALICE);

        r.beforeSignOut(() -> backend.events.add("auth signOut"));

        assertEquals(tokens("otherPhone"), backend.users.get("alice"));
        assertEquals(Arrays.asList(
                "write alice [otherPhone, tok1]",
                "write alice [otherPhone]",
                "deleteLocal tok1",
                "auth signOut"), backend.events);
    }

    @Test
    public void signOutWithoutSessionJustCallsBack() {
        PushTokenRegistrar r = started();
        backend.uid = null;
        AtomicInteger done = new AtomicInteger();

        r.beforeSignOut(done::incrementAndGet);

        assertEquals(1, done.get());
        assertEquals(0, backend.writes);
        assertTrue(backend.events.isEmpty());
    }

    @Test
    public void signOutBeforeRegistrationStillRemovesTheLocalToken() {
        backend.deviceToken = "tokA";
        backend.users.put("alice", tokens("tokA", "otherPhone")); // registrado en un arranque anterior
        PushTokenRegistrar r = started(); // este proceso aún no vio el perfil
        AtomicInteger done = new AtomicInteger();

        r.beforeSignOut(done::incrementAndGet);

        assertEquals(tokens("otherPhone"), backend.users.get("alice"));
        assertNull(backend.deviceToken);
        assertEquals(1, done.get());
    }

    @Test
    public void signOutCallsBackOnceAfterTheTimeoutWhenTheServerNeverAnswers() {
        PushTokenRegistrar r = started();
        profiles.setValue(ALICE);
        backend.hold = true;
        AtomicInteger done = new AtomicInteger();

        r.beforeSignOut(done::incrementAndGet);
        scheduler.advance(PushTokenRegistrar.ACK_TIMEOUT_MS - 1);
        assertEquals(0, done.get());
        scheduler.advance(1);
        assertEquals(1, done.get());
        scheduler.advance(PushTokenRegistrar.ACK_TIMEOUT_MS);
        assertEquals(1, done.get());
    }

    @Test
    public void afterSignOutStartsNothingIsAddedUntilTheSessionChanges() {
        PushTokenRegistrar r = started();
        profiles.setValue(ALICE);
        r.beforeSignOut(() -> { });

        r.onNewToken("late");
        assertEquals(Collections.emptyList(), backend.users.get("alice"));

        // Auth cierra la sesión y entra otra cuenta: se registra para ella.
        profiles.setValue(ProfileState.signedOut());
        backend.uid = "bob";
        backend.users.put("bob", new ArrayList<>());
        profiles.setValue(BOB);

        assertEquals(Collections.emptyList(), backend.users.get("alice"));
        assertEquals(tokens("tok2"), backend.users.get("bob"));
    }

    @Test
    public void deleteAccountRemovesFirstAndReRegistersIfTheFunctionFails() {
        PushTokenRegistrar r = started();
        profiles.setValue(ALICE);
        List<String> atCallable = new ArrayList<>();

        r.beforeDeleteAccount(() -> atCallable.addAll(backend.users.get("alice")));
        assertEquals(Collections.emptyList(), atCallable);
        assertNull(backend.deviceToken);

        r.onDeleteAccountFailed();

        assertEquals(tokens("tok2"), backend.users.get("alice"));
        assertFalse(logged.size() > 0);
    }
}
