package com.example.bbettercalendar.social.friends;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.bbettercalendar.social.common.ListenerException;
import com.example.bbettercalendar.social.common.SerialExecutor;

import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;
import java.util.concurrent.Executor;

/**
 * F2-u5: {@link ReportRepository} con {@link FriendsBackend} simulado (Mockito): reporte y rechazo en un
 * lote con {@code context = request}, y {@code PERMISSION_DENIED} al crear = «ya reportado».
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class ReportRepositoryTest {

    private FriendsBackend backend;
    private FriendsBackend.Batch batch;
    /** Resultado de cada commit, en orden: null = éxito; si no, el error. */
    private final LinkedList<Throwable> outcomes = new LinkedList<>();
    private ReportRepository repo;
    private Boolean already;
    private Throwable error;

    private final ReportRepository.Callback callback = new ReportRepository.Callback() {
        @Override
        public void onReported(boolean alreadyReported) {
            already = alreadyReported;
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
        when(batch.delete(anyString())).thenReturn(batch);
        doAnswer(inv -> {
            FriendsBackend.Result r = inv.getArgument(1);
            Throwable t = outcomes.isEmpty() ? null : outcomes.poll();
            if (t == null) {
                r.onSuccess(null);
            } else {
                r.onError(t);
            }
            return null;
        }).when(batch).commit(any(Executor.class), any(FriendsBackend.Result.class));
        repo = new ReportRepository(backend, new SerialExecutor(Runnable::run));
    }

    private static ListenerException denied() {
        return new ListenerException("PERMISSION_DENIED", null);
    }

    @Test
    public void fromRequest_reportAndDeclineGoInOneBatch() {
        outcomes.add(null);
        repo.report("uidB", "bob", ReportReason.HARASSMENT, ReportReason.Context.REQUEST, callback);
        verify(batch).set(eq("reports/me_uidB"), anyMap());
        verify(batch).delete("friendRequests/uidB_me");
        verify(backend).batch(); // un solo lote
        assertEquals(Boolean.FALSE, already);
        assertNull(error);
    }

    @Test
    public void permissionDeniedOnCreate_countsAsAlreadyReported() {
        outcomes.add(denied());
        repo.report("uidB", "bob", ReportReason.OTHER, ReportReason.Context.FRIEND, callback);
        verify(batch, never()).delete(anyString());
        assertEquals(Boolean.TRUE, already);
        assertNull(error);
    }

    @Test
    public void repeatedReportFromRequest_stillDeclines() {
        outcomes.add(denied()); // lote reporte + rechazo
        outcomes.add(denied()); // reporte solo: ya existía
        outcomes.add(null);     // rechazo solo
        repo.report("uidB", "bob", ReportReason.OFFENSIVE_NAME, ReportReason.Context.REQUEST, callback);
        assertEquals(Boolean.TRUE, already);
        assertNull(error);
    }
}
