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

/** F5-u7: las filas de la bandeja. El toque se lo devuelve al fragmento ({@link Listener}). */
final class InboxAdapter extends RecyclerView.Adapter<InboxAdapter.Holder> {

    interface Listener {
        void onOpen(@NonNull InboxRows.Row row);
    }

    private final Listener listener;
    private final List<InboxRows.Row> rows = new ArrayList<>();

    InboxAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    void submit(@NonNull List<InboxRows.Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_inbox, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(rows.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        private final AvatarView avatar;
        private final TextView title;
        private final TextView body;
        private final TextView age;
        private final View newDot;

        Holder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.inboxAvatar);
            title = itemView.findViewById(R.id.inboxTitle);
            body = itemView.findViewById(R.id.inboxBody);
            age = itemView.findViewById(R.id.inboxAge);
            newDot = itemView.findViewById(R.id.inboxNewDot);
        }

        void bind(@NonNull InboxRows.Row row, @NonNull Listener listener) {
            Context context = itemView.getContext();
            ProfileUi.paintAvatar(avatar, row.fromName, row.fromColor);
            switch (row.type) {
                case CHEER:
                    title.setText(context.getString(R.string.inbox_cheer_title, row.fromName, row.fromHandle));
                    body.setText(context.getString(R.string.inbox_cheer_body, row.fromName, row.fromHandle));
                    break;
                case NUDGE:
                    title.setText(context.getString(R.string.inbox_nudge_title, row.fromName, row.fromHandle));
                    body.setText(context.getString(R.string.inbox_nudge_body, row.fromName, row.fromHandle));
                    break;
                case REQUEST:
                default:
                    title.setText(context.getString(R.string.inbox_request_title, row.fromName, row.fromHandle));
                    body.setText(context.getString(R.string.inbox_request_body, row.fromName, row.fromHandle));
                    break;
            }
            age.setText(ageText(context, row));
            newDot.setVisibility(row.isNew ? View.VISIBLE : View.GONE);
            boolean clickable = row.destination != InboxRows.Destination.NONE;
            itemView.setClickable(clickable);
            itemView.setOnClickListener(clickable ? v -> listener.onOpen(row) : null);
        }

        @NonNull
        private static String ageText(@NonNull Context context, @NonNull InboxRows.Row row) {
            switch (row.ageUnit) {
                case MINUTES:
                    return context.getString(R.string.inbox_age_minutes, row.ageValue);
                case HOURS:
                    return context.getString(R.string.inbox_age_hours, row.ageValue);
                case DAYS:
                    return context.getString(R.string.inbox_age_days, row.ageValue);
                case NOW:
                default:
                    return context.getString(R.string.inbox_age_now);
            }
        }
    }
}
