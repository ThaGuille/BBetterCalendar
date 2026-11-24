package com.example.bbettercalendar.ui.projects;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.databinding.FragmentProjectsBinding;
import com.example.bbettercalendar.feedback.HapticFeedback;
import com.example.bbettercalendar.feedback.SoundFeedback;
import com.example.bbettercalendar.projects.Tag;
import com.example.bbettercalendar.projects.TaskSubtask;
import com.example.bbettercalendar.projects.domain.TaskSnapshot;
import com.example.bbettercalendar.ui.common.motion.ScreenTransitions;
import com.example.bbettercalendar.ui.common.widget.ProjectAccents;
import com.example.bbettercalendar.ui.focus.CelebrationPrefs;
import com.example.bbettercalendar.ui.focus.CelebrationSheet;
import com.example.bbettercalendar.ui.focus.FocusSessionViewModel;
import com.example.bbettercalendar.ui.home.FocusTarget;
import com.example.bbettercalendar.ui.projects.list.ProjectsListAdapter;
import com.example.bbettercalendar.ui.projects.list.ProjectsSelectorAdapter;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * The single Projects screen (rediseno-proyectos, D-P02 / D-P16): toolbar, selector of active projects,
 * summary R2 and the sections as cards. It also handles every fragment result of the sub-screens (see
 * {@link ProjectsResults}) and the undoable actions (archive, complete, deletions).
 */
@AndroidEntryPoint
public class ProjectsFragment extends Fragment {

    /** Navigation argument: project to select (0 = keep the remembered one). Consumed once. */
    public static final String ARG_PROJECT_ID = "projectId";

    private static final int MENU_EDIT = 1;
    private static final int MENU_HIDE_DONE = 2;
    private static final int MENU_ADD_SECTION = 3;
    private static final int MENU_FILTER_AREA = 4;
    private static final int MENU_ARCHIVE = 5;
    private static final int MENU_COMPLETE = 6;

    private static final long COMPLETED_SNACK_MS = 7000L;

    private FragmentProjectsBinding binding;
    private ProjectsViewModel viewModel;
    private ProjectsNavigator navigator;
    private ProjectsListAdapter listAdapter;
    private ProjectsSelectorAdapter selectorAdapter;
    private ProjectsScreenModel current;
    private int lastSelectedProject = -1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Snackbar completedSnack;
    private int completedEntryId;
    private final Runnable dismissCompleted = () -> {
        if (completedSnack != null) completedSnack.dismiss();
    };
    private ViewTreeObserver.OnGlobalLayoutListener keyboardListener;

    // u15: transiciones de pantalla (ScreenTransitions: fundido entre pestañas, eje Z/X, contenedor).
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScreenTransitions.apply(this);
        viewModel = new ViewModelProvider(this).get(ProjectsViewModel.class);
        navigator = new ProjectsNavigator(this);

        Bundle args = getArguments();
        if (args != null) {
            int linked = args.getInt(ARG_PROJECT_ID, 0);
            if (linked > 0) {
                viewModel.selectFromLink(linked);
                args.putInt(ARG_PROJECT_ID, 0); // consumed: a recreation must not select it again
            }
        }
        registerResults();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentProjectsBinding.inflate(inflater, container, false);

        selectorAdapter = new ProjectsSelectorAdapter(new ProjectsSelectorAdapter.Host() {
            @Override
            public void onProjectSelected(int projectId) {
                viewModel.selectProject(projectId);
            }

            @Override
            public void onNewProject() {
                navigator.openProjectForm(0);
            }
        });
        binding.projectsSelector.setAdapter(selectorAdapter);

        listAdapter = new ProjectsListAdapter(new ListHost());
        binding.projectsList.setAdapter(listAdapter);

        setupToolbar();
        binding.projectsFab.setOnClickListener(v -> {
            if (current == null || current.noProjects()) return;
            navigator.openQuickAdd(current.selectedProjectId, firstSectionId());
        });
        binding.projectsCreateFirst.setOnClickListener(v -> navigator.openProjectForm(0));
        setupNoteBar();

        viewModel.getModel().observe(getViewLifecycleOwner(), this::render);
        ScreenTransitions.postponeUntilReady(this);

        FocusSessionViewModel session = new ViewModelProvider(requireActivity()).get(FocusSessionViewModel.class);
        session.getUiState().observe(getViewLifecycleOwner(), ui -> {
            int entryId = ui != null && ui.isRunning() && FocusTarget.isSet() ? FocusTarget.getEntryId() : 0;
            listAdapter.setRunning(entryId, ui == null ? 0f : ui.getProgress());
        });
        // Meta cumplida de una tarea de un proyecto: el sitio unico que la avisa (el presentador de
        // Home/Focus la deja pendiente), ahora o la proxima vez que se abre Projects.
        session.getAutoCompletedTaskTitle().observe(getViewLifecycleOwner(), this::onTimeReached);
        return binding.getRoot();
    }

    /** The goal of a project task was reached and the engine marked it done: "Time reached" + note + Undo. */
    private void onTimeReached(@Nullable String title) {
        if (title == null || binding == null || !isAdded() || isStateSaved()) return;
        if (!FocusTarget.isSet() || FocusTarget.getProjectId() <= 0) return;
        int entryId = FocusTarget.getEntryId();
        FocusSessionViewModel session = new ViewModelProvider(requireActivity()).get(FocusSessionViewModel.class);
        HapticFeedback.confirm(binding.getRoot());
        SoundFeedback.get(requireContext()).playSuccess();
        session.onAutoCompletedTaskShown(title);
        showCompleted(entryId, R.string.projects_focus_time_reached);
    }

    // ---------------------------------------------------------------- toolbar and menu

    private void setupToolbar() {
        tooltip(binding.projectsAgendaButton);
        tooltip(binding.projectsMenuButton);
        binding.projectsAgendaButton.setOnClickListener(v -> {
            if (current != null && !current.noProjects()) navigator.openAgenda(current.selectedProjectId);
        });
        binding.projectsSortButton.setOnClickListener(v -> viewModel.cycleSort());
        binding.projectsMenuButton.setOnClickListener(this::showMenu);
    }

    private static void tooltip(ImageButton button) {
        TooltipCompat.setTooltipText(button, button.getContentDescription());
    }

    private void showMenu(View anchor) {
        if (current == null || current.noProjects()) return;
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        boolean hideDone = viewModel.isHideDone(current.selectedProjectId);
        menu.getMenu().add(Menu.NONE, MENU_EDIT, 1, R.string.projects_main_menu_edit);
        menu.getMenu().add(Menu.NONE, MENU_HIDE_DONE, 2, hideDone
                ? R.string.projects_main_menu_show_done : R.string.projects_main_menu_hide_done);
        menu.getMenu().add(Menu.NONE, MENU_ADD_SECTION, 3, R.string.projects_main_menu_add_section);
        menu.getMenu().add(Menu.NONE, MENU_FILTER_AREA, 4, R.string.projects_main_menu_filter_area);
        menu.getMenu().add(Menu.NONE, MENU_ARCHIVE, 5, R.string.projects_main_menu_archive);
        menu.getMenu().add(Menu.NONE, MENU_COMPLETE, 6, R.string.projects_main_menu_complete);
        menu.setOnMenuItemClickListener(this::onMenuItem);
        menu.show();
    }

    private boolean onMenuItem(MenuItem item) {
        if (current == null || current.noProjects()) return true;
        int projectId = current.selectedProjectId;
        switch (item.getItemId()) {
            case MENU_EDIT:
                navigator.openProjectForm(projectId);
                break;
            case MENU_HIDE_DONE:
                viewModel.toggleHideDone(projectId);
                break;
            case MENU_ADD_SECTION:
                askAddSection();
                break;
            case MENU_FILTER_AREA:
                showAreaFilter();
                break;
            case MENU_ARCHIVE:
                viewModel.archiveProject(projectId);
                showUndo(R.string.projects_main_project_archived,
                        () -> viewModel.unarchiveProject(projectId), null);
                break;
            case MENU_COMPLETE:
                completeProject(projectId);
                break;
            default:
                break;
        }
        return true;
    }

    private void completeProject(int projectId) {
        viewModel.completeProject(projectId);
        showUndo(R.string.projects_main_project_completed, () -> viewModel.reopenProject(projectId), null);
        // u13 (d3): short celebration, once per project id.
        if (CelebrationPrefs.claimProject(requireContext(), projectId)) {
            CelebrationSheet.show(getChildFragmentManager(),
                    getString(R.string.celebration_project_title),
                    getString(R.string.celebration_project_message));
        }
    }

    /** D-P14: the single-choice dialog of the area filter (one menu entry plus one ViewModel filter). */
    private void showAreaFilter() {
        List<Tag> areas = viewModel.getAreas().getValue();
        final List<Tag> list = areas == null ? new ArrayList<Tag>() : areas;
        final List<String> names = new ArrayList<>();
        names.add(getString(R.string.projects_main_area_all));
        int checked = 0;
        for (int i = 0; i < list.size(); i++) {
            names.add(list.get(i).name);
            if (list.get(i).id == viewModel.getAreaFilter()) checked = i + 1;
        }
        final Context context = requireContext();
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(context,
                android.R.layout.simple_list_item_single_choice, names) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                CheckedTextView row = (CheckedTextView) super.getView(position, convertView, parent);
                if (position == 0) {
                    row.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
                } else {
                    row.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            ProjectAccents.projectIconRes(list.get(position - 1).iconKey), 0, 0, 0);
                    row.setCompoundDrawableTintList(ColorStateList.valueOf(
                            ContextCompat.getColor(context, R.color.bb_on_surface_muted)));
                    row.setCompoundDrawablePadding(
                            context.getResources().getDimensionPixelSize(R.dimen.spacing_md));
                }
                return row;
            }
        };
        new AlertDialog.Builder(context, R.style.RoundedDialog)
                .setTitle(R.string.projects_main_area_title)
                .setSingleChoiceItems(adapter, checked, (dialog, which) -> {
                    viewModel.setAreaFilter(which == 0 ? 0 : list.get(which - 1).id);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.projects_main_action_cancel, null)
                .show();
    }

    // ---------------------------------------------------------------- render

    private int firstSectionId() {
        return current == null || current.sections.isEmpty() ? 0 : current.sections.get(0).id;
    }

    private void render(ProjectsScreenModel model) {
        if (binding == null || model == null) return;
        current = model;
        boolean none = model.noProjects();
        binding.projectsFirstState.setVisibility(none ? View.VISIBLE : View.GONE);
        binding.projectsList.setVisibility(none ? View.GONE : View.VISIBLE);
        binding.projectsSelector.setVisibility(none ? View.GONE : View.VISIBLE);
        binding.projectsFab.setVisibility(none ? View.GONE : View.VISIBLE);
        int tools = none ? View.GONE : View.VISIBLE;
        binding.projectsAgendaButton.setVisibility(tools);
        binding.projectsSortButton.setVisibility(tools);
        binding.projectsMenuButton.setVisibility(tools);

        String sortName = ProjectsListAdapter.sortLabel(requireContext(), model.sort);
        String sortDesc = getString(R.string.projects_main_sort_desc, sortName);
        binding.projectsSortButton.setContentDescription(sortDesc);
        TooltipCompat.setTooltipText(binding.projectsSortButton, sortDesc);

        selectorAdapter.submit(model.bubbles);
        if (model.selectedProjectId != lastSelectedProject) {
            lastSelectedProject = model.selectedProjectId;
            for (int i = 0; i < model.bubbles.size(); i++) {
                if (model.bubbles.get(i).selected) binding.projectsSelector.scrollToPosition(i);
            }
        }
        listAdapter.submit(model);
        ScreenTransitions.startWhenDrawn(this, binding.projectsList);
    }

    // ---------------------------------------------------------------- list taps

    private final class ListHost implements ProjectsListAdapter.Host {
        @Override
        public void onSummaryFocus(@NonNull TaskSnapshot task) {
            navigator.startFocus(task);
        }

        @Override
        public void onSectionToggle(int sectionId) {
            viewModel.toggleCollapsed(sectionId);
        }

        @Override
        public void onSectionAdd(int sectionId) {
            if (current != null) navigator.openQuickAdd(current.selectedProjectId, sectionId);
        }

        @Override
        public void onSectionMenu(@NonNull ProjectsScreenModel.Section section, @NonNull View anchor) {
            PopupMenu menu = new PopupMenu(requireContext(), anchor);
            menu.getMenu().add(Menu.NONE, 1, 1, R.string.projects_main_section_rename);
            menu.getMenu().add(Menu.NONE, 2, 2, R.string.projects_main_section_delete);
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) {
                    askRenameSection(section);
                } else {
                    askDeleteSection(section);
                }
                return true;
            });
            menu.show();
        }

        @Override
        public void onAddSection() {
            askAddSection();
        }

        @Override
        public void onAddFirstTask() {
            if (current != null) navigator.openQuickAdd(current.selectedProjectId, firstSectionId());
        }

        @Override
        public void onTaskChecked(@NonNull TaskSnapshot task, boolean checked) {
            viewModel.setTaskDone((int) task.id, checked);
            if (checked) {
                showCompleted((int) task.id);
            } else {
                dismissCompletedNow();
            }
        }

        @Override
        public void onTaskOpen(@NonNull TaskSnapshot task) {
            if (current != null) navigator.openTaskEditor(current.selectedProjectId, (int) task.id);
        }

        @Override
        public void onTaskFocus(@NonNull TaskSnapshot task) {
            navigator.startFocus(task);
        }

        @Override
        public void onSubtaskToggle(@NonNull TaskSubtask subtask) {
            viewModel.toggleSubtask(subtask.id);
        }
    }

    // ---------------------------------------------------------------- section dialogs

    private void askAddSection() {
        askName(R.string.projects_main_section_new_title, "", name -> {
            if (current != null && !current.noProjects()) {
                viewModel.addSection(current.selectedProjectId, name);
            }
        });
    }

    private void askRenameSection(ProjectsScreenModel.Section section) {
        askName(R.string.projects_main_section_rename_title, section.name,
                name -> viewModel.renameSection(section.id, name));
    }

    private void askDeleteSection(ProjectsScreenModel.Section section) {
        if (current != null && current.sections.size() <= 1) {
            Snackbar.make(binding.projectsRoot, R.string.projects_main_section_last, Snackbar.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(requireContext(), R.style.RoundedDialog)
                .setTitle(R.string.projects_main_section_delete_title)
                .setMessage(R.string.projects_main_section_delete_message)
                .setPositiveButton(R.string.projects_main_action_delete, (d, w) ->
                        viewModel.deleteSection(section.id, deleted -> {
                            if (!deleted && binding != null) {
                                Snackbar.make(binding.projectsRoot, R.string.projects_main_section_last,
                                        Snackbar.LENGTH_SHORT).show();
                            }
                        }))
                .setNegativeButton(R.string.projects_main_action_cancel, null)
                .show();
    }

    private interface NameListener {
        void onName(String name);
    }

    private void askName(int title, String initial, NameListener listener) {
        Context context = requireContext();
        EditText input = new EditText(context);
        input.setHint(R.string.projects_main_section_name_hint);
        input.setSingleLine(true);
        input.setText(initial);
        input.setSelection(initial.length());
        FrameLayout holder = new FrameLayout(context);
        int pad = context.getResources().getDimensionPixelSize(R.dimen.spacing_lg);
        holder.setPadding(pad, 0, pad, 0);
        holder.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(context, R.style.RoundedDialog)
                .setTitle(title)
                .setView(holder)
                .setPositiveButton(R.string.projects_main_action_save, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) listener.onName(name);
                })
                .setNegativeButton(R.string.projects_main_action_cancel, null)
                .create();
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
        dialog.show();
    }

    // ---------------------------------------------------------------- snackbars

    /** A snackbar with Undo; {@code onExpire} runs when it goes away without Undo (timeout, swipe, replaced). */
    private void showUndo(int text, Runnable onUndo, @Nullable Runnable onExpire) {
        if (binding == null) return;
        Snackbar snack = Snackbar.make(binding.projectsRoot, text, Snackbar.LENGTH_LONG);
        snack.setAction(R.string.projects_main_undo, v -> onUndo.run());
        if (onExpire != null) {
            snack.addCallback(new BaseTransientBottomBar.BaseCallback<Snackbar>() {
                @Override
                public void onDismissed(Snackbar transientBottomBar, int event) {
                    if (event != DISMISS_EVENT_ACTION) onExpire.run();
                }
            });
        }
        snack.show();
    }

    private void showMessage(String text) {
        if (binding == null) return;
        Snackbar.make(binding.projectsRoot, text, Snackbar.LENGTH_SHORT).show();
    }

    /** "Completed" with the note icon button and Undo; stays 7 s (longer while the note is being written). */
    private void showCompleted(int entryId) {
        showCompleted(entryId, R.string.projects_main_completed);
    }

    private void showCompleted(int entryId, int text) {
        if (binding == null) return;
        dismissCompletedNow();
        completedEntryId = entryId;
        Snackbar snack = Snackbar.make(binding.projectsRoot, text,
                Snackbar.LENGTH_INDEFINITE);
        snack.setAction(R.string.projects_main_undo, v -> viewModel.setTaskDone(entryId, false));
        ViewGroup content = (ViewGroup) ((ViewGroup) snack.getView()).getChildAt(0);
        ImageButton note = new ImageButton(requireContext());
        android.util.TypedValue ripple = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        note.setBackgroundResource(ripple.resourceId);
        note.setImageResource(R.drawable.ic_note_24);
        note.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.bb_primary_dark)));
        note.setContentDescription(getString(R.string.projects_main_completed_note_desc));
        TooltipCompat.setTooltipText(note, note.getContentDescription());
        int size = getResources().getDimensionPixelSize(R.dimen.touch_target_min);
        note.setLayoutParams(new android.widget.LinearLayout.LayoutParams(size, size));
        note.setOnClickListener(v -> openNoteBar());
        content.addView(note, Math.max(0, content.getChildCount() - 1));
        snack.addCallback(new BaseTransientBottomBar.BaseCallback<Snackbar>() {
            @Override
            public void onDismissed(Snackbar transientBottomBar, int event) {
                if (completedSnack == transientBottomBar) {
                    completedSnack = null;
                    handler.removeCallbacks(dismissCompleted);
                    closeNoteBar();
                }
            }
        });
        completedSnack = snack;
        snack.show();
        handler.postDelayed(dismissCompleted, COMPLETED_SNACK_MS);
    }

    private void dismissCompletedNow() {
        handler.removeCallbacks(dismissCompleted);
        if (completedSnack != null) completedSnack.dismiss();
    }

    // ---------------------------------------------------------------- completion note

    private void setupNoteBar() {
        binding.projectsNoteDone.setOnClickListener(v -> saveNote());
        binding.projectsNoteInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveNote();
                return true;
            }
            return false;
        });
    }

    private void openNoteBar() {
        if (binding == null) return;
        handler.removeCallbacks(dismissCompleted); // the snackbar waits while the note is written
        binding.projectsNoteInput.setText("");
        binding.projectsNoteBar.setVisibility(View.VISIBLE);
        binding.projectsNoteInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(binding.projectsNoteInput, InputMethodManager.SHOW_IMPLICIT);
        watchKeyboard();
    }

    private void saveNote() {
        if (binding == null) return;
        String text = binding.projectsNoteInput.getText().toString();
        if (!text.trim().isEmpty()) viewModel.setCompletionNote(completedEntryId, text);
        dismissCompletedNow();
        closeNoteBar();
    }

    private void closeNoteBar() {
        if (binding == null) return;
        if (binding.projectsNoteBar.getVisibility() == View.VISIBLE) {
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(binding.projectsNoteInput.getWindowToken(), 0);
        }
        binding.projectsNoteBar.setVisibility(View.GONE);
        binding.projectsNoteBar.setTranslationY(0f);
        stopWatchingKeyboard();
    }

    /** Lifts the note field above the keyboard (the window does not resize under edge-to-edge). */
    private void watchKeyboard() {
        stopWatchingKeyboard();
        final View root = binding.getRoot();
        keyboardListener = () -> {
            if (binding == null) return;
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
            int ime = insets == null ? 0 : insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            Rect rect = new Rect();
            root.getGlobalVisibleRect(rect);
            int belowRoot = Math.max(0, root.getRootView().getHeight() - rect.bottom);
            binding.projectsNoteBar.setTranslationY(-Math.max(0, ime - belowRoot));
        };
        root.getViewTreeObserver().addOnGlobalLayoutListener(keyboardListener);
    }

    private void stopWatchingKeyboard() {
        if (keyboardListener != null && binding != null) {
            binding.getRoot().getViewTreeObserver().removeOnGlobalLayoutListener(keyboardListener);
        }
        keyboardListener = null;
    }

    // ---------------------------------------------------------------- results from the sub-screens

    private void registerResults() {
        getParentFragmentManager().setFragmentResultListener(ProjectsResults.TASK_SAVED, this,
                (key, bundle) -> {
                    int projectId = ProjectsResults.projectId(bundle);
                    if (projectId > 0 && current != null && projectId != current.selectedProjectId) {
                        viewModel.selectProject(projectId);
                    }
                    String section = ProjectsResults.sectionName(bundle);
                    if (ProjectsResults.isNew(bundle) && section != null) {
                        showMessage(getString(R.string.projects_main_added_to, section));
                    } else {
                        showMessage(getString(R.string.projects_main_changes_saved));
                    }
                });
        getParentFragmentManager().setFragmentResultListener(ProjectsResults.TASK_DELETE_REQUESTED, this,
                (key, bundle) -> {
                    int entryId = ProjectsResults.entryId(bundle);
                    viewModel.hideTask(entryId);
                    showUndo(R.string.projects_main_task_deleted, () -> viewModel.restoreTask(entryId),
                            () -> viewModel.commitTaskDelete(entryId));
                });
        getParentFragmentManager().setFragmentResultListener(ProjectsResults.PROJECT_SAVED, this,
                (key, bundle) -> {
                    int projectId = ProjectsResults.projectId(bundle);
                    if (ProjectsResults.isNew(bundle)) {
                        if (projectId > 0) viewModel.selectProject(projectId);
                        showMessage(getString(R.string.projects_main_project_created));
                    } else {
                        showMessage(getString(R.string.projects_main_changes_saved));
                    }
                });
        getParentFragmentManager().setFragmentResultListener(ProjectsResults.PROJECT_ARCHIVE_REQUESTED, this,
                (key, bundle) -> {
                    int projectId = ProjectsResults.projectId(bundle);
                    viewModel.archiveProject(projectId);
                    showUndo(R.string.projects_main_project_archived,
                            () -> viewModel.unarchiveProject(projectId), null);
                });
        getParentFragmentManager().setFragmentResultListener(ProjectsResults.PROJECT_DELETE_REQUESTED, this,
                (key, bundle) -> {
                    int projectId = ProjectsResults.projectId(bundle);
                    viewModel.hideProject(projectId);
                    showUndo(R.string.projects_main_project_deleted, () -> viewModel.restoreProject(projectId),
                            () -> viewModel.commitProjectDelete(projectId));
                });
        // Tags and areas reach the screen through their own LiveData; nothing else to do here.
        getParentFragmentManager().setFragmentResultListener(ProjectsResults.TAG_SAVED, this,
                (key, bundle) -> { });
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(dismissCompleted);
        stopWatchingKeyboard();
        // The screen is left: whatever still waits for its Undo window is deleted for real (D-P17).
        viewModel.flushPendingDeletes();
        completedSnack = null;
        binding = null;
    }
}
