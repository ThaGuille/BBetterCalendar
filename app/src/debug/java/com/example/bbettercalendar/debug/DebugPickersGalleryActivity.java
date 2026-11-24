package com.example.bbettercalendar.debug;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.projects.domain.DateChoices;
import com.example.bbettercalendar.projects.domain.SectionRef;
import com.example.bbettercalendar.projects.domain.TagRef;
import com.example.bbettercalendar.projects.domain.TaskDraft;
import com.example.bbettercalendar.ui.common.InsetsHelper;
import com.example.bbettercalendar.ui.projects.picker.PickerHost;
import com.example.bbettercalendar.ui.projects.picker.PickerView;
import com.example.bbettercalendar.ui.projects.picker.RepeatAvailability;
import com.google.android.material.chip.ChipGroup;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Debug-only gallery of the task pickers (rediseno-proyectos u6): every picker over one in-memory
 * {@link TaskDraft} with two sample tags and two sample sections, shown one at a time through tabs, and a
 * live summary {@code galleryDraftSummary}. Turns {@link RepeatAvailability} on for itself so the Repeat
 * row can be exercised. Open with
 * <pre>
 * adb shell am start -n io.github.thaguille.bbettercalendar/com.example.bbettercalendar.debug.DebugPickersGalleryActivity
 * </pre>
 */
public class DebugPickersGalleryActivity extends AppCompatActivity implements PickerHost {

    private final List<TagRef> tags = Arrays.asList(
            new TagRef(11, "writing", "pen", "primary", 0),
            new TagRef(12, "reading", "book", "secondary", 0));
    private final List<SectionRef> sections = Arrays.asList(
            new SectionRef(21, "Drafting", 0),
            new SectionRef(22, "Review", 1));

    private boolean previousRepeat;
    private TaskDraft draft;
    private long todayStart;
    private TextView summary;
    private PickerView[] pickers;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetsHelper.enableEdgeToEdge(this);
        setContentView(R.layout.activity_debug_pickers_gallery);
        InsetsHelper.applyToContent(this);
        previousRepeat = RepeatAvailability.isEnabled();
        RepeatAvailability.setEnabled(true);

        todayStart = DateChoices.startOfToday(System.currentTimeMillis(), ZoneId.systemDefault());
        draft = new TaskDraft(todayStart);
        draft.sectionId = 21;
        summary = findViewById(R.id.galleryDraftSummary);

        pickers = new PickerView[] {
                findViewById(R.id.galleryPickerDate), findViewById(R.id.galleryPickerDuration),
                findViewById(R.id.galleryPickerPriority), findViewById(R.id.galleryPickerTag),
                findViewById(R.id.galleryPickerSubtasks), findViewById(R.id.galleryPickerMore),
                findViewById(R.id.galleryPickerSection)};
        for (PickerView p : pickers) {
            p.setHeaderVisible(true);
            p.bind(draft, this);
        }
        int[] tabs = {R.id.galleryTabDate, R.id.galleryTabDuration, R.id.galleryTabPriority, R.id.galleryTabTag,
                R.id.galleryTabSubtasks, R.id.galleryTabMore, R.id.galleryTabSection};
        ChipGroup group = findViewById(R.id.galleryPickerTabs);
        group.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            // The quick add hides the keyboard when a picker takes its place; the gallery does the same.
            InputMethodManager imm = getSystemService(InputMethodManager.class);
            if (imm != null) imm.hideSoftInputFromWindow(group.getWindowToken(), 0);
            for (int i = 0; i < tabs.length; i++) {
                pickers[i].setVisibility(tabs[i] == ids.get(0) ? View.VISIBLE : View.GONE);
            }
        });
        updateSummary();
    }

    @Override
    protected void onDestroy() {
        RepeatAvailability.setEnabled(previousRepeat);
        super.onDestroy();
    }

    private String dayToken() {
        Long day = draft.getDayStartMillis();
        if (day == null) return "none";
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        if (day == DateChoices.startOfToday(now, zone)) return "today";
        if (day == DateChoices.tomorrow(now, zone)) return "tomorrow";
        if (day == DateChoices.nextSaturday(now, zone)) return "sat";
        return "date";
    }

    private void updateSummary() {
        Integer time = draft.getTimeOfDayMinutes();
        String timeText = time == null ? "none" : String.format(Locale.US, "%02d:%02d", time / 60, time % 60);
        summary.setText("day=" + dayToken()
                + " time=" + timeText
                + " dur=" + draft.getDurationMinutes()
                + " pomo=" + draft.isPomodoro()
                + " block=" + draft.isBlockOnFocus()
                + " pri=" + draft.priority
                + " tag=" + draft.tagId
                + " subs=" + draft.subtasks.size()
                + " rep=" + draft.getRepeat().name()
                + " rem=" + draft.getReminder().name()
                + " note=" + (draft.note == null || draft.note.trim().isEmpty() ? "no" : "yes"));
    }

    // ---- PickerHost ----

    @NonNull
    @Override
    public FragmentManager getPickerFragmentManager() {
        return getSupportFragmentManager();
    }

    @Override
    public void onDraftChanged() {
        updateSummary();
        // The other pickers show the same draft: a rule may have changed their fields.
        for (PickerView p : pickers) {
            if (p.getVisibility() != View.VISIBLE) p.refresh();
        }
    }

    @Override
    public void onDone() {
        Toast.makeText(this, "Done", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onNewTag() {
        Toast.makeText(this, "New tag", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onEditTag(long tagId) {
        Toast.makeText(this, "Edit tag " + tagId, Toast.LENGTH_SHORT).show();
    }

    @NonNull
    @Override
    public List<TagRef> getTags() {
        return tags;
    }

    @NonNull
    @Override
    public List<SectionRef> getSections() {
        return sections;
    }
}
