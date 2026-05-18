package com.example.bbettercalendar.feedback;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;

/**
 * Tiny SoundPool wrapper for UI feedback sounds.
 *
 * Drop .ogg files into res/raw/ matching the constants below
 * (tap, success, start, stop) and uncomment the load() calls in
 * init() to enable audio. Until then, every play*() is a no-op
 * so the rest of the app behaves identically.
 */
public final class SoundFeedback {

    private static final String TAG = "SoundFeedback";
    private static final int MAX_STREAMS = 4;
    private static final int NOT_LOADED = -1;

    private static SoundFeedback instance;

    private final SoundPool pool;
    private int tapId = NOT_LOADED;
    private int successId = NOT_LOADED;
    private int startId = NOT_LOADED;
    private int stopId = NOT_LOADED;

    private SoundFeedback(Context context) {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        this.pool = new SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(attrs)
                .build();
        // To activate, drop matching files into res/raw/ and uncomment:
        // tapId     = pool.load(context, R.raw.tap, 1);
        // successId = pool.load(context, R.raw.success, 1);
        // startId   = pool.load(context, R.raw.start, 1);
        // stopId    = pool.load(context, R.raw.stop, 1);
    }

    public static synchronized SoundFeedback get(Context context) {
        if (instance == null) {
            instance = new SoundFeedback(context.getApplicationContext());
        }
        return instance;
    }

    public void playTap() { play(tapId); }
    public void playSuccess() { play(successId); }
    public void playStart() { play(startId); }
    public void playStop() { play(stopId); }

    private void play(int id) {
        if (id == NOT_LOADED) return;
        try {
            pool.play(id, 1f, 1f, 1, 0, 1f);
        } catch (Exception e) {
            Log.w(TAG, "SoundPool play failed", e);
        }
    }
}
