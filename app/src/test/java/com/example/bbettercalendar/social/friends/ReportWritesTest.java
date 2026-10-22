package com.example.bbettercalendar.social.friends;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.firebase.firestore.FieldValue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

/** F2-u5: {@link ReportWrites} tiene exactamente las claves y el id de {@code validReportCreate} (F2-u3). */
public class ReportWritesTest {

    @Test
    public void report_hasExactlyTheRuleKeys_andWireValues() {
        Map<String, Object> m = ReportWrites.report("uidA", "uidB", "bob", ReportReason.HARASSMENT,
                ReportReason.Context.REQUEST);
        assertEquals(new HashSet<>(Arrays.asList("reporter", "reported", "reportedHandle", "reason", "context",
                "createdAt")), m.keySet());
        assertEquals("uidA", m.get("reporter"));
        assertEquals("uidB", m.get("reported"));
        assertEquals("bob", m.get("reportedHandle"));
        assertEquals("harassment", m.get("reason"));
        assertEquals("request", m.get("context"));
        assertTrue(m.get("createdAt") instanceof FieldValue);
    }

    @Test
    public void reportId_isReporterUnderscoreReported() {
        assertEquals("uidA_uidB", ReportWrites.reportId("uidA", "uidB"));
        assertEquals("reports/uidA_uidB", ReportWrites.reportPath("uidA", "uidB"));
    }

    @Test
    public void reasonsAndContexts_matchTheRuleEnums() {
        assertEquals(Arrays.asList("offensive_name", "impersonation", "harassment", "other"),
                Arrays.asList(ReportReason.OFFENSIVE_NAME.wire, ReportReason.IMPERSONATION.wire,
                        ReportReason.HARASSMENT.wire, ReportReason.OTHER.wire));
        assertEquals(4, ReportReason.values().length);
        assertEquals("friend", ReportReason.Context.FRIEND.wire);
        assertEquals("request", ReportReason.Context.REQUEST.wire);
    }
}
