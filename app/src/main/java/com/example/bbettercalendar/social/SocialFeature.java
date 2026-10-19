package com.example.bbettercalendar.social;

import android.view.View;

import androidx.annotation.Nullable;

import com.example.bbettercalendar.BuildConfig;

/**
 * Single switch for the social blocks (u11). Backed by {@code BuildConfig.FEATURE_SOCIAL_MOCK}:
 * true in debug, false in release (app/build.gradle).
 *
 * <p>Screens call {@link #applyVisibility(View...)} on the ROOT of each social block (the whole
 * card/row, not its children) so that with the flag off the block is {@link View#GONE} and leaves
 * no gap.
 */
public final class SocialFeature {

    private SocialFeature() { }

    public static boolean isEnabled() {
        return BuildConfig.FEATURE_SOCIAL_MOCK;
    }

    /**
     * F1: whether real accounts (age gate, sign-in, profile) are on. Backed by
     * {@code BuildConfig.FEATURE_SOCIAL}: true in debug, false in release. Independent of
     * {@link #isEnabled()}, which keeps gating the F4 mock blocks.
     */
    public static boolean isAccountEnabled() {
        return BuildConfig.FEATURE_SOCIAL;
    }

    /** VISIBLE when the feature is on, GONE otherwise. Null views are skipped. */
    public static void applyVisibility(@Nullable View... blocks) {
        if (blocks == null) return;
        int visibility = isEnabled() ? View.VISIBLE : View.GONE;
        for (View v : blocks) {
            if (v != null) v.setVisibility(visibility);
        }
    }
}
