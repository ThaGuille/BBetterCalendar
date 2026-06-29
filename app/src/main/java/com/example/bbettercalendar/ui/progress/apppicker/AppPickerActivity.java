package com.example.bbettercalendar.ui.progress.apppicker;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.database.AppDatabase;
import com.example.bbettercalendar.stats.AppRule;
import com.example.bbettercalendar.stats.AppRuleDAO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Pantalla "Add apps": lista las apps lanzables instaladas (icono + nombre) con una casilla por
// fila, pre-marcando las ya seguidas. Al guardar persiste el set elegido como AppRule(tracked).
// Carga y guardado van en un executor (PackageManager + DB fuera del hilo principal, regla #3).
public class AppPickerActivity extends AppCompatActivity {

    private AppRuleDAO appRuleDao;
    private ExecutorService executor;

    private AppPickerAdapter adapter;
    private ProgressBar loading;
    private TextView empty;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_picker);

        appRuleDao = AppDatabase.getDatabase(getApplicationContext()).appRuleDao();
        executor = Executors.newSingleThreadExecutor();

        RecyclerView list = findViewById(R.id.app_picker_list);
        loading = findViewById(R.id.app_picker_loading);
        empty = findViewById(R.id.app_picker_empty);

        adapter = new AppPickerAdapter();
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        ImageButton close = findViewById(R.id.app_picker_close);
        TextView save = findViewById(R.id.app_picker_save);
        close.setOnClickListener(v -> finish());
        save.setOnClickListener(v -> saveAndFinish());

        loadApps();
    }

    private void loadApps() {
        loading.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        executor.execute(() -> {
            Set<String> tracked = new HashSet<>();
            for (AppRule rule : appRuleDao.getTracked()) {
                tracked.add(rule.packageName);
            }

            PackageManager pm = getPackageManager();
            Intent launchable = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> infos = pm.queryIntentActivities(launchable, 0);

            List<AppPickItem> result = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            String self = getPackageName();
            for (ResolveInfo info : infos) {
                if (info.activityInfo == null) continue;
                String pkg = info.activityInfo.packageName;
                if (pkg == null || pkg.equals(self) || !seen.add(pkg)) continue;
                String label = info.loadLabel(pm).toString();
                Drawable icon = info.loadIcon(pm);
                boolean checked = tracked.contains(pkg);
                result.add(new AppPickItem(pkg, label, icon, checked, checked));
            }
            Collections.sort(result, (a, b) -> a.label.compareToIgnoreCase(b.label));

            runOnUiThread(() -> {
                loading.setVisibility(View.GONE);
                empty.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
                adapter.submit(result);
            });
        });
    }

    private void saveAndFinish() {
        List<AppPickItem> items = adapter.getItems();
        executor.execute(() -> {
            for (AppPickItem item : items) {
                if (item.checked && !item.wasTracked) {
                    appRuleDao.upsert(new AppRule(item.packageName, true));
                } else if (!item.checked && item.wasTracked) {
                    appRuleDao.setTracked(item.packageName, false);
                }
            }
            runOnUiThread(() -> {
                setResult(Activity.RESULT_OK);
                finish();
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdown();
    }
}
