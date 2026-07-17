package com.example.bbettercalendar.notifications.event;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Reusable alarm-scheduling core (spec reminder-generalization / roadmap finding F7): the
 * schedule/cancel/buildPendingIntent/exact-alarm-with-inexact-fallback mechanics that used to
 * live as private methods on {@link EventReminderScheduler}, extracted so a future reminder
 * flavor (e.g. a project-deadline reminder — not built here, see the Phase 5 spec) can reuse
 * them with a disjoint {@link PendingIntent} request-code namespace instead of colliding with
 * whatever id space is already using {@code PendingIntent.getBroadcast()}.
 *
 * Not itself Hilt-provided: each client (e.g. {@link EventReminderScheduler}) constructs its own
 * instance in its own {@code @Inject} constructor, passing its receiver class and namespace —
 * this class has no state worth sharing across flavors.
 */
public class AlarmReminderCore {

    private static final String TAG = "AlarmReminderCore";

    /** Maps (entityId, offsetIndex) to a PendingIntent request code unique to one reminder flavor. */
    public interface RequestCodeNamespace {
        int requestCodeFor(int entityId, int offsetIndex);
    }

    /** Stamps flavor-specific extras onto the alarm's Intent; the core doesn't know key names. */
    public interface IntentPopulator {
        void populate(Intent intent, int entityId, int offsetIndex);
    }

    private final Context appContext;
    private final AlarmManager alarmManager;
    private final Class<? extends BroadcastReceiver> receiverClass;
    private final RequestCodeNamespace namespace;

    public AlarmReminderCore(Context context, AlarmManager alarmManager,
                              Class<? extends BroadcastReceiver> receiverClass,
                              RequestCodeNamespace namespace) {
        this.appContext = context.getApplicationContext();
        this.alarmManager = alarmManager;
        this.receiverClass = receiverClass;
        this.namespace = namespace;
    }

    /**
     * Schedules one exact alarm per enabled offset whose trigger time (anchorMillis - offsetMillis[i])
     * is still in the future. {@code enabledOffsets} and {@code offsetMillis} are walked in lockstep,
     * bounded by the shorter of the two — same guard as the original entry-reminder loop.
     */
    public void schedule(int entityId, boolean[] enabledOffsets, long[] offsetMillis, long anchorMillis,
                          IntentPopulator extras) {
        if (enabledOffsets == null || offsetMillis == null) return;
        long now = System.currentTimeMillis();
        int count = Math.min(enabledOffsets.length, offsetMillis.length);
        for (int i = 0; i < count; i++) {
            if (!enabledOffsets[i]) continue;
            long triggerAt = anchorMillis - offsetMillis[i];
            if (triggerAt <= now) continue;
            scheduleOne(entityId, i, triggerAt, extras);
        }
    }

    /** Cancels any previously scheduled alarms for this entity across all {@code offsetCount} slots. */
    public void cancel(int entityId, int offsetCount, IntentPopulator extras) {
        for (int i = 0; i < offsetCount; i++) {
            PendingIntent pi = buildPendingIntent(entityId, i, extras, PendingIntent.FLAG_NO_CREATE);
            if (pi != null) {
                alarmManager.cancel(pi);
                pi.cancel();
            }
        }
    }

    private void scheduleOne(int entityId, int offsetIndex, long triggerAt, IntentPopulator extras) {
        PendingIntent pi = buildPendingIntent(entityId, offsetIndex, extras, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        } catch (SecurityException e) {
            // Device denied exact alarms — fall back to inexact so the reminder still fires.
            Log.w(TAG, "Exact alarm denied, falling back to inexact", e);
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }

    private PendingIntent buildPendingIntent(int entityId, int offsetIndex, IntentPopulator extras, int extraFlags) {
        Intent intent = new Intent(appContext, receiverClass);
        if (extras != null) {
            extras.populate(intent, entityId, offsetIndex);
        }
        int requestCode = namespace.requestCodeFor(entityId, offsetIndex);
        int flags = PendingIntent.FLAG_IMMUTABLE | extraFlags;
        return PendingIntent.getBroadcast(appContext, requestCode, intent, flags);
    }
}
