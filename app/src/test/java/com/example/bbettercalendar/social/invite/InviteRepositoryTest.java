package com.example.bbettercalendar.social.invite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.bbettercalendar.social.common.ListenerException;
import com.example.bbettercalendar.social.friends.FriendCard;
import com.example.bbettercalendar.social.friends.FriendsBackend;

import org.junit.Before;
import org.junit.Test;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * F2-u6: {@link InviteRepository} con {@link FriendsBackend} simulado (Mockito): el código propio se lee o se
 * crea en un lote, un choque se reintenta como mucho 3 veces, y resolver distingue vigente, propio y caducado.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class InviteRepositoryTest {

    private static final Executor DIRECT = Runnable::run;

    private FriendsBackend backend;
    private FriendsBackend.Batch batch;
    /** Documentos del «servidor» por ruta; una ruta ausente no existe. */
    private final Map<String, Map<String, Object>> server = new HashMap<>();
    /** Resultado de cada commit, en orden: null = éxito; si no, el error. Vacío = éxito. */
    private final LinkedList<Throwable> commits = new LinkedList<>();
    private InviteRepository repo;

    private String code;
    private InviteRepository.Resolution resolution;
    private Throwable error;

    private final InviteRepository.CodeCallback codeCallback = new InviteRepository.CodeCallback() {
        @Override
        public void onCode(String c) {
            code = c;
        }

        @Override
        public void onError(Throwable e) {
            error = e;
        }
    };

    private final InviteRepository.ResolveCallback resolveCallback = new InviteRepository.ResolveCallback() {
        @Override
        public void onResolved(InviteRepository.Resolution r) {
            resolution = r;
        }

        @Override
        public void onError(Throwable e) {
            error = e;
        }
    };

    @Before
    public void setUp() {
        backend = mock(FriendsBackend.class);
        batch = mock(FriendsBackend.Batch.class);
        when(backend.currentUid()).thenReturn("me");
        when(backend.batch()).thenReturn(batch);
        when(batch.set(anyString(), anyMap())).thenReturn(batch);
        when(batch.update(anyString(), anyMap())).thenReturn(batch);
        doAnswer(inv -> {
            String path = inv.getArgument(0);
            FriendsBackend.Result r = inv.getArgument(2);
            String id = path.substring(path.lastIndexOf('/') + 1);
            r.onSuccess(new FriendsBackend.Doc(id, server.get(path), false));
            return null;
        }).when(backend).getFromServer(anyString(), any(), any());
        doAnswer(inv -> {
            FriendsBackend.Result r = inv.getArgument(1);
            Throwable t = commits.isEmpty() ? null : commits.removeFirst();
            if (t == null) {
                r.onSuccess(null);
            } else {
                r.onError(t);
            }
            return null;
        }).when(batch).commit(any(), any());
        repo = new InviteRepository(backend, DIRECT, new SecureRandom());
    }

    private static Map<String, Object> doc(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private void meWithProfile() {
        server.put("users/me", doc("ageBand", "18+"));
        server.put("profiles/me", doc("handle", "alice", "displayName", "Alice", "avatarColor", 2L));
    }

    @Test
    public void ensureMyCodeReturnsTheSavedCodeWithoutWriting() {
        meWithProfile();
        server.get("users/me").put("inviteCode", "ABCD2345");
        repo.ensureMyCode(codeCallback);
        assertEquals("ABCD2345", code);
        verify(backend, never()).batch();
    }

    @Test
    public void ensureMyCodeCreatesInviteAndUserCodeInOneBatch() {
        meWithProfile();
        repo.ensureMyCode(codeCallback);
        assertTrue(InviteCodes.isValid(code));
        verify(batch).set(eq("invites/" + code), eq(InviteWrites.inviteCreate("me", "alice")));
        verify(batch).update(eq("users/me"), eq(InviteWrites.userInviteCode(code)));
        verify(batch, times(1)).commit(any(), any());
        assertNull(error);
    }

    @Test
    public void collisionIsRetriedAtMostThreeTimes() {
        meWithProfile();
        // Todos los códigos que se prueben ya existen: 1 intento + 3 reintentos y error.
        doAnswer(inv -> {
            String path = inv.getArgument(0);
            FriendsBackend.Result r = inv.getArgument(2);
            String id = path.substring(path.lastIndexOf('/') + 1);
            Map<String, Object> data = path.startsWith("invites/") ? doc("uid", "other", "handle", "bob")
                    : server.get(path);
            r.onSuccess(new FriendsBackend.Doc(id, data, false));
            return null;
        }).when(backend).getFromServer(anyString(), any(), any());
        repo.ensureMyCode(codeCallback);
        assertNull(code);
        assertTrue(error instanceof InviteRepository.InviteException);
        assertEquals(InviteRepository.InviteException.Code.NO_FREE_CODE,
                ((InviteRepository.InviteException) error).code);
        verify(backend, times(1 + InviteRepository.MAX_COLLISION_RETRIES))
                .getFromServer(org.mockito.ArgumentMatchers.startsWith("invites/"), any(), any());
        verify(backend, never()).batch();
    }

    @Test
    public void deniedBatchRereadsUserAndRetries() {
        meWithProfile();
        commits.add(new ListenerException("PERMISSION_DENIED", null)); // carrera: el código lo creó otro
        repo.ensureMyCode(codeCallback);
        assertTrue(InviteCodes.isValid(code));
        verify(batch, times(2)).commit(any(), any());
        verify(backend, times(2)).getFromServer(eq("users/me"), any(), any());
        assertNull(error);
    }

    @Test
    public void ensureMyCodeWithoutProfileOrSessionFails() {
        repo.ensureMyCode(codeCallback);
        assertEquals(InviteRepository.InviteException.Code.NO_PROFILE,
                ((InviteRepository.InviteException) error).code);
        when(backend.currentUid()).thenReturn(null);
        repo.ensureMyCode(codeCallback);
        assertEquals(InviteRepository.InviteException.Code.NOT_SIGNED_IN,
                ((InviteRepository.InviteException) error).code);
    }

    @Test
    public void resolveValidInviteGivesTheCard() {
        server.put("invites/ABCD2345", doc("uid", "bob", "handle", "bob"));
        server.put("handles/bob", doc("uid", "bob", "displayName", "Bob", "avatarColor", 3L));
        repo.resolve("abcd2345", resolveCallback);
        assertEquals(InviteRepository.Resolution.Kind.VALID, resolution.kind);
        assertEquals("ABCD2345", resolution.code);
        assertEquals(new FriendCard("bob", "bob", "Bob", 3), resolution.card);
    }

    @Test
    public void resolveExpiredWhenHandleIsNoLongerTheInviters() {
        server.put("invites/ABCD2345", doc("uid", "bob", "handle", "bob"));
        server.put("handles/bob", doc("uid", "carol", "displayName", "Carol", "avatarColor", 1L));
        repo.resolve("ABCD2345", resolveCallback);
        assertEquals(InviteRepository.Resolution.Kind.EXPIRED, resolution.kind);

        server.remove("handles/bob");
        repo.resolve("ABCD2345", resolveCallback);
        assertEquals(InviteRepository.Resolution.Kind.EXPIRED, resolution.kind);

        repo.resolve("ZZZZ9999", resolveCallback); // no existe
        assertEquals(InviteRepository.Resolution.Kind.EXPIRED, resolution.kind);

        repo.resolve("ABCD1234", resolveCallback); // sin formato: ni se lee
        assertEquals(InviteRepository.Resolution.Kind.EXPIRED, resolution.kind);
        assertNull(resolution.code);
        verify(backend, never()).getFromServer(eq("invites/ABCD1234"), any(), any());
    }

    @Test
    public void resolveOwnInviteIsSelf() {
        server.put("invites/ABCD2345", doc("uid", "me", "handle", "alice"));
        repo.resolve("ABCD2345", resolveCallback);
        assertEquals(InviteRepository.Resolution.Kind.SELF, resolution.kind);
        verify(backend, never()).getFromServer(eq("handles/alice"), any(), any());
    }
}
