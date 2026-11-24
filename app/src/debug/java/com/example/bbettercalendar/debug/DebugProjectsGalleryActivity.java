package com.example.bbettercalendar.debug;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.InsetsHelper;
import com.example.bbettercalendar.ui.common.widget.FocusTimerButton;
import com.example.bbettercalendar.ui.common.widget.ProjectAccents;
import com.example.bbettercalendar.ui.common.widget.ProjectBubbleView;
import com.example.bbettercalendar.ui.common.widget.TaskCheckView;

/**
 * Debug-only gallery of the widgets of the Projects redesign (rediseno-proyectos u3): every state of
 * {@link TaskCheckView}, {@link FocusTimerButton} and {@link ProjectBubbleView}, plus the lit card in the
 * four project colours. Demo data only. Open with
 * <pre>
 * adb shell am start -n io.github.thaguille.bbettercalendar/com.example.bbettercalendar.debug.DebugProjectsGalleryActivity
 * </pre>
 */
public class DebugProjectsGalleryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetsHelper.enableEdgeToEdge(this);
        setContentView(R.layout.activity_debug_projects_gallery);
        InsetsHelper.applyToContent(this);
        bindChecks();
        bindTimers();
        bindBubbles();
    }

    private void bindChecks() {
        TextView state = findViewById(R.id.galleryTaskCheckState);
        TaskCheckView check = findViewById(R.id.galleryTaskCheck);
        check.setTaskTitle("Escribir el capítulo 3");
        // "open" instead of "unchecked": a substring assert for "checked" must not match the initial state.
        check.setOnCheckedChangeListener((v, checked) -> state.setText(checked ? "state: checked" : "state: open"));

        TaskCheckView small = findViewById(R.id.galleryTaskCheckSmall);
        small.setTaskTitle("Subtarea");

        TaskCheckView high = findViewById(R.id.galleryCheckHigh);
        high.setPriority(3);
        TaskCheckView medium = findViewById(R.id.galleryCheckMedium);
        medium.setPriority(2);
        TaskCheckView low = findViewById(R.id.galleryCheckLow);
        low.setPriority(1);
        TaskCheckView done = findViewById(R.id.galleryCheckDone);
        done.setChecked(true);
        TaskCheckView doneSmall = findViewById(R.id.galleryCheckDoneSmall);
        doneSmall.setChecked(true);
    }

    private void bindTimers() {
        FocusTimerButton idle = findViewById(R.id.galleryTimerIdle);
        idle.setGoal(30, 120);
        FocusTimerButton running = findViewById(R.id.galleryTimerRunning);
        running.setGoal(75, 120);
        running.setRunning(true);
        FocusTimerButton big = findViewById(R.id.galleryTimerBig);
        big.setGoal(0, 25);
        FocusTimerButton bigRunning = findViewById(R.id.galleryTimerBigRunning);
        bigRunning.setProgress(0.4f);
        bigRunning.setRunning(true);
    }

    private void bindBubbles() {
        bubble(R.id.galleryBubble, 0, "cap", 0.6f, 3, false, false);
        bubble(R.id.galleryBubbleSelected, 1, "book", 0.35f, 2, false, true);
        bubble(R.id.galleryBubbleLate, 2, "code", 0.8f, 12, true, false);
        bubble(R.id.galleryBubbleQuiet, 3, "plane", 0.1f, 0, false, false);
        ProjectBubbleView add = findViewById(R.id.galleryBubbleNew);
        add.setNewProject(true);
    }

    private void bubble(int id, int colorIndex, String iconKey, float progress, int count, boolean late,
                        boolean selected) {
        ProjectBubbleView view = findViewById(id);
        view.setAccentColor(ContextCompat.getColor(this, ProjectAccents.colorResFor(colorIndex)));
        view.setIconRes(ProjectAccents.projectIconRes(iconKey));
        view.setProgress(progress);
        view.setBadge(count, late);
        view.setSelected(selected);
        view.setProjectName(iconKey);
    }
}
