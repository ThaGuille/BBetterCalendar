package com.example.bbettercalendar.ui.social;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.social.block.BlockRepository;
import com.example.bbettercalendar.social.block.BlockResult;
import com.example.bbettercalendar.social.block.BlockState;
import com.example.bbettercalendar.social.friends.FriendCard;
import com.example.bbettercalendar.social.friends.ReportReason;
import com.example.bbettercalendar.social.friends.ReportRepository;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * L-u10: bloquear y desbloquear desde la UI. Los callbacks de {@link BlockRepository} llegan fuera del hilo
 * principal: todo sale con postValue (regla #3). Un error no cambia nada (lo garantiza el servidor).
 */
@HiltViewModel
public class BlockViewModel extends ViewModel {

    /** Un aviso para el fragmento; {@code blocked} es true solo al bloquear con éxito. */
    public static final class Event {
        @StringRes public final int message;
        @Nullable public final String arg;
        public final boolean blocked;

        Event(@StringRes int message, @Nullable String arg, boolean blocked) {
            this.message = message;
            this.arg = arg;
            this.blocked = blocked;
        }
    }

    /** L-review F1: espera máxima a que el reporte se confirme antes de darlo por fallido (offline no resuelve). */
    static final long REPORT_TIMEOUT_MS = 12_000L;

    private final BlockRepository blocks;
    private final ReportRepository reports;
    private final ScheduledExecutorService timer;
    private final long reportTimeoutMs;
    private final MutableLiveData<Event> event = new MutableLiveData<>();
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    /** L-review F2: true mientras un bloqueo (con su reporte) está en curso. */
    private final AtomicBoolean inFlight = new AtomicBoolean(false);

    @Inject
    public BlockViewModel(BlockRepository blocks, ReportRepository reports) {
        this(blocks, reports, Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "block-report-timeout");
            t.setDaemon(true);
            return t;
        }), REPORT_TIMEOUT_MS);
    }

    BlockViewModel(BlockRepository blocks, ReportRepository reports, ScheduledExecutorService timer,
                   long reportTimeoutMs) {
        this.blocks = blocks;
        this.reports = reports;
        this.timer = timer;
        this.reportTimeoutMs = reportTimeoutMs;
    }

    /** True mientras hay un bloqueo en curso: la UI puede desactivar la entrada «Bloquear». */
    public LiveData<Boolean> getBusy() {
        return busy;
    }

    /** La lista de bloqueados; solo hay listener mientras se observa. */
    public LiveData<BlockState> getBlocked() {
        return blocks.observeBlocked();
    }

    public LiveData<Event> getEvent() {
        return event;
    }

    /** Llamar al atender el aviso (hilo principal). */
    public void consumeEvent() {
        event.setValue(null);
    }

    /**
     * L-review F5: bloquear y, si {@code reason != null}, reportar a la vez. Reportar a alguien ya bloqueado
     * lo deniegan las reglas, así que el reporte va PRIMERO y {@code blockUser} solo corre cuando se guardó
     * (o ya existía). Si el reporte falla no se bloquea y el fallo se ve ({@code block_report_failed}).
     */
    public void block(@NonNull FriendCard person, @Nullable ReportReason reason,
                      @NonNull ReportReason.Context context) {
        if (!inFlight.compareAndSet(false, true)) {
            return;
        }
        busy.postValue(true);
        if (reason == null) {
            blockNow(person.uid, person.displayName);
            return;
        }
        // L-review F1: offline el commit del reporte solo resuelve con el ack del servidor; se acota la espera
        // y una confirmación tardía (ya fallado) no bloquea.
        AtomicBoolean settled = new AtomicBoolean(false);
        ScheduledFuture<?> timeout = timer.schedule(() -> {
            if (settled.compareAndSet(false, true)) {
                failReport();
            }
        }, reportTimeoutMs, TimeUnit.MILLISECONDS);
        reports.report(person.uid, person.handle, reason, context, new ReportRepository.Callback() {
            @Override
            public void onReported(boolean alreadyReported) {
                if (settled.compareAndSet(false, true)) {
                    timeout.cancel(false);
                    blockNow(person.uid, person.displayName);
                }
            }

            @Override
            public void onError(@NonNull Throwable error) {
                if (settled.compareAndSet(false, true)) {
                    timeout.cancel(false);
                    failReport();
                }
            }
        });
    }

    private void failReport() {
        inFlight.set(false);
        busy.postValue(false);
        event.postValue(new Event(R.string.block_report_failed, null, false));
    }

    public void block(@NonNull String uid, @NonNull String displayName) {
        if (!inFlight.compareAndSet(false, true)) {
            return;
        }
        busy.postValue(true);
        blockNow(uid, displayName);
    }

    private void blockNow(String uid, String displayName) {
        blocks.block(uid, result -> {
            inFlight.set(false);
            busy.postValue(false);
            event.postValue(result == BlockResult.BLOCKED
                    ? new Event(R.string.block_toast_blocked, displayName, true)
                    : new Event(R.string.block_error, null, false));
        });
    }

    public void unblock(@NonNull String uid, @NonNull String displayName) {
        blocks.unblock(uid, ok -> event.postValue(ok
                ? new Event(R.string.block_toast_unblocked, displayName, false)
                : new Event(R.string.block_unblock_error, null, false)));
    }
}
