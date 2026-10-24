package com.example.bbettercalendar.ui.social;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.social.friends.FriendCard;
import com.example.bbettercalendar.social.friends.FriendRequest;
import com.example.bbettercalendar.ui.common.widget.AvatarView;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * F2-u7: las filas de Amigos (cabeceras de sección y personas). No enseña presencia (llega en F3).
 * Las acciones se las devuelve al fragmento a través de {@link Listener}; el menú «⋮» lo abre él.
 */
final class FriendsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    interface Listener {
        void onAccept(@NonNull FriendRequest request);

        void onDecline(@NonNull FriendRequest request);

        void onCancel(@NonNull FriendRequest request);

        void onMenu(@NonNull View anchor, @NonNull FriendsViewModel.Row row);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_PERSON = 1;

    private final Listener listener;
    private final List<FriendsViewModel.Row> rows = new ArrayList<>();

    FriendsAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    void submit(@NonNull List<FriendsViewModel.Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    /** Posición de la cabecera de Solicitudes (o -1), para subir hasta ella. */
    int positionOfRequests() {
        for (int i = 0; i < rows.size(); i++) {
            FriendsViewModel.Row r = rows.get(i);
            if (r.type == FriendsViewModel.Row.Type.HEADER && r.titleRes == R.string.friends_section_requests) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type == FriendsViewModel.Row.Type.HEADER ? TYPE_HEADER : TYPE_PERSON;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.item_friend_header, parent, false));
        }
        return new PersonHolder(inflater.inflate(R.layout.item_friend, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        FriendsViewModel.Row row = rows.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).title.setText(
                    holder.itemView.getContext().getString(row.titleRes, row.count));
        } else {
            ((PersonHolder) holder).bind(row, listener);
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private static final class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView title;

        HeaderHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.friendHeader);
        }
    }

    private static final class PersonHolder extends RecyclerView.ViewHolder {
        final AvatarView avatar;
        final TextView name;
        final TextView handle;
        final TextView note;
        final ImageButton menu;
        final View actions;
        final MaterialButton primary;
        final MaterialButton secondary;

        PersonHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.friendAvatar);
            name = itemView.findViewById(R.id.friendName);
            handle = itemView.findViewById(R.id.friendHandle);
            note = itemView.findViewById(R.id.friendNote);
            menu = itemView.findViewById(R.id.friendMenu);
            actions = itemView.findViewById(R.id.friendActions);
            primary = itemView.findViewById(R.id.friendPrimary);
            secondary = itemView.findViewById(R.id.friendSecondary);
        }

        void bind(@NonNull FriendsViewModel.Row row, @NonNull Listener listener) {
            FriendCard card = row.card;
            FriendRequest request = row.request;
            ProfileUi.paintAvatar(avatar, card.displayName, card.avatarColor);
            name.setText(card.displayName);
            handle.setText("@" + card.handle);
            note.setVisibility(View.GONE);
            actions.setVisibility(View.GONE);
            secondary.setVisibility(View.VISIBLE);
            menu.setVisibility(View.VISIBLE);
            menu.setOnClickListener(v -> listener.onMenu(v, row));
            switch (row.type) {
                case REQUEST:
                    actions.setVisibility(View.VISIBLE);
                    secondary.setText(R.string.friends_decline);
                    secondary.setOnClickListener(v -> listener.onDecline(request));
                    primary.setText(R.string.friends_accept);
                    primary.setOnClickListener(v -> listener.onAccept(request));
                    break;
                case FAILED:
                    // Aceptada pero la Function no pudo crear la amistad: motivo y «Descartar».
                    showNote(failText(request));
                    actions.setVisibility(View.VISIBLE);
                    secondary.setVisibility(View.GONE);
                    primary.setText(R.string.friends_dismiss);
                    primary.setOnClickListener(v -> listener.onDecline(request));
                    break;
                case SENT:
                    menu.setVisibility(View.GONE);
                    note.setTextAppearance(R.style.TextAppearance_BBetter_Caption);
                    note.setText(R.string.friends_sent_note);
                    note.setVisibility(View.VISIBLE);
                    actions.setVisibility(View.VISIBLE);
                    secondary.setVisibility(View.GONE);
                    primary.setText(R.string.friends_cancel_request);
                    primary.setOnClickListener(v -> listener.onCancel(request));
                    break;
                default: // FRIEND: solo el menú
                    break;
            }
        }

        private void showNote(@StringRes int text) {
            note.setTextAppearance(R.style.TextAppearance_BBetter_SocialError);
            note.setText(text);
            note.setVisibility(View.VISIBLE);
        }

        @StringRes
        private static int failText(@NonNull FriendRequest request) {
            if (FriendRequest.FAIL_CAP.equals(request.failReason)) {
                return R.string.friends_failed_cap;
            }
            if (FriendRequest.FAIL_GONE.equals(request.failReason)) {
                return R.string.friends_failed_gone;
            }
            return R.string.friends_failed_other;
        }
    }
}
