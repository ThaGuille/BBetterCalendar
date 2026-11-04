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

    private final BlockRepository blocks;
    private final MutableLiveData<Event> event = new MutableLiveData<>();

    @Inject
    public BlockViewModel(BlockRepository blocks) {
        this.blocks = blocks;
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

    public void block(@NonNull String uid, @NonNull String displayName) {
        blocks.block(uid, result -> event.postValue(result == BlockResult.BLOCKED
                ? new Event(R.string.block_toast_blocked, displayName, true)
                : new Event(R.string.block_error, null, false)));
    }

    public void unblock(@NonNull String uid, @NonNull String displayName) {
        blocks.unblock(uid, ok -> event.postValue(ok
                ? new Event(R.string.block_toast_unblocked, displayName, false)
                : new Event(R.string.block_unblock_error, null, false)));
    }
}
