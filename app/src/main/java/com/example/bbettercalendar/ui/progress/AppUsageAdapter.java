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
import androidx.recyclerview.widget.RecyclerView;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.helpers.FormatHelper;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Lista de apps seguidas con su tiempo del rango. Cada fila: [toggle bloqueo deshabilitado]
// [icono] [nombre] [tiempo]. El toggle es un placeholder visual (Phase 4). El icono se resuelve
// vía PackageManager y se cachea por paquete (lista corta; evita relecturas al re-vincular).
public class AppUsageAdapter extends RecyclerView.Adapter<AppUsageAdapter.VH> {

    private final PackageManager pm;
    private final Map<String, Drawable> iconCache = new HashMap<>();
    private List<AppUsageRow> rows = Collections.emptyList();

    public AppUsageAdapter(Context context) {
        this.pm = context.getApplicationContext().getPackageManager();
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
        holder.time.setText(FormatHelper.formatDuration(row.foregroundMillis));
        holder.icon.setImageDrawable(iconFor(row.packageName));
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
