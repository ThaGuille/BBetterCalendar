package com.example.bbettercalendar.ui.projects;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.calendarEntries.CalendarEntry;
import com.example.bbettercalendar.databinding.FragmentProjectDetailBinding;
import com.example.bbettercalendar.projects.Project;
import com.example.bbettercalendar.projects.ProjectDeadlineState;
import com.example.bbettercalendar.ui.common.widget.StatTileView;
import com.example.bbettercalendar.ui.home.FocusTarget;
import com.example.bbettercalendar.ui.home.QuickAddTaskSheet;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

// Detalle de proyecto (mejora-visual u11, mockup 31): anillo + nombre editable + chip de vencimiento,
// celdas Enfocado / Tareas, lista de tareas y alta rapida fija abajo. Barra propia con atras y menu
// (completar, eliminar). Nombre y notas se guardan solos en onPause (sin boton Save).
@AndroidEntryPoint
public class ProjectDetailFragment extends Fragment {

    private static final String ARG_PROJECT_ID = "projectId";
    private static final int MENU_COMPLETE = 1;
    private static final int MENU_DELETE = 2;

    private FragmentProjectDetailBinding binding;
    private ProjectDetailViewModel viewModel;
    private ProjectItemAdapter itemAdapter;
    private boolean headerLoaded = false;
    // Para el chip de deadline: un proyecto terminado (completado o todos sus items hechos) no
    // está "vencido". Proyecto e items llegan por observers distintos, así que se recuerdan aquí.
    @Nullable private Project currentProject;
    private int doneItems;
    private int totalItems;
    private int remainingMinutes;
    private int focusedTotal;
    private int focusedWeek;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(ProjectDetailViewModel.class);
        binding = FragmentProjectDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        int projectId = requireArguments().getInt(ARG_PROJECT_ID, 0);
        viewModel.setProjectId(projectId);

        itemAdapter = new ProjectItemAdapter((entry, done) -> viewModel.setItemDone(entry, done));
        // "Focus this" (spec focus-attribution): el timer vive sólo en Home, así que vinculamos el
        // item y navegamos a Home, que consume el arranque pendiente (FocusTarget.pendingAutoStart).
        itemAdapter.setOnItemFocusListener(entry -> {
            FocusTarget.set(entry.getId(), entry.getTitle(), entry.getTargetMinutes());
            NavHostFragment.findNavController(this).navigate(R.id.navigation_home);
        });
        binding.projectDetailItemsList.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.projectDetailItemsList.setAdapter(itemAdapter);

        // El nombre puede ocupar dos lineas (cabecera del mockup) pero "Hecho" cierra el teclado.
        binding.projectDetailNameInput.setRawInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        binding.projectDetailNameInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        binding.projectDetailNameInput.setOnEditorActionListener((v, actionId, event) -> {
            v.clearFocus();
            return false;
        });

        binding.projectDetailDeadlineChip.setOnClickListener(v -> showDeadlinePicker());
        binding.projectDetailMenuButton.setOnClickListener(this::showMenu);

        // Alta rápida: "+" o "Hecho" añaden la tarea; pulsación larga en "+" abre la hoja completa.
        binding.projectDetailAddItemButton.setOnClickListener(v -> submitQuickAdd());
        binding.projectDetailAddItemButton.setOnLongClickListener(v -> {
            QuickAddTaskSheet.forProject(projectId)
                    .show(getChildFragmentManager(), QuickAddTaskSheet.SHEET_TAG);
            return true;
        });
        binding.projectDetailQuickAddInput.setOnEditorActionListener((v, actionId, event) -> {
            submitQuickAdd();
            return true;
        });

        viewModel.getProject().observe(getViewLifecycleOwner(), this::bindProject);
        // Sin ActionBar (spec frontend-structure-cleanup): flecha atrás propia en el contenido.
        binding.projectDetailBackButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());

        viewModel.getItems().observe(getViewLifecycleOwner(), items -> {
            itemAdapter.submitList(items);
            totalItems = items == null ? 0 : items.size();
            doneItems = 0;
            if (items != null) {
                for (CalendarEntry entry : items) {
                    if (entry.isDone()) doneItems++;
                }
            }
            List<CalendarEntry> list = items == null ? new ArrayList<>() : items;
            remainingMinutes = ProjectFormat.remainingMinutes(list);
            if (currentProject != null) {
                bindDeadlineViews(currentProject);
            }
            bindProgress();
            binding.projectDetailItemsEmptyText.setVisibility(
                    items == null || items.isEmpty() ? View.VISIBLE : View.GONE);
        });
        viewModel.getFocusedTotalMinutes().observe(getViewLifecycleOwner(), minutes -> {
            focusedTotal = minutes == null ? 0 : minutes;
            bindFocusTile();
        });
        viewModel.getFocusedWeekMinutes().observe(getViewLifecycleOwner(), minutes -> {
            focusedWeek = minutes == null ? 0 : minutes;
            bindFocusTile();
        });
        viewModel.getProjectDeleted().observe(getViewLifecycleOwner(), deleted -> {
            if (Boolean.TRUE.equals(deleted)) {
                NavHostFragment.findNavController(this).navigateUp();
            }
        });
    }

    private void bindProject(Project project) {
        if (project == null) {
            return;
        }
        if (!headerLoaded) {
            binding.projectDetailNameInput.setText(project.name);
            binding.projectDetailNotesInput.setText(project.notes);
            headerLoaded = true;
        }
        currentProject = project;
        binding.projectDetailRing.setRingColor(ContextCompat.getColor(requireContext(),
                ProjectListAdapter.accentColorResFor(project.colorIndex)));
        bindDeadlineViews(project);
    }

    // Anillo y celda "Tareas" salen de los items: marcar una subtarea los actualiza a la vez.
    private void bindProgress() {
        int percent = totalItems == 0 ? 0 : Math.round(100f * doneItems / totalItems);
        binding.projectDetailRing.setProgress(percent, true);
        StatTileView tile = binding.projectDetailTasksTile;
        tile.setValue(getString(R.string.project_detail_tasks_value, doneItems, totalItems));
        if (totalItems == 0) {
            tile.setSub(getString(R.string.project_detail_tasks_none));
        } else if (remainingMinutes > 0) {
            tile.setSub(getString(R.string.project_detail_remaining, ProjectFormat.duration(
                    requireContext(), ProjectFormat.roundedMinutes(remainingMinutes))));
        } else {
            tile.setSub(null);
        }
    }

    private void bindFocusTile() {
        if (binding == null) {
            return;
        }
        StatTileView tile = binding.projectDetailFocusTile;
        tile.setValue(ProjectFormat.duration(requireContext(), focusedTotal));
        tile.setSub(focusedTotal == 0
                ? getString(R.string.project_detail_focused_none)
                : getString(R.string.project_detail_focused_week,
                        ProjectFormat.duration(requireContext(), focusedWeek)));
    }

    private void submitQuickAdd() {
        if (binding == null) {
            return;
        }
        String title = binding.projectDetailQuickAddInput.getText().toString().trim();
        if (title.isEmpty()) {
            binding.projectDetailQuickAddInput.requestFocus();
            return;
        }
        viewModel.addItem(title, null, 0);
        binding.projectDetailQuickAddInput.setText("");
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        boolean completed = currentProject != null && currentProject.status == Project.STATUS_COMPLETED;
        if (!completed) {
            menu.getMenu().add(Menu.NONE, MENU_COMPLETE, 1, R.string.project_detail_complete);
        }
        menu.getMenu().add(Menu.NONE, MENU_DELETE, 2, R.string.project_detail_delete);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_COMPLETE) {
                viewModel.completeProject();
            } else {
                confirmDelete();
            }
            return true;
        });
        menu.show();
    }

    private void bindDeadlineViews(Project project) {
        long softDeadlineMillis = project.softDeadlineMillis;
        TextView chip = binding.projectDetailDeadlineChip;
        // El chip siempre está visible y abre el selector de fecha ("Añadir fecha límite" si no hay).
        if (softDeadlineMillis <= 0L) {
            chip.setText(R.string.project_due_add);
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.bb_on_surface_muted));
            chip.setBackgroundResource(R.drawable.bg_project_due_chip);
            return;
        }
        long now = System.currentTimeMillis();
        boolean finished = ProjectDeadlineState.isFinished(project.status, doneItems, totalItems);
        ProjectDeadlineState state = ProjectDeadlineState.from(softDeadlineMillis, now, finished);
        chip.setText(ProjectFormat.detailDeadline(requireContext(), softDeadlineMillis, now, state));
        int color = R.color.bb_on_surface_muted;
        int background = R.drawable.bg_project_due_chip;
        if (state == ProjectDeadlineState.PASSED) {
            color = R.color.bb_danger;
        } else if (state == ProjectDeadlineState.APPROACHING) {
            color = R.color.bb_accent_reward;
            background = R.drawable.bg_project_due_chip_reward;
        }
        chip.setTextColor(ContextCompat.getColor(requireContext(), color));
        chip.setBackgroundResource(background);
    }

    private void showDeadlinePicker() {
        Calendar now = Calendar.getInstance();
        new DatePickerDialog(requireContext(), R.style.Theme_BBetter_PickerDialog,
                (picker, year, month, dayOfMonth) -> {
            Calendar deadline = Calendar.getInstance();
            deadline.set(year, month, dayOfMonth, 23, 59, 59);
            viewModel.updateDeadline(deadline.getTimeInMillis());
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
    }

    // Ya no hay botón "Save": el header (nombre/notas) se persiste solo al abandonar la pantalla
    // (back, flecha Up, o app a background). Un nombre vacío no se guarda para no pisar el actual.
    private void persistHeaderIfValid() {
        if (binding == null) {
            return;
        }
        String name = binding.projectDetailNameInput.getText().toString().trim();
        if (name.isEmpty()) {
            return;
        }
        String notes = binding.projectDetailNotesInput.getText().toString().trim();
        viewModel.updateHeader(name, notes);
    }

    private void confirmDelete() {
        new AlertDialog.Builder(requireContext(), R.style.RoundedDialog)
                .setTitle(R.string.project_detail_delete_confirm_title)
                .setMessage(R.string.project_detail_delete_confirm_message)
                .setPositiveButton(R.string.project_detail_delete, (dialog, which) -> viewModel.deleteProject())
                .setNegativeButton(R.string.create_project_cancel, null)
                .show();
    }

    @Override
    public void onPause() {
        super.onPause();
        persistHeaderIfValid();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
