package com.example.bbettercalendar.social;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Arrays;

/**
 * Fake social data for the designed screens (debug builds only, BuildConfig.FEATURE_SOCIAL_MOCK).
 * Each screen shows what its Stitch design shows (u13b), so there is one set per design:
 * <ul>
 *   <li>{@link #getSnapshot()} (Focus, html 01): "Studying now 5 / 8" grid, Laia has the crown.</li>
 *   <li>{@link #getHomeSnapshot()} (Home, html 04): "3 friends studying now · Maya, Alex, Leo".</li>
 *   <li>{@link #getCelebrationSnapshot()} (Cycle complete, html 02): "Laia and 3 others still
 *   studying", "Best · 2nd among friends".</li>
 * </ul>
 *
 * Static data — no clock, no I/O — so it is safe on any thread and deterministic in tests.
 */
// PLACEHOLDER(ui-placeholders#social-backend)
public class MockSocialRepository implements SocialRepository {

    private final MutableLiveData<SocialSnapshot> snapshot =
            new MutableLiveData<>(buildSnapshot());
    private final MutableLiveData<SocialSnapshot> homeSnapshot =
            new MutableLiveData<>(buildHomeSnapshot());
    private final MutableLiveData<SocialSnapshot> celebrationSnapshot =
            new MutableLiveData<>(buildCelebrationSnapshot());

    @NonNull
    @Override
    public LiveData<SocialSnapshot> getSnapshot() {
        return snapshot;
    }

    @NonNull
    @Override
    public LiveData<SocialSnapshot> getHomeSnapshot() {
        return homeSnapshot;
    }

    @NonNull
    @Override
    public LiveData<SocialSnapshot> getCelebrationSnapshot() {
        return celebrationSnapshot;
    }

    /** html 04: three friends studying, shown as avatars M, A, L and "Maya, Alex, Leo". */
    @NonNull
    static SocialSnapshot buildHomeSnapshot() {
        return new SocialSnapshot(Arrays.asList(
                friend("maya", "Maya", "MA", Friend.Status.STUDYING, 1, 30, 0),
                friend("alex_h", "Alex", "AL", Friend.Status.STUDYING, 1, 10, 0),
                friend("leo", "Leo", "LE", Friend.Status.STUDYING, 0, 45, 0)
        ), null);
    }

    /** html 02: Laia plus 3 others studying (avatars L, M, J), the user 2nd among friends. */
    @NonNull
    static SocialSnapshot buildCelebrationSnapshot() {
        return new SocialSnapshot(Arrays.asList(
                friend("laia", "Laia", "LM", Friend.Status.STUDYING, 3, 12, 5),
                friend("marc", "Marc", "MS", Friend.Status.STUDYING, 1, 48, 30),
                friend("julia", "Júlia", "JB", Friend.Status.STUDYING, 1, 10, 2),
                friend("noa", "Noa", "NJ", Friend.Status.STUDYING, 0, 52, 10),
                friend("pau", "Pau", "PK", Friend.Status.AWAY, 2, 5, 44),
                friend("oriol", "Oriol", "OG", Friend.Status.AWAY, 1, 25, 0),
                friend("elena", "Elena", "ET", Friend.Status.AWAY, 0, 40, 0),
                friend("alex", "Alex", "AR", Friend.Status.AWAY, 0, 0, 0)
        ), null);
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
        ), null);
    }

    private static Friend friend(String id, String name, String initials, Friend.Status status,
                                 int h, int m, int s) {
        return new Friend(id, name, initials, status, h * 3600L + m * 60L + s);
    }
}
