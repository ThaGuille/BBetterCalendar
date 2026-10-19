package com.example.bbettercalendar.debug;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.TextViewCompat;

import com.example.bbettercalendar.BuildConfig;
import com.example.bbettercalendar.R;
import com.example.bbettercalendar.helpers.FontHelper;
import com.example.bbettercalendar.stats.StreakCalculator;
import com.example.bbettercalendar.ui.common.InsetsHelper;
import com.example.bbettercalendar.ui.common.chart.ChartStyler;
import com.example.bbettercalendar.ui.common.widget.AppUsageRowView;
import com.example.bbettercalendar.ui.common.widget.PeriodNavigatorView;
import com.example.bbettercalendar.ui.common.widget.SectionHeaderView;
import com.example.bbettercalendar.ui.common.widget.SegmentedControlView;
import com.example.bbettercalendar.ui.common.widget.StepperView;
import com.example.bbettercalendar.ui.common.widget.StreakWeekView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Debug-only gallery of the reusable widgets of {@code ui/common/widget} (plan
 * migracion-visual-stitch, u9), each in its main states, so the components can be checked on a
 * device without wiring a screen. Not in release builds (src/debug). Open with:
 * <pre>
 * adb shell am start -n io.github.thaguille.bbettercalendar/com.example.bbettercalendar.debug.DebugGalleryActivity
 * </pre>
 * Demo data only; nothing here touches the database.
 */
@AndroidEntryPoint
public class DebugGalleryActivity extends AppCompatActivity {

    @Inject FirebaseFirestore firestore;

    /**
     * F0-u4: lee un documento y enseña el error. Con las reglas que lo deniegan todo el resultado correcto es
     * PERMISSION_DENIED: prueba a la vez que la app llega a Firebase (al emulador si USE_FIREBASE_EMULATOR) y que
     * las reglas mandan. Cualquier otro código (UNAVAILABLE...) es que no hay conexión con el emulador.
     */
    private void bindFirebasePing() {
        Button button = findViewById(R.id.galleryFirebasePingButton);
        TextView result = findViewById(R.id.galleryFirebasePingResult);
        button.setOnClickListener(v -> {
            result.setText("Pinging " + (BuildConfig.USE_FIREBASE_EMULATOR ? "emulator" : "project") + "...");
            firestore.collection("ping").document("x").get().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    result.setText("UNEXPECTED: read allowed (rules are not deny-all)");
                    return;
                }
                Exception e = task.getException();
                String code = e instanceof FirebaseFirestoreException
                        ? ((FirebaseFirestoreException) e).getCode().name() : String.valueOf(e);
                result.setText(code + (code.equals("PERMISSION_DENIED") ? " (expected)" : " - not reaching Firebase?"));
            });
        });
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetsHelper.enableEdgeToEdge(this);
        setContentView(R.layout.activity_debug_gallery);
        InsetsHelper.applyToContent(this);

        bindFirebasePing();
        bindCharts();
        bindFontWeights();
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

    /**
     * u10: the ChartStyler looks the Progress carousel does not use yet (u17-u20): hero line +
     * dashed comparison (html 13), stacked within/over-limit bars with a limit line (html 10),
     * usage line + FOCUS bands (html 14).
     */
    private void bindCharts() {
        LinearLayout box = findViewById(R.id.galleryCharts);
        ChartStyler styler = new ChartStyler(this);
        int height = (int) (180 * getResources().getDisplayMetrics().density);

        // Hero + comparativa: 12 semanas, horas.
        float[] now = {6, 8, 7, 10, 12, 11, 13, 15, 14, 16, 17, 19};
        float[] prev = {4, 5, 5, 6, 7, 6, 8, 8, 9, 9, 10, 11};
        List<Entry> a = new ArrayList<>();
        List<Entry> b = new ArrayList<>();
        for (int i = 0; i < now.length; i++) {
            a.add(new Entry(i, now[i]));
            b.add(new Entry(i, prev[i]));
        }
        LineChart hero = new LineChart(this);
        hero.setData(new LineData(styler.styleCompareLine(new LineDataSet(b, "prev")),
                styler.styleHeroLine(new LineDataSet(a, "now"))));
        styler.styleChart(hero, null);
        styler.fitYAxis(hero, 19);
        styler.setYAxisFormatter(hero, ChartStyler.unitFormatter("h"));
        box.addView(hero, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height));

        // Barras apiladas con límite de 45 min (html 10).
        float[] usage = {38, 54, 25, 62, 48, 35, 42};
        List<BarEntry> bars = new ArrayList<>();
        for (int i = 0; i < usage.length; i++) {
            bars.add(new BarEntry(i, ChartStyler.splitAtLimit(usage[i], 45)));
        }
        BarChart limit = new BarChart(this);
        BarData barData = new BarData(styler.styleLimitBars(new BarDataSet(bars, "")));
        barData.setBarWidth(ChartStyler.barWidth());
        limit.setData(barData);
        styler.styleChart(limit, new String[]{"M", "T", "W", "T", "F", "S", "S"});
        styler.fitYAxis(limit, 62);
        styler.setYAxisFormatter(limit, ChartStyler.unitFormatter("m"));
        styler.addLimitLine(limit, 45, "45m");
        box.addView(limit, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height));

        // Uso por hora + bandas FOCUS (html 14).
        float[] perHour = {2, 1, 0, 0, 0, 0, 1, 6, 9, 0, 0, 3, 14, 18, 4, 0, 0, 8, 12, 15, 10, 7, 5, 3};
        boolean[] focus = new boolean[24];
        focus[9] = focus[10] = focus[15] = focus[16] = true;
        List<Entry> line = new ArrayList<>();
        int peak = 0;
        for (int h = 0; h < perHour.length; h++) {
            line.add(new Entry(h, perHour[h]));
            if (perHour[h] > perHour[peak]) peak = h;
        }
        LineDataSet usageSet = styler.styleUsageLine(new LineDataSet(line, "usage"));
        styler.markEntry(usageSet.getEntryForIndex(peak));
        usageSet.setDrawIcons(true);
        float top = ChartStyler.niceMax(perHour[peak]);
        CombinedData combined = new CombinedData();
        combined.setData(new LineData(usageSet));
        combined.setData(styler.focusBandsData(focus, top));
        CombinedChart bands = new CombinedChart(this);
        styler.styleBandsChart(bands);
        bands.setData(combined);
        styler.styleChart(bands, null);
        styler.fitYAxis(bands, top);
        bands.getXAxis().setAxisMinimum(-0.5f);
        bands.getXAxis().setAxisMaximum(23.5f);
        box.addView(bands, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height));
    }

    /** u9b: FontHelper.appTypeface(ctx, weight) for the five bundled weights. */
    private void bindFontWeights() {
        LinearLayout box = findViewById(R.id.galleryFontWeights);
        for (int weight = 400; weight <= 800; weight += 100) {
            TextView tv = new TextView(this);
            TextViewCompat.setTextAppearance(tv, R.style.TextAppearance_BBetter_Body);
            tv.setTypeface(FontHelper.appTypeface(this, weight));
            tv.setText("FontHelper " + weight + " · Focus 5h 52m");
            box.addView(tv);
        }
    }

    private void bindStreaks() {
        LocalDate today = LocalDate.now();
        // 12-day run ending today (done today), goal 3/week -> a few weeks met, today's dot sage.
        List<LocalDate> doneToday = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            doneToday.add(today.minusDays(i));
        }
        StreakWeekView top = findViewById(R.id.galleryStreakTop);
        top.setStreak(StreakCalculator.computeFromDates(doneToday, 3, today));

        // Today still pending, with a gap 3 days ago -> rest / done / pending / future mix.
        List<LocalDate> pending = new ArrayList<>();
        pending.add(today.minusDays(1));
        pending.add(today.minusDays(2));
        pending.add(today.minusDays(4));
        pending.add(today.minusDays(5));
        StreakWeekView end = findViewById(R.id.galleryStreakEnd);
        end.setStreak(StreakCalculator.computeFromDates(pending, 5, today));
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
