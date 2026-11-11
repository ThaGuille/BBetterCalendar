package com.example.bbettercalendar.ui.progress.apppicker;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
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
import com.example.bbettercalendar.blocking.FocusAllowlist;
import com.example.bbettercalendar.database.IoExecutor;
import com.example.bbettercalendar.stats.AppRule;
import com.example.bbettercalendar.stats.AppRuleDAO;
import com.example.bbettercalendar.ui.common.InsetsHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

// Pantalla "Add apps": lista las apps lanzables instaladas (icono + nombre) con una casilla por
// fila, pre-marcando las ya seguidas. Al guardar persiste el set elegido como AppRule(tracked).
// Carga y guardado van en un executor (PackageManager + DB fuera del hilo principal, regla #3).
//
// u10: con EXTRA_MODE = MODE_ALLOWLIST elige las "apps siempre permitidas" del escudo de enfoque:
// título propio, pre-marca la lista actual (o, la primera vez y con la lista vacía, sugiere el
// marcador y la app de mapas por defecto sin guardar nada hasta pulsar Guardar), guarda en
// FocusAllowlist y NUNCA toca AppRule.
@AndroidEntryPoint
public class AppPickerActivity extends AppCompatActivity {

    /** Modo del picker; sin extra = seguir apps (AppRule). */
    public static final String EXTRA_MODE = "mode";
    /** Apps siempre permitidas del escudo de enfoque (FocusAllowlist). */
    public static final String MODE_ALLOWLIST = "allowlist";

    /** Intent del picker en modo "siempre permitidas". */
    public static Intent allowlistIntent(Context context) {
        return new Intent(context, AppPickerActivity.class).putExtra(EXTRA_MODE, MODE_ALLOWLIST);
    }

    @Inject AppRuleDAO appRuleDao;
    @Inject @IoExecutor ExecutorService executor;

    private AppPickerAdapter adapter;
    private ProgressBar loading;
    private TextView empty;
    private boolean allowlistMode;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InsetsHelper.enableEdgeToEdge(this);
        setContentView(R.layout.activity_app_picker);
        // Sin campos de texto: no hace falta el IME de applyToContent. El inset inferior va en la
        // lista (clipToPadding="false") para que se desplace bajo la barra de navegación.
        InsetsHelper.apply(findViewById(android.R.id.content),
                InsetsHelper.TOP | InsetsHelper.HORIZONTAL);

        RecyclerView list = findViewById(R.id.app_picker_list);
        InsetsHelper.apply(list, InsetsHelper.BOTTOM);
        loading = findViewById(R.id.app_picker_loading);
        empty = findViewById(R.id.app_picker_empty);

        allowlistMode = MODE_ALLOWLIST.equals(getIntent().getStringExtra(EXTRA_MODE));
        TextView title = findViewById(R.id.app_picker_title);
        title.setText(allowlistMode ? R.string.app_picker_title_allowlist : R.string.app_picker_title);

        adapter = new AppPickerAdapter();
        adapter.setCheckDescription(getString(allowlistMode
                ? R.string.app_picker_check_allowlist : R.string.app_picker_check));
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
            PackageManager pm = getPackageManager();
            // Lo que sale marcado al abrir: las apps seguidas o, en modo allowlist, la lista actual.
            Set<String> tracked = new HashSet<>();
            Set<String> suggestions = Collections.emptySet();
            if (allowlistMode) {
                tracked.addAll(FocusAllowlist.get(this));
                if (tracked.isEmpty() && !FocusAllowlist.isSuggestionDone(this)) {
                    suggestions = resolveSuggestions(pm);
                }
            } else {
                for (AppRule rule : appRuleDao.getTracked()) {
                    tracked.add(rule.packageName);
                }
            }

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
                boolean was = tracked.contains(pkg);
                boolean suggested = !was && suggestions.contains(pkg);
                AppPickItem item = new AppPickItem(pkg, label, icon, was || suggested, was);
                item.suggested = suggested;
                result.add(item);
            }
            Collections.sort(result, (a, b) -> a.label.compareToIgnoreCase(b.label));
            if (allowlistMode) {
                // Las elegidas (y sugeridas) arriba: la lista de apps instaladas es larga.
                Collections.sort(result, (a, b) -> Boolean.compare(b.checked, a.checked));
            }

            runOnUiThread(() -> {
                loading.setVisibility(View.GONE);
                empty.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
                adapter.submit(result);
            });
        });
    }

    private void saveAndFinish() {
        List<AppPickItem> items = adapter.getItems();
        if (allowlistMode) {
            saveAllowlistAndFinish(items);
            return;
        }
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

    // Modo allowlist: la lista nueva = la actual, + marcadas, - desmarcadas de las que se ven (un
    // paquete guardado que ya no es lanzable no sale en la lista y se conserva). Nunca toca AppRule.
    private void saveAllowlistAndFinish(List<AppPickItem> items) {
        executor.execute(() -> {
            Set<String> next = new HashSet<>(FocusAllowlist.get(this));
            for (AppPickItem item : items) {
                if (item.checked) {
                    next.add(item.packageName);
                } else {
                    next.remove(item.packageName);
                }
            }
            FocusAllowlist.set(this, next);
            // Guardar confirma (o descarta) la sugerencia inicial: no se vuelve a proponer.
            FocusAllowlist.markSuggestionDone(this);
            runOnUiThread(() -> {
                setResult(Activity.RESULT_OK);
                finish();
            });
        });
    }

    // Sugerencia inicial de "siempre permitidas": el marcador y la app de mapas POR DEFECTO, si se
    // pueden resolver. resolveActivity(MATCH_DEFAULT_ONLY) sin predeterminado devuelve el selector
    // del sistema (paquete "android"), que no es lanzable y por tanto nunca casa con una fila.
    private static Set<String> resolveSuggestions(PackageManager pm) {
        Set<String> out = new HashSet<>();
        addDefaultHandler(pm, new Intent(Intent.ACTION_DIAL), out);
        addDefaultHandler(pm, new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")), out);
        return out;
    }

    private static void addDefaultHandler(PackageManager pm, Intent intent, Set<String> out) {
        ResolveInfo info = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (info != null && info.activityInfo != null && info.activityInfo.packageName != null) {
            out.add(info.activityInfo.packageName);
        }
    }

}
