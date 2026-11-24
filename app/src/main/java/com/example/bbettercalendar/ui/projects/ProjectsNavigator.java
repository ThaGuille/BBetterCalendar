package com.example.bbettercalendar.ui.projects;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.domain.TaskDraft;
import com.example.bbettercalendar.projects.domain.TaskSnapshot;
import com.example.bbettercalendar.ui.focus.FocusSessionViewModel;
import com.example.bbettercalendar.ui.focus.FocusTimerStateMachine;
import com.example.bbettercalendar.ui.focus.FocusUiState;
import com.example.bbettercalendar.ui.focus.TaskFocusRequest;
import com.example.bbettercalendar.ui.projects.agenda.AgendaSheet;
import com.example.bbettercalendar.ui.projects.form.ProjectFormFragment;
import com.example.bbettercalendar.ui.projects.quickadd.QuickAddDialog;
import com.example.bbettercalendar.ui.projects.tags.TagEditorDialog;

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
        Bundle args = new Bundle();
        args.putInt(ProjectFormFragment.ARG_PROJECT_ID, projectId);
        NavController nav = NavHostFragment.findNavController(fragment);
        // A second tap while the form is already opening must not stack another one.
        NavDestination current = nav.getCurrentDestination();
        if (current != null && current.getId() == R.id.navigation_project_form) return;
        nav.navigate(R.id.navigation_project_form, args);
    }

    /** Tag or area editor; 0 = create. */
    public void openTagEditor(int kind, int tagId) {
        // Like the quick add, the dialog lives in the NavHostFragment's child manager.
        FragmentManager manager = fragment.getParentFragmentManager();
        if (manager.isStateSaved() || manager.findFragmentByTag(TagEditorDialog.TAG) != null) return;
        TagEditorDialog.newInstance(kind, tagId).show(manager, TagEditorDialog.TAG);
    }

    /** Agenda sheet of the project. */
    public void openAgenda(int projectId) {
        // The sheet lives in the NavHostFragment's child manager; never a second one on top.
        FragmentManager manager = fragment.getParentFragmentManager();
        if (manager.isStateSaved() || manager.findFragmentByTag(AgendaSheet.TAG) != null) return;
        AgendaSheet.newInstance(projectId).show(manager, AgendaSheet.TAG);
    }

    /**
     * Binds the task to the Focus session and opens Focus straight (no Home in between). A task without a
     * goal first gets its pomodoro saved ({@link FocusLink}); when a session already runs it is not touched:
     * Focus just opens.
     */
    public void startFocus(@NonNull TaskSnapshot task) {
        FragmentActivity activity = fragment.requireActivity();
        FocusSessionViewModel session = new ViewModelProvider(activity).get(FocusSessionViewModel.class);
        FocusUiState ui = session.getUiState().getValue();
        boolean running = ui != null && ui.getState() != FocusTimerStateMachine.State.STOPPED;
        FocusLink.Action action = FocusLink.decide(task, running);
        if (action == FocusLink.Action.OPEN_ONLY) {
            openFocus();
            return;
        }
        ProjectsViewModel projects = new ViewModelProvider(fragment).get(ProjectsViewModel.class);
        ProjectsScreenModel model = projects.getModel().getValue();
        Project project = model == null ? null : model.selectedProject;
        if (action == FocusLink.Action.START) {
            begin(session, project, task, task.targetMinutes);
        } else {
            projects.ensurePomodoro((int) task.id, goal -> {
                if (goal == null || goal <= 0 || !fragment.isAdded()) return;
                begin(session, project, task, goal);
            });
        }
    }

    private void begin(FocusSessionViewModel session, @Nullable Project project, TaskSnapshot task, int goal) {
        TaskFocusRequest request = FocusLink.request(task, goal,
                project == null ? null : project.name, project == null ? null : project.iconKey,
                project == null ? 0 : project.colorIndex, project == null ? 0 : project.id);
        session.startTaskFocus(request);
        openFocus();
    }

    /** Focus is a secondary destination: one instance, never stacked on a double tap. */
    private void openFocus() {
        NavController nav = NavHostFragment.findNavController(fragment);
        NavDestination current = nav.getCurrentDestination();
        if (current != null && current.getId() == R.id.navigation_projects) {
            nav.navigate(R.id.navigation_focus);
        }
    }
}
