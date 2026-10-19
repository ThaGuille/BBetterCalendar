package com.example.bbettercalendar.social.account;

import static org.junit.Assert.assertEquals;

import com.example.bbettercalendar.social.account.SocialEntryRouter.Step;

import org.junit.Test;

public class SocialEntryRouterTest {

    private static final int NOW = 2026;
    private static final int TERMS = 2;

    @Test
    public void noYearGoesToAgeGate() {
        assertEquals(Step.AGE_GATE,
                SocialEntryRouter.route(null, NOW, AccountState.SIGNED_IN, 2, TERMS));
    }

    @Test
    public void underageYearGoesToUnderage() {
        assertEquals(Step.UNDERAGE,
                SocialEntryRouter.route(2012, NOW, AccountState.SIGNED_OUT, null, TERMS));
    }

    @Test
    public void underageIsRecomputedEveryCall() {
        assertEquals(Step.UNDERAGE,
                SocialEntryRouter.route(2012, 2026, AccountState.SIGNED_OUT, null, TERMS));
        assertEquals(Step.JOIN,
                SocialEntryRouter.route(2012, 2027, AccountState.SIGNED_OUT, null, TERMS));
    }

    @Test
    public void signedOutAdultGoesToJoin() {
        assertEquals(Step.JOIN,
                SocialEntryRouter.route(2000, NOW, AccountState.SIGNED_OUT, null, TERMS));
    }

    @Test
    public void signedInWithoutProfileGoesToCreateProfile() {
        assertEquals(Step.CREATE_PROFILE,
                SocialEntryRouter.route(2000, NOW, AccountState.SIGNED_IN, null, TERMS));
    }

    @Test
    public void oldTermsVersionGoesToAcceptTerms() {
        assertEquals(Step.ACCEPT_TERMS,
                SocialEntryRouter.route(2000, NOW, AccountState.SIGNED_IN, 1, TERMS));
    }

    @Test
    public void currentTermsGoesToDestination() {
        assertEquals(Step.DESTINATION,
                SocialEntryRouter.route(2000, NOW, AccountState.SIGNED_IN, TERMS, TERMS));
    }

    @Test
    public void newerTermsThanCurrentGoesToDestination() {
        assertEquals(Step.DESTINATION,
                SocialEntryRouter.route(2000, NOW, AccountState.SIGNED_IN, TERMS + 1, TERMS));
    }
}
