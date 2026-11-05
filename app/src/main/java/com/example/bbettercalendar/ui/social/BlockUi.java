package com.example.bbettercalendar.ui.social;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.social.friends.FriendCard;
import com.example.bbettercalendar.social.friends.ReportReason;

import java.util.function.Consumer;

/**
 * L-u10: la confirmacion de Bloquear, la misma en el perfil de amigo y en las solicitudes. L-review F5: con
 * «Reportar también» marcado se elige un motivo y quien confirma recibe ese motivo; el reporte va ANTES de
 * bloquear porque, ya bloqueado, las reglas lo deniegan.
 */
final class BlockUi {

    private BlockUi() {
    }

    /** Explica los efectos y solo bloquea si se confirma; el motivo es null si no se reporta. */
    static void confirm(@NonNull Context context, @NonNull FriendCard person,
                        @NonNull Consumer<ReportReason> onConfirm) {
        View extras = LayoutInflater.from(context).inflate(R.layout.dialog_block_confirm, null);
        CheckBox also = extras.findViewById(R.id.blockAlsoReport);
        RadioGroup reasons = extras.findViewById(R.id.blockReportReasons);
        AlertDialog dialog = new AlertDialog.Builder(context, R.style.RoundedDialog)
                .setTitle(context.getString(R.string.block_confirm_title, person.displayName))
                .setMessage(context.getString(R.string.block_confirm_message, person.displayName))
                .setView(extras)
                .setNegativeButton(R.string.block_cancel, null)
                .setPositiveButton(R.string.block_confirm_action, (d, which) ->
                        onConfirm.accept(also.isChecked() ? selected(reasons) : null))
                .create();
        Runnable refresh = () -> {
            Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (ok != null) {
                ok.setEnabled(!also.isChecked() || selected(reasons) != null);
            }
        };
        also.setOnCheckedChangeListener((b, checked) -> {
            reasons.setVisibility(checked ? View.VISIBLE : View.GONE);
            refresh.run();
        });
        reasons.setOnCheckedChangeListener((g, id) -> refresh.run());
        dialog.show();
    }

    @Nullable
    private static ReportReason selected(@NonNull RadioGroup reasons) {
        int id = reasons.getCheckedRadioButtonId();
        if (id == R.id.blockReasonOffensive) {
            return ReportReason.OFFENSIVE_NAME;
        } else if (id == R.id.blockReasonImpersonation) {
            return ReportReason.IMPERSONATION;
        } else if (id == R.id.blockReasonHarassment) {
            return ReportReason.HARASSMENT;
        } else if (id == R.id.blockReasonOther) {
            return ReportReason.OTHER;
        }
        return null;
    }
}
