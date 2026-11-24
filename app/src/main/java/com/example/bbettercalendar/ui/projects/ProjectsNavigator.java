package com.example.bbettercalendar.ui.projects;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.projects.domain.TaskDraft;
import com.example.bbettercalendar.projects.domain.TaskSnapshot;
import com.example.bbettercalendar.ui.focus.FocusSessionViewModel;
import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine;
import com.example.bbettercalendar.ui.focus.FocusUiState;
import com.example.bbettercalendar.ui.home.FocusTarget;
import com.example.bbettercalendar.ui.projects.quickadd.QuickAddDialog;

/**
 * One method per destination reachable from the Projects screen. {@link #startFocus} is real; the other
 * bodies are placeholders that the later parts of rediseno-proyectos replace, each one only its own, so the
 * Projects screen never has to be edited again to navigate.
 */
public class ProjectsNavigator {

    private final Fragment fragment;

    public ProjectsNavigator(@NonNull Fragment fragment) {
        this.fragment = fragment;
    }

    /** Quick add (A2) with the section pre-selected. */
    public void openQuickAdd(int projectId, int sectionId) {
        // The dialog lives in the NavHostFragment's child manager, the one ProjectsFragment listens on.
        FragmentManager manager = fragment.getParentFragmentManager();
        if (manager.isStateSaved() || manager.findFragmentByTag(QuickAddDialog.TAG) != null) return;
        QuickAddDialog.newInstance(projectId, sectionId).show(manager, QuickAddDialog.TAG);
    }

    /** Full editor (D1) in edit mode. */
    public void openTaskEditor(int projectId, int entryId) {
        android.os.Bundle args = new android.os.Bundle();
        args.putInt("projectId", projectId);
        args.putInt("entryId", entryId);
        NavHostFragment.findNavController(fragment).navigate(R.id.navigation_task_editor, args);
    }

    /** Full editor (D1) opened with the quick add draft. */
    public void openTaskEditorWithDraft(int projectId, TaskDraft draft) {
        android.os.Bundle args = new android.os.Bundle();
        args.putInt("projectId", projectId);
        args.putInt("entryId", 0);
        args.putSerializable("draft", draft);
        NavHostFragment.findNavController(fragment).navigate(R.id.navigation_task_editor, args);
    }

    /** Project form; 0 = create. */
    public void openProjectForm(int projectId) {
        // TODO(rediseno-proyectos): form
    }

    /** Tag or area editor; 0 = create. */
    public void openTagEditor(int kind, int tagId) {
        // TODO(rediseno-proyectos): tag editor
    }

    /** Agenda sheet of the project. */
    public void openAgenda(int projectId) {
        // TODO(rediseno-proyectos): agenda
    }

    /**
     * Binds the task to the Focus session and opens Focus. When a session already runs it is not touched:
     * Focus just opens.
     */
    public void startFocus(@NonNull TaskSnapshot task) {
        FragmentActivity activity = fragment.requireActivity();
        FocusSessionViewModel session = new ViewModelProvider(activity).get(FocusSessionViewModel.class);
        FocusUiState ui = session.getUiState().getValue();
        boolean running = ui != null && ui.getState() != FocusTimerStateMachine.State.STOPPED;
        if (!running) {
            FocusTarget.set((int) task.id, task.title, task.targetMinutes);
            session.startBoundFocusIfIdle();
            // The session is already started: Home must not start another one when it consumes this later.
            FocusTarget.consumePendingAutoStart();
        }
        NavHostFragment.findNavController(fragment).navigate(R.id.navigation_focus);
    }
}
