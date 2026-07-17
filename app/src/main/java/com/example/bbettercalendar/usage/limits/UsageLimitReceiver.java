package com.example.bbettercalendar.usage.limits;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.bbettercalendar.database.IoExecutor;

import java.util.concurrent.ExecutorService;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class UsageLimitReceiver extends BroadcastReceiver {

    private static final String TAG = "UsageLimitReceiver";

    @Inject UsageLimitChecker checker;
    @Inject UsageLimitScheduler scheduler;
    @Inject @IoExecutor ExecutorService IO;

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
