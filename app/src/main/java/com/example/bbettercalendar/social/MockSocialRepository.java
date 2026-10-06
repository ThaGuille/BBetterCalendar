package com.example.bbettercalendar.social;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Arrays;

/**
 * Fake social data for the designed screens (debug builds only, BuildConfig.FEATURE_SOCIAL_MOCK).
 * Names, times and rank come from the Stitch mocks: html 01 "Studying now 5 / 8" grid (Laia has
 * the crown), html 02 "Best · 2nd among friends" / "Laia and 3 others still studying". html 04's
 * "3 friends studying now · Maya, Alex, Leo" is NOT reproduced literally: one consistent data set
 * is used everywhere, so Home shows the same 5 studying friends as Focus.
 *
 * Static data — no clock, no I/O — so it is safe on any thread and deterministic in tests.
 */
// PLACEHOLDER(ui-placeholders#social-backend)
public class MockSocialRepository implements SocialRepository {

    // PLACEHOLDER(ui-placeholders#social-rank) — fixed, not computed from the user's real minutes.
    static final SocialRank MOCK_RANK = new SocialRank(2, 9);

    private final MutableLiveData<SocialSnapshot> snapshot =
            new MutableLiveData<>(buildSnapshot());

    @NonNull
    @Override
    public LiveData<SocialSnapshot> getSnapshot() {
        return snapshot;
    }

    @NonNull
    static SocialSnapshot buildSnapshot() {
        return new SocialSnapshot(Arrays.asList(
                friend("laia", "Laia", "LM", Friend.Status.STUDYING, 3, 12, 5),
                friend("marc", "Marc", "MS", Friend.Status.STUDYING, 1, 48, 30),
                friend("pau", "Pau", "PK", Friend.Status.STUDYING, 2, 5, 44),
                friend("julia", "Júlia", "JB", Friend.Status.STUDYING, 1, 10, 2),
                friend("noa", "Noa", "NJ", Friend.Status.STUDYING, 0, 52, 10),
                friend("oriol", "Oriol", "OG", Friend.Status.AWAY, 1, 25, 0),
                friend("elena", "Elena", "ET", Friend.Status.AWAY, 0, 40, 0),
                friend("alex", "Alex", "AR", Friend.Status.AWAY, 0, 0, 0)
        ), MOCK_RANK);
    }

    private static Friend friend(String id, String name, String initials, Friend.Status status,
                                 int h, int m, int s) {
        return new Friend(id, name, initials, status, h * 3600L + m * 60L + s);
    }
}
