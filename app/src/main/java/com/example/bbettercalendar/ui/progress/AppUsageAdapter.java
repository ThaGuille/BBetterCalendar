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

// Lista de apps seguidas con su tiempo del rango. Cada fila: [toggle "hacer cumplir"]
// [icono] [nombre] [tiempo (+ límite diario, Phase 3)]. El toggle 🚫 refleja/alterna enforceAtLimit
// (Phase 4a). El icono se resuelve vía PackageManager y se cachea por paquete (lista corta; evita
// relecturas al re-vincular). Tocar la fila abre el diálogo de límite (Phase 3).
public class AppUsageAdapter extends RecyclerView.Adapter<AppUsageAdapter.VH> {

    // Callback de tap en fila: pasa el paquete/etiqueta/límite actual para abrir AppLimitDialog.
    public interface OnRowClickListener {
        void onRowClick(String packageName, String label, int currentLimitMinutes);
    }

    // Callback de tap en el toggle 🚫: el Fragment decide la ruta (sin límite -> diálogo de límite;
    // servicio no activado -> divulgación/Ajustes; si no -> alternar enforceAtLimit).
    public interface OnEnforceToggleListener {
        void onEnforceToggle(String packageName, String label, int currentLimitMinutes,
                             boolean currentlyEnforced);
    }

    private final PackageManager pm;
    private final OnRowClickListener rowClickListener;
    private final OnEnforceToggleListener enforceToggleListener;
    private final Map<String, Drawable> iconCache = new HashMap<>();
    private List<AppUsageRow> rows = Collections.emptyList();
    // ¿Está activado el servicio de Accesibilidad? Decide si un toggle enforce=on se pinta "activo"
    // (rojo, bloqueando) o "pendiente" (ámbar, armado pero el servicio aún no está activado). Global,
    // no por fila; lo fija el Fragment en onResume tras leer AccessibilityAccess.isEnabled().
    private boolean accessibilityEnabled;

    public AppUsageAdapter(Context context, OnRowClickListener rowClickListener,
                           OnEnforceToggleListener enforceToggleListener) {
        this.pm = context.getApplicationContext().getPackageManager();
        this.rowClickListener = rowClickListener;
        this.enforceToggleListener = enforceToggleListener;
    }

    public void submit(@NonNull List<AppUsageRow> newRows) {
        this.rows = newRows;
        notifyDataSetChanged();
    }

    // Lo llama el Fragment (onResume / al volver de Ajustes): re-pinta los toggles enforce=on como
    // "activo" o "pendiente" según el servicio esté o no activado. Sólo re-vincula si cambió.
    public void setAccessibilityEnabled(boolean enabled) {
        if (this.accessibilityEnabled == enabled) return;
        this.accessibilityEnabled = enabled;
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
        bindEnforceToggle(holder, row);
        holder.itemView.setOnClickListener(v ->
                rowClickListener.onRowClick(row.packageName, row.label, row.dailyLimitMinutes));
    }

    // Toggle 🚫, tres estados: apagado (muted, semitransparente) cuando enforce=off; ACTIVO (bb_danger,
    // opaco) cuando enforce=on Y el servicio de Accesibilidad está activado (bloqueando de verdad);
    // PENDIENTE (bb_accent_reward ámbar) cuando enforce=on pero el servicio aún NO está activado —
    // armado, pero no bloqueará hasta que el usuario active el servicio en Ajustes. Así el toggle
    // nunca se pinta "activo" sin el permiso concedido.
    private void bindEnforceToggle(VH holder, AppUsageRow row) {
        int tint;
        float alpha;
        if (!row.enforceAtLimit) {
            tint = R.color.bb_on_surface_muted;
            alpha = 0.35f;
        } else if (accessibilityEnabled) {
            tint = R.color.bb_danger;
            alpha = 1f;
        } else {
            tint = R.color.bb_accent_reward;
            alpha = 0.9f;
        }
        holder.enforceToggle.setImageTintList(ContextCompat.getColorStateList(
                holder.enforceToggle.getContext(), tint));
        holder.enforceToggle.setAlpha(alpha);
        holder.enforceToggle.setOnClickListener(v ->
                enforceToggleListener.onEnforceToggle(row.packageName, row.label,
                        row.dailyLimitMinutes, row.enforceAtLimit));
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
        final ImageView enforceToggle;
        final ImageView icon;
        final TextView label;
        final TextView time;

        VH(@NonNull View itemView) {
            super(itemView);
            enforceToggle = itemView.findViewById(R.id.usage_block_toggle);
            icon = itemView.findViewById(R.id.usage_app_icon);
            label = itemView.findViewById(R.id.usage_app_label);
            time = itemView.findViewById(R.id.usage_app_time);
        }
    }
}
