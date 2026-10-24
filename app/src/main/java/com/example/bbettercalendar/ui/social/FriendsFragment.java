package com.example.bbettercalendar.ui.social;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
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
import com.example.bbettercalendar.social.account.AccountState;
import com.example.bbettercalendar.social.friends.FriendCard;
import com.example.bbettercalendar.social.friends.FriendRequest;
import com.example.bbettercalendar.social.friends.ReportReason;
import com.example.bbettercalendar.ui.common.InsetsHelper;
import com.example.bbettercalendar.ui.common.TopBarHelper;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * F2-u7: Amigos. Una lista con tres secciones: Solicitudes (Aceptar, Rechazar; Reportar en el menú; las
 * {@code failed} con su motivo y Descartar), Amigos (menú: Eliminar de amigos con confirmación y Reportar)
 * y Enviadas (Cancelar). Arriba, Añadir (y, desde u8, Invitar). Sin presencia. El argumento opcional
 * {@code section=requests} sube hasta las solicitudes.
 */
@AndroidEntryPoint
public class FriendsFragment extends Fragment implements FriendsAdapter.Listener {

    public static final String ARG_SECTION = "section";
    public static final String SECTION_REQUESTS = "requests";
    static final String TAG_ADD = "friends-add";
    static final String TAG_REPORT = "friends-report";

    private FriendsViewModel viewModel;
    private FriendsAdapter adapter;
    private RecyclerView list;
    private TextView status;
    private View empty;
    private View actions;
    private boolean scrolledToSection;

    public FriendsFragment() {
        super(R.layout.fragment_friends);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(FriendsViewModel.class);
        TopBarHelper.bind(this, view, R.string.friends_top_title);
        list = view.findViewById(R.id.friendsList);
        status = view.findViewById(R.id.friendsStatus);
        empty = view.findViewById(R.id.friendsEmpty);
        actions = view.findViewById(R.id.friendsActions);
        // Edge-to-edge: la lista llega al final de la pantalla y deja sitio a la barra de navegación.
        InsetsHelper.apply(list, InsetsHelper.BOTTOM);
        InsetsHelper.apply(empty, InsetsHelper.BOTTOM);

        adapter = new FriendsAdapter(this);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        View.OnClickListener add = v -> showAddSheet();
        view.findViewById(R.id.friendsAddButton).setOnClickListener(add);
        view.findViewById(R.id.friendsEmptyAdd).setOnClickListener(add);
        // Invitar (friendsInviteButton / friendsEmptyInvite) sigue oculto hasta la hoja de F2-u8.

        viewModel.getScreen().observe(getViewLifecycleOwner(), this::render);
        viewModel.getEvent().observe(getViewLifecycleOwner(), event -> {
            if (event == null) {
                return;
            }
            viewModel.consumeEvent();
            Toast.makeText(requireContext().getApplicationContext(),
                    event.arg == null ? getString(event.message) : getString(event.message, event.arg),
                    Toast.LENGTH_SHORT).show();
            if (event.offerRemove != null) {
                offerRemoveAfterReport(event.offerRemove);
            }
        });
        new ViewModelProvider(this).get(ProfileViewModel.class).getAccountState()
                .observe(getViewLifecycleOwner(), state -> {
                    if (state == AccountState.SIGNED_OUT) {
                        NavHostFragment.findNavController(this).popBackStack();
                    }
                });
    }

    private void render(@Nullable FriendsViewModel.Screen screen) {
        if (screen == null) {
            return;
        }
        switch (screen.status) {
            case ERROR:
                list.setVisibility(View.GONE);
                empty.setVisibility(View.GONE);
                actions.setVisibility(View.GONE);
                status.setText(R.string.friends_error);
                status.setVisibility(View.VISIBLE);
                break;
            case LOADING:
                list.setVisibility(View.GONE);
                empty.setVisibility(View.GONE);
                actions.setVisibility(View.VISIBLE);
                status.setText(R.string.friends_loading);
                status.setVisibility(View.VISIBLE);
                break;
            default:
                status.setVisibility(View.GONE);
                adapter.submit(screen.rows);
                // Vacío: el propio estado vacío trae las dos acciones, así que no se repiten arriba.
                actions.setVisibility(screen.empty ? View.GONE : View.VISIBLE);
                empty.setVisibility(screen.empty ? View.VISIBLE : View.GONE);
                list.setVisibility(screen.empty ? View.GONE : View.VISIBLE);
                scrollToRequestedSection();
                break;
        }
    }

    private void scrollToRequestedSection() {
        Bundle args = getArguments();
        if (scrolledToSection || args == null
                || !SECTION_REQUESTS.equals(args.getString(ARG_SECTION))) {
            return;
        }
        int position = adapter.positionOfRequests();
        if (position >= 0) {
            scrolledToSection = true;
            list.scrollToPosition(position);
        }
    }

    private void showAddSheet() {
        if (getChildFragmentManager().findFragmentByTag(TAG_ADD) == null) {
            viewModel.resetAdd();
            new AddFriendSheet().show(getChildFragmentManager(), TAG_ADD);
        }
    }

    private void showReportSheet(@NonNull FriendCard target, @NonNull ReportReason.Context context) {
        if (getChildFragmentManager().findFragmentByTag(TAG_REPORT) == null) {
            viewModel.resetReport();
            ReportSheet.newInstance(target, context).show(getChildFragmentManager(), TAG_REPORT);
        }
    }

    // ---- FriendsAdapter.Listener -------------------------------------------------------------------------

    @Override
    public void onAccept(@NonNull FriendRequest request) {
        viewModel.accept(request);
    }

    @Override
    public void onDecline(@NonNull FriendRequest request) {
        viewModel.decline(request);
    }

    @Override
    public void onCancel(@NonNull FriendRequest request) {
        viewModel.cancel(request);
    }

    @Override
    public void onMenu(@NonNull View anchor, @NonNull FriendsViewModel.Row row) {
        FriendCard card = row.card;
        if (card == null) {
            return;
        }
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        final int idReport = 1;
        final int idRemove = 2;
        boolean isFriend = row.type == FriendsViewModel.Row.Type.FRIEND;
        // Rechazar y Aceptar ya están en la fila; el menú trae lo que no cabe en ella.
        menu.getMenu().add(0, idReport, 0, R.string.friends_report);
        if (isFriend) {
            menu.getMenu().add(0, idRemove, 1, R.string.friends_remove);
        }
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == idReport) {
                showReportSheet(card, isFriend ? ReportReason.Context.FRIEND : ReportReason.Context.REQUEST);
            } else if (item.getItemId() == idRemove) {
                confirmRemove(card);
            }
            return true;
        });
        menu.show();
    }

    private void confirmRemove(@NonNull FriendCard friend) {
        new AlertDialog.Builder(requireContext(), R.style.RoundedDialog)
                .setTitle(getString(R.string.friends_remove_title, friend.displayName))
                .setMessage(R.string.friends_remove_message)
                .setNegativeButton(R.string.friends_cancel, null)
                .setPositiveButton(R.string.friends_remove_confirm, (d, which) -> viewModel.remove(friend))
                .show();
    }

    /** Tras reportar desde la lista: «¿Eliminar también a X?», con dos botones que dicen qué hacen. */
    private void offerRemoveAfterReport(@NonNull FriendCard friend) {
        new AlertDialog.Builder(requireContext(), R.style.RoundedDialog)
                .setTitle(getString(R.string.report_remove_title, friend.displayName))
                .setMessage(R.string.report_remove_message)
                .setNegativeButton(R.string.report_remove_no, null)
                .setPositiveButton(R.string.report_remove_yes, (d, which) -> viewModel.remove(friend))
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        list.setAdapter(null);
        list = null;
        adapter = null;
    }
}
