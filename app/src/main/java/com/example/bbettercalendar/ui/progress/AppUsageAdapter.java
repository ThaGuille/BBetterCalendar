package com.example.bbettercalendar.ui.progress;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.helpers.FormatHelper;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// Lista de apps seguidas con su tiempo del rango. Cada fila: [toggle bloqueo deshabilitado]
// [icono] [nombre] [tiempo (+ límite diario, Phase 3)]. El toggle es un placeholder visual
// (Phase 4). El icono se resuelve vía PackageManager y se cachea por paquete (lista corta; evita
// relecturas al re-vincular). Tocar la fila abre el diálogo de límite (Phase 3).
public class AppUsageAdapter extends RecyclerView.Adapter<AppUsageAdapter.VH> {

    // Callback de tap en fila: pasa el paquete/etiqueta/límite actual para abrir AppLimitDialog.
    public interface OnRowClickListener {
        void onRowClick(String packageName, String label, int currentLimitMinutes);
    }

    private final PackageManager pm;
    private final OnRowClickListener rowClickListener;
    private final Map<String, Drawable> iconCache = new HashMap<>();
    private List<AppUsageRow> rows = Collections.emptyList();

    public AppUsageAdapter(Context context, OnRowClickListener rowClickListener) {
        this.pm = context.getApplicationContext().getPackageManager();
        this.rowClickListener = rowClickListener;
    }

    public void submit(@NonNull List<AppUsageRow> newRows) {
        this.rows = newRows;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_usage_row, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        AppUsageRow row = rows.get(position);
        holder.label.setText(row.label);
        holder.icon.setImageDrawable(iconFor(row.packageName));
        bindTime(holder, row);
        holder.itemView.setOnClickListener(v ->
                rowClickListener.onRowClick(row.packageName, row.label, row.dailyLimitMinutes));
    }

    private void bindTime(VH holder, AppUsageRow row) {
        String used = FormatHelper.formatDuration(row.foregroundMillis);
        // Sin límite -> sólo el tiempo usado.
        if (row.dailyLimitMinutes <= 0) {
            holder.time.setText(used);
            holder.time.setTextColor(ContextCompat.getColor(holder.time.getContext(), R.color.bb_on_surface));
            return;
        }
        // Con límite -> mostrarlo SIEMPRE ("usado / límite"), para que un límite recién guardado sea
        // visible en cualquier rango (Semana es la vista por defecto). El coloreado de "superado" sólo
        // se aplica en vista DAY: el usado acumulado de una semana/mes no es comparable a un límite diario.
        String limit = FormatHelper.formatDuration(TimeUnit.MINUTES.toMillis(row.dailyLimitMinutes));
        holder.time.setText(holder.time.getContext()
                .getString(R.string.progress_usage_limit_progress_format, used, limit));

        long usedMinutes = TimeUnit.MILLISECONDS.toMinutes(row.foregroundMillis);
        boolean overLimit = row.showLimitProgress && usedMinutes >= row.dailyLimitMinutes;
        int colorRes = overLimit ? R.color.bb_danger : R.color.bb_on_surface;
        holder.time.setTextColor(ContextCompat.getColor(holder.time.getContext(), colorRes));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private Drawable iconFor(String packageName) {
        Drawable cached = iconCache.get(packageName);
        if (cached != null) return cached;
        Drawable icon;
        try {
            icon = pm.getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            icon = pm.getDefaultActivityIcon();
        }
        iconCache.put(packageName, icon);
        return icon;
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;
        final TextView time;

        VH(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.usage_app_icon);
            label = itemView.findViewById(R.id.usage_app_label);
            time = itemView.findViewById(R.id.usage_app_time);
        }
    }
}
