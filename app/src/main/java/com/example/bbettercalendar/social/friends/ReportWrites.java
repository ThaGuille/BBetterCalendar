package com.example.bbettercalendar.social.friends;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Map;

/**
 * El mapa y el id de {@code reports/{reporter_reported}} (F2-u5, D-028 §4). Exactamente las claves de
 * {@code validReportCreate} en las reglas de F2-u3; {@code ReportWritesTest} lo fija. Un reporte por
 * pareja y solo crear: un segundo reporte sería un update y las reglas lo deniegan. Puro.
 */
public final class ReportWrites {

    public static final String REPORTS = "reports";

    public static final String REPORTER = "reporter";
    public static final String REPORTED = "reported";
    public static final String REPORTED_HANDLE = "reportedHandle";
    public static final String REASON = "reason";
    public static final String CONTEXT = "context";
    public static final String CREATED_AT = "createdAt";

    private ReportWrites() {
    }

    /** Id determinista: {@code reporter_reported}. */
    @NonNull
    public static String reportId(@NonNull String reporter, @NonNull String reported) {
        return reporter + "_" + reported;
    }

    @NonNull
    public static String reportPath(@NonNull String reporter, @NonNull String reported) {
        return REPORTS + "/" + reportId(reporter, reported);
    }

    /** {@code reports/{reporter_reported}} al crearlo; {@code createdAt} con la hora del servidor. */
    @NonNull
    public static Map<String, Object> report(@NonNull String reporter, @NonNull String reported,
                                             @NonNull String reportedHandle, @NonNull ReportReason reason,
                                             @NonNull ReportReason.Context context) {
        Map<String, Object> m = new HashMap<>();
        m.put(REPORTER, reporter);
        m.put(REPORTED, reported);
        m.put(REPORTED_HANDLE, reportedHandle);
        m.put(REASON, reason.wire);
        m.put(CONTEXT, context.wire);
        m.put(CREATED_AT, FieldValue.serverTimestamp());
        return m;
    }
}
