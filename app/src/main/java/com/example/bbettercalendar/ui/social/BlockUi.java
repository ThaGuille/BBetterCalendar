package com.example.bbettercalendar.ui.social;

import android.app.AlertDialog;
import android.content.Context;

import androidx.annotation.NonNull;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.social.friends.FriendCard;

/** L-u10: la confirmacion de Bloquear, la misma en el perfil de amigo y en las solicitudes. */
final class BlockUi {

    private BlockUi() {
    }

    /** Explica los efectos y solo bloquea si se confirma. */
    static void confirm(@NonNull Context context, @NonNull FriendCard person, @NonNull Runnable onConfirm) {
        new AlertDialog.Builder(context, R.style.RoundedDialog)
                .setTitle(context.getString(R.string.block_confirm_title, person.displayName))
                .setMessage(context.getString(R.string.block_confirm_message, person.displayName))
                .setNegativeButton(R.string.block_cancel, null)
                .setPositiveButton(R.string.block_confirm_action, (d, which) -> onConfirm.run())
                .show();
    }
}
