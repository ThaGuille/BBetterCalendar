package com.example.bbettercalendar.ui.progress;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.bbettercalendar.R;

// Diálogo para fijar/borrar el límite diario de una app seguida (Phase 3, warn-only). Se abre al
// tocar una fila de la lista de uso; reutiliza el ProgressViewModel del Fragment padre (mismo
// patrón de "diálogo hijo comparte VM del host" — evita un listener frágil entre configuraciones).
public class AppLimitDialog extends DialogFragment {

    private static final String ARG_PACKAGE = "package";
    private static final String ARG_LABEL = "label";
    private static final String ARG_CURRENT_MINUTES = "current_minutes";

    public static AppLimitDialog newInstance(String packageName, String label, int currentLimitMinutes) {
        Bundle args = new Bundle();
        args.putString(ARG_PACKAGE, packageName);
        args.putString(ARG_LABEL, label);
        args.putInt(ARG_CURRENT_MINUTES, currentLimitMinutes);
        AppLimitDialog dialog = new AppLimitDialog();
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = requireArguments();
        String packageName = args.getString(ARG_PACKAGE);
        String label = args.getString(ARG_LABEL);
        int currentMinutes = args.getInt(ARG_CURRENT_MINUTES);

        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity(), R.style.RoundedDialog);
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_app_limit, null);
        builder.setView(view);

        ((TextView) view.findViewById(R.id.app_limit_dialog_app_label)).setText(label);
        EditText minutesInput = view.findViewById(R.id.app_limit_dialog_minutes);
        if (currentMinutes > 0) {
            minutesInput.setText(String.valueOf(currentMinutes));
        }

        ProgressViewModel viewModel = new ViewModelProvider(requireParentFragment()).get(ProgressViewModel.class);
        Dialog dialog = builder.create();

        view.findViewById(R.id.app_limit_dialog_cancel).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.app_limit_dialog_clear).setOnClickListener(v -> {
            viewModel.setDailyLimit(packageName, 0);
            dismiss();
        });
        view.findViewById(R.id.app_limit_dialog_save).setOnClickListener(v -> {
            int minutes = parseMinutes(minutesInput.getText().toString());
            if (minutes <= 0) {
                minutesInput.setError(getString(R.string.app_limit_dialog_invalid_minutes));
                return;
            }
            viewModel.setDailyLimit(packageName, minutes);
            dismiss();
        });

        return dialog;
    }

    private static int parseMinutes(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
