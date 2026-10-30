package com.example.bbettercalendar.social;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

/**
 * Source of the social blocks (friends studying, crown, user rank). Bound by {@link SocialModule}:
 * {@link FirebaseSocialRepository} when {@link SocialFeature#isAccountEnabled()}, else
 * {@link MockSocialRepository} when {@link SocialFeature#isEnabled()}, else
 * {@link EmptySocialRepository} — so it is always injectable and screens never null-check it.
 * Screens still hide every social block with {@link SocialFeature#applyVisibility} when the flag
 * is off; an empty snapshot is not the "off" signal.
 *
 * <p>F4-u4 (03 §10): there is ONE snapshot. Focus, Home, Cycle complete and Amigos all render the
 * same value; the per-design accessors of the mock are gone.
 */
public interface SocialRepository {

    /**
     * Never null and never holds null: {@link SocialSnapshot#EMPTY} when there is no data (no
     * session, no friends yet). The real repository listens to Firebase only while this is observed.
     */
    @NonNull
    LiveData<SocialSnapshot> getSnapshot();
}
