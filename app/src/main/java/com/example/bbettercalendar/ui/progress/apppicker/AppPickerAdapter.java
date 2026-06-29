package com.example.bbettercalendar.ui.progress.apppicker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bbettercalendar.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Lista multi-selección de apps instaladas. El estado de selección vive en cada AppPickItem;
// el click de fila lo alterna (la CheckBox no es clicable por sí misma).
public class AppPickerAdapter extends RecyclerView.Adapter<AppPickerAdapter.VH> {

    private List<AppPickItem> items = Collections.emptyList();

    public void submit(@NonNull List<AppPickItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    public List<AppPickItem> getItems() {
        return new ArrayList<>(items);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_pick, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        AppPickItem item = items.get(position);
        holder.icon.setImageDrawable(item.icon);
        holder.label.setText(item.label);
        holder.check.setChecked(item.checked);
        holder.itemView.setOnClickListener(v -> {
            item.checked = !item.checked;
            holder.check.setChecked(item.checked);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;
        final CheckBox check;

        VH(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.app_pick_icon);
            label = itemView.findViewById(R.id.app_pick_label);
            check = itemView.findViewById(R.id.app_pick_check);
        }
    }
}
