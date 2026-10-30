package com.example.bbettercalendar.social;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.bbettercalendar.social.presence.Presence;

import java.util.Arrays;

/**
 * Fake social data (BuildConfig.FEATURE_SOCIAL_MOCK without FEATURE_SOCIAL, and tests). Since F4-u4
 * it holds ONE snapshot like the real repository: the Focus design (html 01) "Studying now 5 / 8",
 * Laia with the crown. Each studying friend's session started {@code focusSecondsToday} before the
 * moment the snapshot was built, so the longest session comes first.
 *
 * <p>No I/O; the only clock is the {@code now} the snapshot is built with.
 */
public class MockSocialRepository implements SocialRepository {

    /** The mock's crown (html 01). */
    static final String CROWN_UID = "laia";

    private final MutableLiveData<SocialSnapshot> snapshot;

    public MockSocialRepository() {
        this(System.currentTimeMillis());
    }

    MockSocialRepository(long nowMs) {
        snapshot = new MutableLiveData<>(buildSnapshot(nowMs));
    }

    @NonNull
    @Override
    public LiveData<SocialSnapshot> getSnapshot() {
        return snapshot;
    }

    @NonNull
    static SocialSnapshot buildSnapshot(long nowMs) {
        return new SocialSnapshot(Arrays.asList(
                friend(nowMs, "laia", "Laia", 0, true, 3, 12, 5),
                friend(nowMs, "marc", "Marc", 1, true, 1, 48, 30),
                friend(nowMs, "pau", "Pau", 2, true, 2, 5, 44),
                friend(nowMs, "julia", "Júlia", 3, true, 1, 10, 2),
                friend(nowMs, "noa", "Noa", 4, true, 0, 52, 10),
                friend(nowMs, "oriol", "Oriol", 5, false, 1, 25, 0),
                friend(nowMs, "elena", "Elena", 6, false, 0, 40, 0),
                friend(nowMs, "alex", "Alex", 7, false, 0, 0, 0)
        ), null, CROWN_UID);
    }

    private static Friend friend(long nowMs, String id, String name, int color, boolean studying,
                                 int h, int m, int s) {
        long seconds = h * 3600L + m * 60L + s;
        Presence presence = studying ? Presence.studying(nowMs - seconds * 1000L, nowMs) : Presence.AWAY;
        return new Friend(id, id, name, color, presence, 0L, seconds);
    }
}
