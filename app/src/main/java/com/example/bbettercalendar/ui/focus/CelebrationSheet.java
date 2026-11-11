package com.example.bbettercalendar.ui.focus;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.ui.common.InsetsHelper;
import com.example.bbettercalendar.ui.common.widget.CelebrationView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * mejora-visual u13: the brief bottom-sheet message with a {@link CelebrationView}, shown when the weekly
 * goal is reached and when a project is completed. It is only a view: whether it may appear is decided
 * by {@link CelebrationOncePolicy} / {@link CelebrationPrefs} (once per ISO week, once per project id).
 */
public class CelebrationSheet extends BottomSheetDialogFragment {

    public static final String TAG = "celebration_sheet";
    private static final String ARG_TITLE = "title";
    private static final String ARG_MESSAGE = "message";

    @NonNull
    public static CelebrationSheet newInstance(@NonNull String title, @NonNull String message) {
        CelebrationSheet sheet = new CelebrationSheet();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_MESSAGE, message);
        sheet.setArguments(args);
        return sheet;
    }

    /** Shows the sheet unless one is already up. */
    public static void show(@NonNull FragmentManager fm, @NonNull String title, @NonNull String message) {
        if (fm.findFragmentByTag(TAG) == null && !fm.isStateSaved()) {
            newInstance(title, message).show(fm, TAG);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_celebration, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsHelper.apply(view, InsetsHelper.BOTTOM);
        Bundle args = requireArguments();
        ((TextView) view.findViewById(R.id.celebrationSheetTitle)).setText(args.getString(ARG_TITLE));
        ((TextView) view.findViewById(R.id.celebrationSheetMessage)).setText(args.getString(ARG_MESSAGE));
        view.findViewById(R.id.celebrationSheetButton).setOnClickListener(v -> dismiss());
        CelebrationView celebration = view.findViewById(R.id.celebrationSheetMark);
        if (savedInstanceState == null) {
            celebration.play();
        } else {
            celebration.showComplete();
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
