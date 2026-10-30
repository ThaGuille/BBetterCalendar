package com.example.bbettercalendar.social;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Bound when the social feature is off (release builds): always {@link SocialSnapshot#EMPTY}.
 * Exists so screens can inject {@link SocialRepository} unconditionally.
 */
public class EmptySocialRepository implements SocialRepository {

    private final MutableLiveData<SocialSnapshot> snapshot =
            new MutableLiveData<>(SocialSnapshot.EMPTY);

    @NonNull
    @Override
    public LiveData<SocialSnapshot> getSnapshot() {
        return snapshot;
    }
}
