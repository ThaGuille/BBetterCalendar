package com.example.bbettercalendar.social;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

import javax.inject.Singleton;

/**
 * Binds {@link SocialRepository}: the mock in debug (flag on), an always-empty one in release.
 * When a real backend exists, swap the mock branch here; screens don't change.
 */
@Module
@InstallIn(SingletonComponent.class)
public class SocialModule {

    @Provides
    @Singleton
    public static SocialRepository provideSocialRepository() {
        return SocialFeature.isEnabled() ? new MockSocialRepository() : new EmptySocialRepository();
    }
}
