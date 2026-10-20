package com.example.bbettercalendar.ui.home;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.social.SocialFeature;
import com.example.bbettercalendar.ui.common.InsetsHelper;
import com.example.bbettercalendar.ui.social.SocialEntryViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * F1-u8: editor del nombre local, que se abre al tocar el avatar de Home sin cuenta. Guarda el
 * nombre en {@code SocialPrefs} (vía {@link HomeViewModel}) y funciona también en release. En debug
 * ({@link SocialFeature#isAccountEnabled()}) ofrece además «Únete a tus amigos», que sigue al router
 * a través del {@link SocialEntryViewModel} de Home.
 *
 * <p>Se muestra con el childFragmentManager de {@link HomeFragment}: los ViewModels se piden al
 * Fragment padre para compartir instancia con Home.
 */
@AndroidEntryPoint
public class LocalNameDialog extends BottomSheetDialogFragment {

    public static final String TAG = "LocalNameDialog";
    private static final String ARG_CURRENT = "current";

    @NonNull
    public static LocalNameDialog newInstance(@Nullable String currentName) {
        LocalNameDialog dialog = new LocalNameDialog();
        Bundle args = new Bundle();
        args.putString(ARG_CURRENT, currentName);
        dialog.setArguments(args);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_local_name, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsHelper.apply(view, InsetsHelper.BOTTOM);

        HomeViewModel home = new ViewModelProvider(requireParentFragment()).get(HomeViewModel.class);
        EditText input = view.findViewById(R.id.localNameInput);
        if (savedInstanceState == null && getArguments() != null) {
            String current = getArguments().getString(ARG_CURRENT);
            if (!TextUtils.isEmpty(current)) {
                input.setText(current);
                input.setSelection(current.length());
            }
        }

        View save = view.findViewById(R.id.localNameSave);
        Runnable commit = () -> {
            home.saveLocalName(input.getText().toString());
            dismiss();
        };
        save.setOnClickListener(v -> commit.run());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commit.run();
                return true;
            }
            return false;
        });

        // ADJUST_RESIZE: sin esto el teclado tapa los botones de la hoja (patrón de QuickAddTaskSheet).
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        View join = view.findViewById(R.id.localNameJoin);
        if (SocialFeature.isAccountEnabled()) {
            join.setVisibility(View.VISIBLE);
            join.setOnClickListener(v -> {
                SocialEntryViewModel entry =
                        new ViewModelProvider(requireParentFragment()).get(SocialEntryViewModel.class);
                dismiss();
                entry.requestEntry();
            });
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetBehavior<FrameLayout> behavior = ((BottomSheetDialog) getDialog()).getBehavior();
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }
}
