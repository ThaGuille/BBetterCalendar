package com.example.bbettercalendar.social.interaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * F5-u5: {@link InteractionRepository} con un {@link InteractionBackend} escrito a mano (respuestas del servidor
 * guionizadas) y un registro en memoria: la traducción de cada respuesta, el {@code dayKey} del que envía y, sobre
 * todo, el registro local de «ya enviado hoy» (por mi uid, purgado de otros días y otras cuentas).
 */
public class InteractionRepositoryTest {

    private static final Executor DIRECT = Runnable::run;

    /** Un servidor falso: apunta lo que se le pide y responde lo guionizado. */
    private static final class FakeBackend implements InteractionBackend {
        String uid = "meA";
        Map<String, Object> answer;
        Failure failure;
        final List<String> calls = new ArrayList<>();

        @Nullable
        @Override
        public String currentUid() {
            return uid;
        }

        @Override
        public void call(@NonNull String callable, @NonNull String friendUid, @NonNull String dayKey,
                         @NonNull Executor executor, @NonNull Result result) {
            calls.add(callable + " " + friendUid + " " + dayKey);
            if (failure != null) {
                result.onError(failure);
            } else {
                result.onSuccess(answer);
            }
        }
    }

    private static final class MemoryStore implements SentLogStore {
        Set<String> entries = new HashSet<>();
        boolean cleared;

        @NonNull
        @Override
        public Set<String> read() {
            return new HashSet<>(entries);
        }

        @Override
        public void write(@NonNull Set<String> e) {
            entries = new HashSet<>(e);
        }

        @Override
        public void clear() {
            entries = new HashSet<>();
            cleared = true;
        }
    }

    private FakeBackend backend;
    private MemoryStore store;
    private LocalDate day;
    private InteractionRepository repo;

    @Before
    public void setUp() {
        backend = new FakeBackend();
        store = new MemoryStore();
        day = LocalDate.of(2026, 11, 2);
        repo = new InteractionRepository(backend, DIRECT, store, () -> day);
    }

    private static Map<String, Object> status(String s) {
        return Collections.singletonMap("status", (Object) s);
    }

    private InteractionResult send(InteractionType type, String friend) {
        final InteractionResult[] out = new InteractionResult[1];
        repo.send(type, friend, r -> out[0] = r);
        return out[0];
    }

    private boolean sentToday(InteractionType type, String friend) {
        final boolean[] out = new boolean[1];
        repo.sentToday(type, friend, s -> out[0] = s);
        return out[0];
    }

    @Test
    public void sentIsMappedAndRemembered() {
        backend.answer = status("sent");
        assertEquals(InteractionResult.SENT, send(InteractionType.CHEER, "bob"));
        assertTrue(sentToday(InteractionType.CHEER, "bob"));
    }

    @Test
    public void alreadyIsMappedAndAlsoRemembered() {
        backend.answer = status("already");
        assertEquals(InteractionResult.ALREADY_SENT, send(InteractionType.NUDGE, "bob"));
        assertTrue("the server says it went out today, so the screen must know", sentToday(InteractionType.NUDGE, "bob"));
    }

    @Test
    public void studiedTodayAndNotFriendsAreMappedFromTheServerMessageAndNotRemembered() {
        backend.failure = new InteractionBackend.Failure("FAILED_PRECONDITION", "studied-today", null);
        assertEquals(InteractionResult.STUDIED_TODAY, send(InteractionType.NUDGE, "bob"));
        backend.failure = new InteractionBackend.Failure("PERMISSION_DENIED", "not-friends", null);
        assertEquals(InteractionResult.NOT_FRIENDS, send(InteractionType.CHEER, "bob"));
        assertFalse(sentToday(InteractionType.NUDGE, "bob"));
        assertFalse(sentToday(InteractionType.CHEER, "bob"));
    }

    @Test
    public void anythingElseIsAnErrorAndNeverRemembered() {
        backend.failure = new InteractionBackend.Failure("UNAVAILABLE", "offline", null);
        assertEquals(InteractionResult.ERROR, send(InteractionType.CHEER, "bob"));
        backend.failure = new InteractionBackend.Failure("FAILED_PRECONDITION", "no-profile", null);
        assertEquals(InteractionResult.ERROR, send(InteractionType.CHEER, "bob"));
        backend.failure = null;
        backend.answer = status("surprise");
        assertEquals(InteractionResult.ERROR, send(InteractionType.CHEER, "bob"));
        backend.answer = null;
        assertEquals(InteractionResult.ERROR, send(InteractionType.CHEER, "bob"));
        assertFalse(sentToday(InteractionType.CHEER, "bob"));
    }

    @Test
    public void itCallsTheRightCallableWithTheSendersOwnDayKey() {
        backend.answer = status("sent");
        send(InteractionType.CHEER, "bob");
        send(InteractionType.NUDGE, "carol");
        assertEquals("sendCheer bob 2026-11-02", backend.calls.get(0));
        assertEquals("sendNudge carol 2026-11-02", backend.calls.get(1));
    }

    @Test
    public void withoutASessionItIsAnErrorAndCallsNothing() {
        backend.uid = null;
        backend.answer = status("sent");
        assertEquals(InteractionResult.ERROR, send(InteractionType.CHEER, "bob"));
        assertTrue(backend.calls.isEmpty());
        assertFalse(sentToday(InteractionType.CHEER, "bob"));
    }

    @Test
    public void cheerAndNudgeAreRememberedSeparatelyPerFriend() {
        backend.answer = status("sent");
        send(InteractionType.CHEER, "bob");
        assertTrue(sentToday(InteractionType.CHEER, "bob"));
        assertFalse(sentToday(InteractionType.NUDGE, "bob"));
        assertFalse(sentToday(InteractionType.CHEER, "carol"));
    }

    @Test
    public void anotherAccountOnTheSameDeviceSeesNothingTheLastOneSent() {
        backend.answer = status("sent");
        send(InteractionType.CHEER, "bob");
        assertTrue(sentToday(InteractionType.CHEER, "bob"));

        backend.uid = "meB";
        assertFalse("account B must not see account A's sent state", sentToday(InteractionType.CHEER, "bob"));
        // Y la lectura de B ha purgado lo de A: volver a A tampoco lo recupera.
        assertTrue(store.entries.isEmpty());
        backend.uid = "meA";
        assertFalse(sentToday(InteractionType.CHEER, "bob"));
    }

    @Test
    public void accountBSendingPurgesAccountAsEntriesAndKeepsItsOwn() {
        backend.answer = status("sent");
        send(InteractionType.CHEER, "bob");
        backend.uid = "meB";
        send(InteractionType.NUDGE, "carol");
        assertEquals(Collections.singleton("meB|carol|nudge|2026-11-02"), store.entries);
        assertTrue(sentToday(InteractionType.NUDGE, "carol"));
    }

    @Test
    public void entriesOfOtherDaysArePurgedOnRead() {
        store.entries.add("meA|bob|cheer|2026-11-01");
        store.entries.add("meA|carol|nudge|2026-11-03");
        store.entries.add("meA|bob|nudge|2026-11-02");
        assertFalse(sentToday(InteractionType.CHEER, "bob"));
        assertEquals(Collections.singleton("meA|bob|nudge|2026-11-02"), store.entries);
    }

    @Test
    public void whenTheDayChangesYesterdaysSendIsForgotten() {
        backend.answer = status("sent");
        send(InteractionType.CHEER, "bob");
        assertTrue(sentToday(InteractionType.CHEER, "bob"));
        day = day.plusDays(1);
        assertFalse(sentToday(InteractionType.CHEER, "bob"));
        assertTrue(store.entries.isEmpty());
    }

    @Test
    public void sendingPurgesStaleEntriesToo() {
        store.entries.add("meA|bob|cheer|2026-10-30");
        store.entries.add("meZ|bob|cheer|2026-11-02");
        store.entries.add("garbage");
        backend.answer = status("sent");
        send(InteractionType.CHEER, "carol");
        assertEquals(Collections.singleton("meA|carol|cheer|2026-11-02"), store.entries);
    }

    @Test
    public void clearLocalLogRemovesTheWholeFile() {
        backend.answer = status("sent");
        send(InteractionType.CHEER, "bob");
        repo.clearLocalLog();
        assertTrue(store.cleared);
        assertTrue(store.entries.isEmpty());
        assertFalse(sentToday(InteractionType.CHEER, "bob"));
    }

    @Test
    public void theServerStaysTheAuthorityEvenWhenTheLogForgot() {
        // El móvil no recuerda nada (otra instalación), pero el servidor dice que ya salió.
        backend.answer = status("already");
        assertEquals(InteractionResult.ALREADY_SENT, send(InteractionType.CHEER, "bob"));
        assertEquals(1, backend.calls.size());
    }
}
