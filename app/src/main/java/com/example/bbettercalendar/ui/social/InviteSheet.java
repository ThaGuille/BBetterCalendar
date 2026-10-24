package com.example.bbettercalendar.ui.social;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.social.invite.QrMatrix;
import com.example.bbettercalendar.ui.common.InsetsHelper;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * F2-u8: hoja Invitar (patrón de QuickAddTaskSheet, insets de F0-u2). Enseña tu {@code @usuario} (copiar), tu
 * enlace (copiar y compartir como texto con ACTION_SEND) y el QR del enlace, oscuro sobre claro también en tema
 * oscuro ({@code bb_qr_foreground} / {@code bb_qr_background}). Se abre desde Focus y desde Amigos; al abrirse,
 * {@link InviteViewModel} llama a {@code InviteRepository.ensureMyCode()} (estado de carga mientras tanto).
 */
@AndroidEntryPoint
public class InviteSheet extends BottomSheetDialogFragment {

    public static final String TAG = "invite_sheet";

    private InviteViewModel viewModel;
    private ImageView qr;
    private View loading;
    private View error;
    private TextView handleView;
    private TextView linkView;
    private View shareButton;
    @Nullable private String link;
    @Nullable private String handle;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_invite, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Edge-to-edge (F0-u2): solo la barra de navegación.
        InsetsHelper.apply(view, InsetsHelper.BOTTOM);

        viewModel = new ViewModelProvider(this).get(InviteViewModel.class);
        qr = view.findViewById(R.id.inviteQr);
        loading = view.findViewById(R.id.inviteLoading);
        error = view.findViewById(R.id.inviteError);
        handleView = view.findViewById(R.id.inviteHandle);
        linkView = view.findViewById(R.id.inviteLink);
        shareButton = view.findViewById(R.id.inviteShareButton);

        view.findViewById(R.id.inviteRetryButton).setOnClickListener(v -> viewModel.load());
        view.findViewById(R.id.inviteCopyHandle).setOnClickListener(v -> {
            if (handle != null) {
                copy("@" + handle);
            }
        });
        view.findViewById(R.id.inviteCopyLink).setOnClickListener(v -> {
            if (link != null) {
                copy(link);
            }
        });
        shareButton.setOnClickListener(v -> share());

        viewModel.getHandle().observe(getViewLifecycleOwner(), h -> {
            handle = h;
            handleView.setText(h == null ? "" : "@" + h);
        });
        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
    }

    private void render(@Nullable InviteViewModel.State state) {
        if (state == null) {
            return;
        }
        boolean ready = state.status == InviteViewModel.State.Status.READY && state.qr != null;
        link = ready ? state.link : null;
        loading.setVisibility(state.status == InviteViewModel.State.Status.LOADING ? View.VISIBLE : View.GONE);
        error.setVisibility(state.status == InviteViewModel.State.Status.ERROR ? View.VISIBLE : View.GONE);
        qr.setVisibility(ready ? View.VISIBLE : View.INVISIBLE);
        shareButton.setEnabled(ready);
        linkView.setText(link == null ? "" : link);
        if (ready) {
            qr.setImageDrawable(qrDrawable(state.qr));
        } else {
            qr.setImageDrawable(null);
        }
    }

    /** Un píxel por módulo, escalado sin filtrar para que los bordes queden nítidos. */
    @NonNull
    private BitmapDrawable qrDrawable(@NonNull QrMatrix matrix) {
        int fg = ContextCompat.getColor(requireContext(), R.color.bb_qr_foreground);
        int bg = ContextCompat.getColor(requireContext(), R.color.bb_qr_background);
        int n = matrix.size();
        Bitmap bitmap = Bitmap.createBitmap(matrix.toPixels(fg, bg), n, n, Bitmap.Config.ARGB_8888);
        BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);
        drawable.setFilterBitmap(false);
        return drawable;
    }

    private void copy(@NonNull String text) {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("BBetter", text));
            Toast.makeText(requireContext().getApplicationContext(), R.string.invite_copied,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void share() {
        if (link == null) {
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, getString(R.string.invite_share_text, link));
        startActivity(Intent.createChooser(send, getString(R.string.invite_share_chooser)));
    }

    @Override
    public void onStart() {
        super.onStart();
        // Abierta ya del todo: sin estado colapsado, el QR y los botones quedan a la vista.
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetBehavior<FrameLayout> behavior = ((BottomSheetDialog) getDialog()).getBehavior();
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }
}
