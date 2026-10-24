package com.example.bbettercalendar.ui.social;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.social.friends.FriendCard;
import com.example.bbettercalendar.social.friends.ReportReason;
import com.example.bbettercalendar.ui.common.InsetsHelper;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * F2-u7: reportar a una persona con uno de cuatro motivos (botones de radio, sin texto libre; 04 §2).
 * Desde la lista de amigos el botón dice «Enviar reporte» y después se ofrece eliminarlo; desde una solicitud
 * dice «Reportar y rechazar solicitud», porque el reporte también la rechaza (mismo lote). Comparte el
 * {@link FriendsViewModel} de {@link FriendsFragment}.
 */
@AndroidEntryPoint
public class ReportSheet extends BottomSheetDialogFragment {

    private static final String ARG_UID = "uid";
    private static final String ARG_HANDLE = "handle";
    private static final String ARG_NAME = "name";
    private static final String ARG_COLOR = "color";
    private static final String ARG_CONTEXT = "context";

    static ReportSheet newInstance(@NonNull FriendCard target, @NonNull ReportReason.Context context) {
        Bundle args = new Bundle();
        args.putString(ARG_UID, target.uid);
        args.putString(ARG_HANDLE, target.handle);
        args.putString(ARG_NAME, target.displayName);
        args.putInt(ARG_COLOR, target.avatarColor);
        args.putString(ARG_CONTEXT, context.name());
        ReportSheet sheet = new ReportSheet();
        sheet.setArguments(args);
        return sheet;
    }

    private FriendsViewModel viewModel;
    private RadioGroup reasons;
    private MaterialButton send;
    private TextView message;
    private FriendCard target;
    private ReportReason.Context context;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_report, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        InsetsHelper.apply(view, InsetsHelper.BOTTOM);
        Bundle args = requireArguments();
        target = new FriendCard(args.getString(ARG_UID, ""), args.getString(ARG_HANDLE, ""),
                args.getString(ARG_NAME, ""), args.getInt(ARG_COLOR));
        context = ReportReason.Context.valueOf(args.getString(ARG_CONTEXT, ReportReason.Context.FRIEND.name()));
        viewModel = new ViewModelProvider(requireParentFragment()).get(FriendsViewModel.class);

        boolean fromRequest = context == ReportReason.Context.REQUEST;
        ((TextView) view.findViewById(R.id.reportTitle)).setText(getString(R.string.report_title, target.displayName));
        ((TextView) view.findViewById(R.id.reportBody)).setText(getString(
                fromRequest ? R.string.report_body_request : R.string.report_body_friend, target.displayName));
        reasons = view.findViewById(R.id.reportReasons);
        message = view.findViewById(R.id.reportMessage);
        send = view.findViewById(R.id.reportSendButton);
        send.setText(fromRequest ? R.string.report_send_request : R.string.report_send_friend);

        reasons.setOnCheckedChangeListener((group, checkedId) -> refresh(false));
        send.setOnClickListener(v -> {
            ReportReason reason = selectedReason();
            if (reason != null) {
                viewModel.report(target, reason, context);
            }
        });
        viewModel.getReportState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }
            boolean sending = state.step == FriendsViewModel.ReportState.Step.SENDING;
            if (state.step == FriendsViewModel.ReportState.Step.DONE) {
                dismissAllowingStateLoss();
                return;
            }
            if (state.step == FriendsViewModel.ReportState.Step.ERROR) {
                message.setText(state.message);
                message.setVisibility(View.VISIBLE);
            } else {
                message.setVisibility(View.GONE);
            }
            refresh(sending);
        });
    }

    @Nullable
    private ReportReason selectedReason() {
        int id = reasons.getCheckedRadioButtonId();
        if (id == R.id.reportReasonOffensive) {
            return ReportReason.OFFENSIVE_NAME;
        } else if (id == R.id.reportReasonImpersonation) {
            return ReportReason.IMPERSONATION;
        } else if (id == R.id.reportReasonHarassment) {
            return ReportReason.HARASSMENT;
        } else if (id == R.id.reportReasonOther) {
            return ReportReason.OTHER;
        }
        return null;
    }

    private void refresh(boolean sending) {
        send.setEnabled(!sending && selectedReason() != null);
        for (int i = 0; i < reasons.getChildCount(); i++) {
            reasons.getChildAt(i).setEnabled(!sending);
        }
    }
}
