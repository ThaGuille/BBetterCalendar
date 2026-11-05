package com.example.bbettercalendar.ui.social;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.social.block.BlockRepository;
import com.example.bbettercalendar.social.block.BlockResult;
import com.example.bbettercalendar.social.friends.FriendCard;
import com.example.bbettercalendar.social.friends.ReportReason;
import com.example.bbettercalendar.social.friends.ReportRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

/**
 * L-review F5: «Reportar y bloquear a la vez». El reporte va ANTES de {@code blockUser} (ya bloqueado, las reglas
 * lo deniegan) y, si el reporte falla, no se bloquea y el fallo se ve.
 */
public class BlockViewModelTest {

    private final FriendCard bob = new FriendCard("bob", "bob_h", "Bob", 1);
    private BlockRepository blocks;
    private ReportRepository reports;
    private BlockViewModel vm;

    @Before
    public void setUp() {
        ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            @Override
            public void executeOnDiskIO(Runnable r) {
                r.run();
            }

            @Override
            public void postToMainThread(Runnable r) {
                r.run();
            }

            @Override
            public boolean isMainThread() {
                return true;
            }
        });
        blocks = mock(BlockRepository.class);
        reports = mock(ReportRepository.class);
        doAnswer(inv -> {
            ((BlockRepository.Callback) inv.getArgument(1)).onResult(BlockResult.BLOCKED);
            return null;
        }).when(blocks).block(anyString(), any(BlockRepository.Callback.class));
        vm = new BlockViewModel(blocks, reports);
    }

    @After
    public void tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    private void reportAnswers(boolean ok, boolean already) {
        doAnswer(inv -> {
            ReportRepository.Callback cb = inv.getArgument(4);
            if (ok) {
                cb.onReported(already);
            } else {
                cb.onError(new RuntimeException("net"));
            }
            return null;
        }).when(reports).report(anyString(), anyString(), any(ReportReason.class),
                any(ReportReason.Context.class), any(ReportRepository.Callback.class));
    }

    @Test
    public void reportIsSentBeforeBlockUserRuns() {
        reportAnswers(true, false);
        vm.block(bob, ReportReason.HARASSMENT, ReportReason.Context.FRIEND);
        InOrder order = inOrder(reports, blocks);
        order.verify(reports).report(eq("bob"), eq("bob_h"), eq(ReportReason.HARASSMENT),
                eq(ReportReason.Context.FRIEND), any(ReportRepository.Callback.class));
        order.verify(blocks).block(eq("bob"), any(BlockRepository.Callback.class));
        assertTrue(vm.getEvent().getValue().blocked);
    }

    @Test
    public void anAlreadyReportedPersonIsStillBlockedAfterwards() {
        reportAnswers(true, true);
        vm.block(bob, ReportReason.OTHER, ReportReason.Context.REQUEST);
        verify(blocks).block(eq("bob"), any(BlockRepository.Callback.class));
    }

    @Test
    public void aFailedReportDoesNotBlockAndShowsTheFailure() {
        reportAnswers(false, false);
        vm.block(bob, ReportReason.OTHER, ReportReason.Context.FRIEND);
        verify(blocks, never()).block(anyString(), any(BlockRepository.Callback.class));
        assertNotNull(vm.getEvent().getValue());
        assertEquals(R.string.block_report_failed, vm.getEvent().getValue().message);
        assertFalse(vm.getEvent().getValue().blocked);
    }

    @Test
    public void withoutReportingItJustBlocks() {
        vm.block(bob, null, ReportReason.Context.FRIEND);
        verify(reports, never()).report(anyString(), anyString(), any(ReportReason.class),
                any(ReportReason.Context.class), any(ReportRepository.Callback.class));
        verify(blocks).block(eq("bob"), any(BlockRepository.Callback.class));
    }
}
