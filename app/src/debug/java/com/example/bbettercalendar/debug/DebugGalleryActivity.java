package com.example.bbettercalendar.debug;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.stats.StreakCalculator;
import com.example.bbettercalendar.ui.common.widget.AppUsageRowView;
import com.example.bbettercalendar.ui.common.widget.PeriodNavigatorView;
import com.example.bbettercalendar.ui.common.widget.SectionHeaderView;
import com.example.bbettercalendar.ui.common.widget.SegmentedControlView;
import com.example.bbettercalendar.ui.common.widget.StepperView;
import com.example.bbettercalendar.ui.common.widget.StreakWeekView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Debug-only gallery of the reusable widgets of {@code ui/common/widget} (plan
 * migracion-visual-stitch, u9), each in its main states, so the components can be checked on a
 * device without wiring a screen. Not in release builds (src/debug). Open with:
 * <pre>
 * adb shell am start -n io.github.thaguille.bbettercalendar/com.example.bbettercalendar.debug.DebugGalleryActivity
 * </pre>
 * Demo data only; nothing here touches the database.
 */
public class DebugGalleryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_gallery);

        bindStreaks();
        bindAppRows();
        bindPeriod();

        SectionHeaderView header = findViewById(R.id.galleryHeaderAction);
        header.setOnActionClickListener(h -> toast("Action: See all"));

        SegmentedControlView kinds = findViewById(R.id.gallerySegmentedDisabled);
        kinds.setItemEnabled(2, false);

        StepperView stepper = findViewById(R.id.galleryStepper);
        stepper.setOnValueChangeListener((v, value) -> v.setCaption(value >= 60
                ? "Target session limit · " + (value / 60) + "h " + (value % 60) + "m"
                : "Target session limit"));
    }

    private void bindStreaks() {
        LocalDate today = LocalDate.now();
        // 12-day run ending today (done today) -> current streak 12, today's dot sage.
        List<LocalDate> doneToday = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            doneToday.add(today.minusDays(i));
        }
        StreakWeekView top = findViewById(R.id.galleryStreakTop);
        top.setStreak(StreakCalculator.computeFromDates(doneToday, today));

        // Today still pending, with a gap 3 days ago -> missed / done / pending / future mix.
        List<LocalDate> pending = new ArrayList<>();
        pending.add(today.minusDays(1));
        pending.add(today.minusDays(2));
        pending.add(today.minusDays(4));
        pending.add(today.minusDays(5));
        StreakWeekView end = findViewById(R.id.galleryStreakEnd);
        end.setStreak(StreakCalculator.computeFromDates(pending, today));
    }

    private void bindAppRows() {
        Drawable icon = getApplicationInfo().loadIcon(getPackageManager());
        int[] ids = {R.id.galleryAppWarning, R.id.galleryAppBlocked, R.id.galleryAppOk, R.id.galleryAppNoLimit};
        for (int id : ids) {
            AppUsageRowView row = findViewById(id);
            row.setAppIcon(icon);
            row.setOnRowClickListener(r -> toast("Open detail"));
            row.setOnLockChangeListener((r, locked) -> toast(locked ? "Locked" : "Unlocked"));
            row.setOnSetLimitClickListener(r -> toast("Set limit"));
        }
        AppUsageRowView blocked = findViewById(R.id.galleryAppBlocked);
        blocked.setStatus(AppUsageRowView.Status.BLOCKED, "Blocked · 00:00");
    }

    private void bindPeriod() {
        SegmentedControlView segmented = findViewById(R.id.gallerySegmented);
        PeriodNavigatorView period = findViewById(R.id.galleryPeriod);
        final String[] titles = {"This week", "This month", "This year"};
        final String[] subtitles = {"Mon – Sun", "Oct 1 – 31", "2026"};
        segmented.setOnSegmentSelectedListener((v, index) -> {
            period.setTitle(titles[index]);
            period.setSubtitle(subtitles[index]);
            period.setNextEnabled(false);
        });
        period.setOnPeriodNavigateListener(new PeriodNavigatorView.OnPeriodNavigateListener() {
            @Override
            public void onPrevious(PeriodNavigatorView view) {
                view.setTitle("Previous");
                view.setNextEnabled(true);
            }

            @Override
            public void onNext(PeriodNavigatorView view) {
                int i = segmented.getSelectedIndex();
                view.setTitle(titles[i]);
                view.setNextEnabled(false);
            }
        });
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
