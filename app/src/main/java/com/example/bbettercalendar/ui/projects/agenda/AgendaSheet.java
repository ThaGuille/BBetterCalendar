package com.example.bbettercalendar.ui.projects.agenda;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.projects.ProjectTasksRepository;
import com.example.bbettercalendar.projects.ProjectsRepository;
import com.example.bbettercalendar.ui.common.InsetsHelper;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Agenda of a project (rediseno-proyectos spec 3.2): short bottom sheet with the pending dated tasks by
 * day, the deadline and the undated count. Data is read once off the main thread; the grouping is
 * {@link AgendaGrouping}. It never opens another sheet.
 */
@AndroidEntryPoint
public class AgendaSheet extends BottomSheetDialogFragment {

    public static final String TAG = "project_agenda_sheet";
    private static final String ARG_PROJECT_ID = "projectId";

    @Inject ProjectTasksRepository tasksRepository;
    @Inject ProjectsRepository projectsRepository;

    private LinearLayout list;

    @NonNull
    public static AgendaSheet newInstance(int projectId) {
        AgendaSheet sheet = new AgendaSheet();
        Bundle args = new Bundle();
        args.putInt(ARG_PROJECT_ID, projectId);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_project_agenda, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsHelper.apply(view, InsetsHelper.BOTTOM);
        list = view.findViewById(R.id.agendaList);
        View close = view.findViewById(R.id.agendaClose);
        TooltipCompat.setTooltipText(close, getString(R.string.projects_agenda_close));
        close.setOnClickListener(v -> dismiss());

        int projectId = requireArguments().getInt(ARG_PROJECT_ID);
        tasksRepository.loadAgenda(projectId, tasks -> {
            if (!isAdded() || list == null) return;
            projectsRepository.loadDeadline(projectId, deadline -> {
                if (!isAdded() || list == null) return;
                render(AgendaGrouping.build(tasks, deadline, System.currentTimeMillis(),
                        ZoneId.systemDefault(), Locale.getDefault()));
            });
        });
    }

    @Override
    public void onDestroyView() {
        list = null;
        super.onDestroyView();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetBehavior<FrameLayout> behavior = ((BottomSheetDialog) getDialog()).getBehavior();
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    private void render(List<AgendaRow> rows) {
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        if (rows.isEmpty()) {
            TextView empty = (TextView) inflater.inflate(R.layout.item_project_agenda_header, list, false);
            empty.setText(R.string.projects_agenda_empty);
            TextViewCompat.setTextAppearance(empty, R.style.TextAppearance_BBetter_BodyMuted);
            list.addView(empty);
            return;
        }
        for (AgendaRow row : rows) {
            switch (row.kind) {
                case HEADER:
                    View header = headerView(inflater, headerText(row), row.tone);
                    if (row.day == AgendaRow.Day.YESTERDAY) header.setId(R.id.agendaHeaderYesterday);
                    else if (row.day == AgendaRow.Day.TODAY) header.setId(R.id.agendaHeaderToday);
                    list.addView(header);
                    break;
                case UNDATED:
                    list.addView(headerView(inflater,
                            getString(R.string.projects_agenda_undated, row.count), AgendaRow.Tone.NORMAL));
                    break;
                case DEADLINE: {
                    list.addView(headerView(inflater, row.label, AgendaRow.Tone.NORMAL));
                    View v = inflater.inflate(R.layout.item_project_agenda_deadline, list, false);
                    ((TextView) v.findViewById(R.id.agendaDeadlineText))
                            .setText(R.string.projects_agenda_deadline);
                    list.addView(v);
                    break;
                }
                default: {
                    View v = inflater.inflate(R.layout.item_project_agenda_task, list, false);
                    ((TextView) v.findViewById(R.id.agendaRowTime)).setText(row.time);
                    ((TextView) v.findViewById(R.id.agendaRowTitle)).setText(row.title);
                    ((TextView) v.findViewById(R.id.agendaRowDuration)).setText(row.duration);
                    v.findViewById(R.id.agendaRowLock)
                            .setVisibility(row.blocks ? View.VISIBLE : View.GONE);
                    list.addView(v);
                }
            }
        }
    }

    private String headerText(AgendaRow row) {
        switch (row.day) {
            case YESTERDAY: return getString(R.string.projects_agenda_yesterday);
            case TODAY: return getString(R.string.projects_agenda_today);
            case TOMORROW: return getString(R.string.projects_agenda_tomorrow);
            default: return row.label;
        }
    }

    private View headerView(LayoutInflater inflater, String text, AgendaRow.Tone tone) {
        TextView header = (TextView) inflater.inflate(R.layout.item_project_agenda_header, list, false);
        header.setText(text);
        int color = tone == AgendaRow.Tone.OVERDUE ? R.color.bb_danger
                : tone == AgendaRow.Tone.TODAY ? R.color.bb_primary_bright : R.color.bb_on_surface_muted;
        header.setTextColor(ContextCompat.getColor(requireContext(), color));
        return header;
    }
}
