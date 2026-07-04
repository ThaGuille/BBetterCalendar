package com.example.bbettercalendar.usage.limits;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class UsageLimitReceiver extends BroadcastReceiver {

    private static final String TAG = "UsageLimitReceiver";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    @Inject UsageLimitChecker checker;
    @Inject UsageLimitScheduler scheduler;

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pendingResult = goAsync();
        IO.execute(() -> {
            try {
                checker.run();
            } catch (Exception e) {
                Log.e(TAG, "onReceive failed", e);
            } finally {
                // Reprograma el siguiente tick (o desarma si ya no quedan límites pendientes hoy).
                scheduler.arm();
                pendingResult.finish();
            }
        });
    }
}
