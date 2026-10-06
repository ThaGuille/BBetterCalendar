package com.example.bbettercalendar.social;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

/**
 * Source of the social blocks (friends studying, user rank). Bound by {@link SocialModule}:
 * {@link MockSocialRepository} when {@link SocialFeature#isEnabled()}, otherwise
 * {@link EmptySocialRepository} — so it is always injectable and screens never null-check it.
 * Screens still hide every social block with {@link SocialFeature#applyVisibility} when the flag
 * is off; an empty snapshot is not the "off" signal.
 *
 * <p>There is no backend yet: see .claude/docs/systems/ui-placeholders.md (social rows).
 */
public interface SocialRepository {

    /** Never null and never holds null: {@link SocialSnapshot#EMPTY} when there is no data. */
    @NonNull
    LiveData<SocialSnapshot> getSnapshot();
}
