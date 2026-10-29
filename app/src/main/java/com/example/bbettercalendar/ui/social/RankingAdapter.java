package com.example.bbettercalendar.ui.social;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.widget.AvatarView;

import java.util.ArrayList;
import java.util.List;

/**
 * F3-u7: las filas de la pestaña Ranking. Solo pinta lo que {@link RankingRows} ya decidió; tocar la fila de
 * un amigo se lo devuelve al fragmento (la mía no abre nada).
 */
final class RankingAdapter extends RecyclerView.Adapter<RankingAdapter.Holder> {

    interface Listener {
        void onOpen(@NonNull RankingRows.Item item);
    }

    private final Listener listener;
    private final List<RankingRows.Item> items = new ArrayList<>();
    private RankingRows.Category category = RankingRows.Category.MINUTES;

    RankingAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    void submit(@NonNull RankingRows rows) {
        category = rows.category;
        items.clear();
        items.addAll(rows.items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ranking_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position), category, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView position;
        final AvatarView avatar;
        final TextView name;
        final TextView handle;
        final TextView value;

        Holder(@NonNull View itemView) {
            super(itemView);
            position = itemView.findViewById(R.id.rankingPosition);
            avatar = itemView.findViewById(R.id.rankingAvatar);
            name = itemView.findViewById(R.id.rankingName);
            handle = itemView.findViewById(R.id.rankingHandle);
            value = itemView.findViewById(R.id.rankingValue);
        }

        void bind(@NonNull RankingRows.Item item, @NonNull RankingRows.Category category,
                  @NonNull Listener listener) {
            Context context = itemView.getContext();
            // Mi fila: mi nombre si ya llegó mi perfil, si no «Tú». Un amigo sin tarjeta: su @usuario.
            String shownName;
            if (item.name != null && !item.name.trim().isEmpty()) {
                shownName = item.name;
            } else if (item.isMe || item.handle == null) {
                shownName = context.getString(R.string.ranking_me_name);
            } else {
                shownName = item.handle;
            }
            position.setText(String.valueOf(item.position));
            position.setContentDescription(context.getString(R.string.ranking_position_description, item.position));
            ProfileUi.paintAvatar(avatar, shownName, item.avatarColor);
            name.setText(shownName);
            if (item.handle == null) {
                handle.setVisibility(View.GONE);
            } else {
                handle.setText("@" + item.handle);
                handle.setVisibility(View.VISIBLE);
            }
            if (category == RankingRows.Category.MINUTES) {
                value.setText(context.getString(R.string.ranking_value_minutes, item.value));
            } else if (item.goal > 0) {
                value.setText(context.getString(R.string.ranking_value_days, item.value, item.goal));
            } else {
                value.setText(context.getString(R.string.ranking_value_days_nogoal, item.value));
            }
            itemView.setBackgroundResource(item.isMe ? R.drawable.bg_ranking_row_me : R.drawable.bg_card);
            if (item.isMe) {
                itemView.setOnClickListener(null);
                itemView.setClickable(false);
            } else {
                itemView.setOnClickListener(v -> listener.onOpen(item));
            }
        }
    }
}
