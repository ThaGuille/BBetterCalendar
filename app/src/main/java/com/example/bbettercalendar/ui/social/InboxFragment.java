package com.example.bbettercalendar.ui.social;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.social.inbox.InboxState;
import com.example.bbettercalendar.ui.common.InsetsHelper;
import com.example.bbettercalendar.ui.common.TopBarHelper;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * F5-u7: la bandeja de la campana de Home: ánimos, empujones y solicitudes recibidos, el más nuevo primero. Abrirla
 * los marca leídos (d1). Tocar uno lleva al perfil del amigo o, una solicitud de quien aún no es amigo, a Amigos
 * en Solicitudes (d4). Argumento opcional {@code openId}: el aviso de la notificación que la abrió.
 */
@AndroidEntryPoint
public class InboxFragment extends Fragment implements InboxAdapter.Listener {

    public static final String ARG_OPEN_ID = "openId";

    private InboxAdapter adapter;
    private RecyclerView list;
    private TextView status;
    private View empty;

    public InboxFragment() {
        super(R.layout.fragment_inbox);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InboxViewModel viewModel = new ViewModelProvider(this).get(InboxViewModel.class);
        TopBarHelper.bind(this, view, R.string.inbox_top_title);
        list = view.findViewById(R.id.inboxList);
        status = view.findViewById(R.id.inboxStatus);
        empty = view.findViewById(R.id.inboxEmpty);
        InsetsHelper.apply(list, InsetsHelper.BOTTOM);
        InsetsHelper.apply(empty, InsetsHelper.BOTTOM);

        adapter = new InboxAdapter(this);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        Bundle args = getArguments();
        viewModel.openFromNotification(args == null ? null : args.getString(ARG_OPEN_ID));
        viewModel.getScreen().observe(getViewLifecycleOwner(), this::render);
    }

    private void render(@Nullable InboxViewModel.Screen screen) {
        if (screen == null) {
            return;
        }
        switch (screen.status) {
            case SIGNED_OUT:
                NavHostFragment.findNavController(this).popBackStack();
                return;
            case ERROR:
                show(R.string.inbox_error, false, false);
                return;
            case READY:
                adapter.submit(screen.rows);
                boolean none = screen.rows.isEmpty();
                show(0, !none, none);
                return;
            case LOADING:
            default:
                show(R.string.inbox_loading, false, false);
        }
    }

    private void show(int statusText, boolean listVisible, boolean emptyVisible) {
        if (statusText == 0) {
            status.setVisibility(View.GONE);
        } else {
            status.setText(statusText);
            status.setVisibility(View.VISIBLE);
        }
        list.setVisibility(listVisible ? View.VISIBLE : View.GONE);
        empty.setVisibility(emptyVisible ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onOpen(@NonNull InboxRows.Row row) {
        NavController nav = NavHostFragment.findNavController(this);
        switch (row.destination) {
            case PROFILE: {
                Bundle args = new Bundle();
                args.putString(FriendProfileFragment.ARG_UID, row.fromUid);
                args.putString(FriendProfileFragment.ARG_HANDLE, row.fromHandle);
                args.putString(FriendProfileFragment.ARG_NAME, row.fromName);
                args.putInt(FriendProfileFragment.ARG_COLOR, row.fromColor);
                nav.navigate(R.id.navigation_friend_profile, args);
                break;
            }
            case FRIEND_REQUESTS: {
                Bundle args = new Bundle();
                args.putString(FriendsFragment.ARG_SECTION, FriendsFragment.SECTION_REQUESTS);
                nav.navigate(R.id.navigation_friends, args);
                break;
            }
            case NONE:
            default:
                break;
        }
    }
}
