package com.example.bbettercalendar.ui.focus;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.feedback.HapticFeedback;
import com.example.bbettercalendar.feedback.SoundFeedback;
import com.example.bbettercalendar.popups.AlertPopup;
import com.example.bbettercalendar.popups.FirstFocusCelebrationPopup;
import com.example.bbettercalendar.popups.OnPopupListener;
import com.example.bbettercalendar.popups.PopupHelper;
import com.example.bbettercalendar.ui.common.SingleEvent;

/**
 * u14: the ONE place that turns the focus session's one-shot outcomes into UI, shared by the two
 * screens a session can be watched from — {@code HomeFragment} and {@code FocusFragment}. Each
 * creates one, calls {@link #observe(LifecycleOwner)} with its view lifecycle and
 * {@link #recheck()} from {@code onResume()}.
 *
 * <ul>
 *   <li>All cycles completed → navigate to {@code navigation_cycle_complete} (from Focus, Focus is
 *       popped so back from Cycle complete lands on Home).</li>
 *   <li>Session failed (background grace expired) → {@link AlertPopup}; closing it resets the idle
 *       display.</li>
 *   <li>First pomodoro of the day → {@link FirstFocusCelebrationPopup}.</li>
 *   <li>Bound task auto-completed → haptic + success sound + fallback notification, unbind.</li>
 * </ul>
 *
 * <p><b>Why not MainActivity:</b> creating {@link FocusSessionViewModel} from the activity before
 * Home's onCreateView is unsafe (its constructor reads the configuration on the main thread — u2
 * gotcha), and the outcomes need a fragment to show dialogs / navigate. <b>Why nothing is lost:</b>
 * the four sources are retained LiveData (SingleEvent or a value cleared only after showing), so an
 * outcome that fires while the user is on another tab, in another activity or in background stays
 * pending and is shown by whichever of Home / Focus resumes next (first consumer wins). A session
 * normally runs on Focus, and Focus pops back to Home, so one of them is always close.
 */
public final class FocusSessionEventPresenter implements OnPopupListener<Object> {

    private static final String ALERT_TAG = "focus_failed_popup";

    private final Fragment host;
    private final FocusSessionViewModel session;
    @Nullable
    private final Runnable onTargetCleared;

    /**
     * @param onTargetCleared optional: runs after an auto-completed task unbinds FocusTarget (Home
     *                        repaints its "Focusing: X" line).
     */
    public FocusSessionEventPresenter(@NonNull Fragment host, @NonNull FocusSessionViewModel session,
                                      @Nullable Runnable onTargetCleared) {
        this.host = host;
        this.session = session;
        this.onTargetCleared = onTargetCleared;
    }

    /** Call from onCreateView/onViewCreated with {@code getViewLifecycleOwner()}. */
    public void observe(@NonNull LifecycleOwner owner) {
        session.getCycleCompletedEvents().observe(owner, this::onCycleCompleted);
        session.getSessionFailedEvents().observe(owner, this::onSessionFailed);
        session.getFirstFocusOfDay().observe(owner, this::onFirstFocusOfDay);
        session.getAutoCompletedTaskTitle().observe(owner, this::onAutoCompletedTask);
    }

    /**
     * Safety net from onResume: an outcome delivered while the state was saved (the observer had to
     * skip it) is still pending and can be shown now.
     */
    public void recheck() {
        onCycleCompleted(session.getCycleCompletedEvents().getValue());
        onSessionFailed(session.getSessionFailedEvents().getValue());
        onFirstFocusOfDay(session.getFirstFocusOfDay().getValue());
        onAutoCompletedTask(session.getAutoCompletedTaskTitle().getValue());
    }

    /**
     * The guard is {@code isStateSaved()}, NOT isResumed(): a LiveData observer becomes active at
     * STARTED and the retained value is delivered BEFORE onResume, so requiring RESUMED would drop
     * it on that very delivery (see the old HomeFragment.maybeShowFirstFocusCelebration note).
     */
    private boolean cannotShow() {
        return !host.isAdded() || host.isStateSaved();
    }

    private void onCycleCompleted(@Nullable SingleEvent<Integer> event) {
        if (event == null || event.isHandled() || cannotShow()) {
            return;
        }
        NavController nav = NavHostFragment.findNavController(host);
        NavDestination current = nav.getCurrentDestination();
        if (current == null) {
            return;
        }
        event.consume();
        if (current.getId() == R.id.navigation_cycle_complete) {
            return;
        }
        NavOptions.Builder options = new NavOptions.Builder();
        if (current.getId() == R.id.navigation_focus) {
            // Back desde Cycle complete vuelve a Home: la sesión ya terminó, Focus no pinta nada.
            options.setPopUpTo(R.id.navigation_focus, true);
        }
        nav.navigate(R.id.navigation_cycle_complete, null, options.build());
    }

    private void onSessionFailed(@Nullable SingleEvent<Boolean> event) {
        if (event == null || event.isHandled() || cannotShow()) {
            return;
        }
        event.consume();
        AlertPopup alert = new AlertPopup();
        alert.setOnPopupListener(this);
        alert.selectView(PopupHelper.ALERT_POPUP);
        alert.show(host.getParentFragmentManager(), ALERT_TAG);
    }

    private void onFirstFocusOfDay(@Nullable Integer daysThisMonth) {
        if (daysThisMonth == null || cannotShow()) {
            return;
        }
        FirstFocusCelebrationPopup.newInstance(daysThisMonth)
                .show(host.getParentFragmentManager(), FirstFocusCelebrationPopup.POPUP_TAG);
        session.clearFirstFocusOfDay();
    }

    /** Spec focus-attribution: in-app feedback first, the notification is the fallback. */
    private void onAutoCompletedTask(@Nullable String title) {
        if (title == null || !host.isAdded()) {
            return;
        }
        View view = host.getView();
        if (view != null) {
            HapticFeedback.confirm(view);
        }
        SoundFeedback.get(host.requireContext()).playSuccess();
        session.onAutoCompletedTaskShown(title);
        if (onTargetCleared != null) {
            onTargetCleared.run();
        }
    }

    @Override
    public void OnClosePopup(int popupType) {
        if (popupType == PopupHelper.ALERT_POPUP) {
            session.resetIdleDisplay();
        }
    }

    @Override
    public void OnClosePopup(int popupType, Object result) {
        OnClosePopup(popupType);
    }
}
