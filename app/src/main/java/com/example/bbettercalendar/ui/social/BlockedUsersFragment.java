package com.example.bbettercalendar.ui.social;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.InsetsHelper;
import com.example.bbettercalendar.ui.common.TopBarHelper;
import com.example.bbettercalendar.ui.common.widget.AvatarView;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * L-u10: «Usuarios bloqueados» (desde Mi perfil). Una tarjeta por bloqueado con Desbloquear; sin nadie, un
 * estado vacío. Desbloquear borra el documento y el listener quita la fila; si falla, un aviso y la fila sigue.
 */
@AndroidEntryPoint
public class BlockedUsersFragment extends Fragment {

    private BlockViewModel viewModel;
    private Adapter adapter;
    private RecyclerView list;
    private TextView status;
    private View empty;
    private boolean left;

    public BlockedUsersFragment() {
        super(R.layout.fragment_blocked_users);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(BlockViewModel.class);
        TopBarHelper.bind(this, view, R.string.blocked_top_title);
        list = view.findViewById(R.id.blockedList);
        status = view.findViewById(R.id.blockedStatus);
        empty = view.findViewById(R.id.blockedEmpty);
        InsetsHelper.apply(list, InsetsHelper.BOTTOM);
        InsetsHelper.apply(empty, InsetsHelper.BOTTOM);

        adapter = new Adapter(row -> viewModel.unblock(row.uid, row.name));
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        viewModel.getBlocked().observe(getViewLifecycleOwner(), state -> render(BlockedRows.from(state)));
        viewModel.getEvent().observe(getViewLifecycleOwner(), event -> {
            if (event == null) {
                return;
            }
            viewModel.consumeEvent();
            Toast.makeText(requireContext().getApplicationContext(),
                    event.arg == null ? getString(event.message) : getString(event.message, event.arg),
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void render(@NonNull BlockedRows rows) {
        if (rows.status == BlockedRows.Status.GONE) {
            if (!left) {
                left = true;
                NavHostFragment.findNavController(this).popBackStack();
            }
            return;
        }
        boolean message = rows.status == BlockedRows.Status.LOADING || rows.status == BlockedRows.Status.ERROR;
        status.setVisibility(message ? View.VISIBLE : View.GONE);
        status.setText(rows.status == BlockedRows.Status.ERROR ? R.string.blocked_error : R.string.blocked_loading);
        empty.setVisibility(rows.status == BlockedRows.Status.EMPTY ? View.VISIBLE : View.GONE);
        boolean ready = rows.status == BlockedRows.Status.READY;
        list.setVisibility(ready ? View.VISIBLE : View.GONE);
        adapter.submit(rows.rows);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        list.setAdapter(null);
        list = null;
        adapter = null;
    }

    private static final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {

        interface Listener {
            void onUnblock(@NonNull BlockedRows.Row row);
        }

        private final Listener listener;
        private final List<BlockedRows.Row> rows = new ArrayList<>();

        Adapter(@NonNull Listener listener) {
            this.listener = listener;
        }

        void submit(@NonNull List<BlockedRows.Row> newRows) {
            rows.clear();
            rows.addAll(newRows);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_blocked, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            BlockedRows.Row row = rows.get(position);
            ProfileUi.paintAvatar(holder.avatar, row.name, row.avatarColor);
            holder.name.setText(row.name);
            holder.handle.setText(row.handleText);
            holder.unblock.setOnClickListener(v -> listener.onUnblock(row));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final AvatarView avatar;
            final TextView name;
            final TextView handle;
            final MaterialButton unblock;

            Holder(@NonNull View itemView) {
                super(itemView);
                avatar = itemView.findViewById(R.id.blockedAvatar);
                name = itemView.findViewById(R.id.blockedName);
                handle = itemView.findViewById(R.id.blockedHandle);
                unblock = itemView.findViewById(R.id.blockedUnblock);
            }
        }
    }
}
