package com.example.bbettercalendar.social;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * Binds {@link SocialRepository} (F4-u4, 03 §10): {@link FirebaseSocialRepository} with real
 * accounts ({@code FEATURE_SOCIAL}, it has priority over the mock), else the mock with
 * {@code FEATURE_SOCIAL_MOCK}, else an always-empty one (release). The real one is behind a
 * {@link Provider}, so without accounts nothing touches Firebase. Screens don't change.
 */
@Module
@InstallIn(SingletonComponent.class)
public class SocialModule {

    @Provides
    @Singleton
    public static SocialRepository provideSocialRepository(Provider<FirebaseSocialRepository> real) {
        if (SocialFeature.isAccountEnabled()) {
            return real.get();
        }
        return SocialFeature.isEnabled() ? new MockSocialRepository() : new EmptySocialRepository();
    }
}
