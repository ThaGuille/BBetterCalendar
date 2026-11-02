package com.example.bbettercalendar.ui.social;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.social.friends.FriendCard;
import com.example.bbettercalendar.social.interaction.InteractionResult;
import com.example.bbettercalendar.social.interaction.InteractionType;
import com.example.bbettercalendar.social.stats.FriendStats;
import com.example.bbettercalendar.social.stats.RankingState.FriendStatsState;

import org.junit.Test;

import java.time.LocalDate;

/** F5-u8: el mapper puro de animo y empujon del perfil de un amigo. */
public class FriendInteractionUiTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    private static final FriendCard CARD = new FriendCard("u1", "bob", "Bob", 2);

    private static FriendStatsState ready(String dayKey, int minutesToday) {
        FriendStats s = new FriendStats(dayKey, "2026-W45", minutesToday, 100, 3, 4, 5, 9, 1500, null);
        return FriendStatsState.ready("u1", s, CARD);
    }

    private static FriendInteractionUi ui(FriendStatsState state, boolean cheerSent, boolean nudgeSent,
                                          InteractionType type, InteractionResult result) {
        return FriendInteractionUi.from(state, TODAY, cheerSent, nudgeSent, type, result);
    }

    @Test
    public void notStudiedToday_bothButtonsEnabled() {
        FriendInteractionUi ui = ui(ready("2026-11-02", 0), false, false, null, null);
        assertEquals(FriendInteractionUi.Button.ENABLED, ui.cheer);
        assertEquals(FriendInteractionUi.Button.ENABLED, ui.nudge);
        assertFalse(ui.hasMessage());
        assertEquals(R.string.interaction_cheer, ui.cheerText());
        assertEquals(R.string.interaction_nudge, ui.nudgeText());
    }

    @Test
    public void studiedToday_hidesNudgeKeepsCheer() {
        FriendInteractionUi ui = ui(ready("2026-11-02", 25), false, false, null, null);
        assertTrue(ui.cheerVisible());
        assertFalse(ui.nudgeVisible());
    }

    @Test
    public void staleStats_offerNudge() {
        FriendInteractionUi ui = ui(ready("2026-11-01", 40), false, false, null, null);
        assertEquals(FriendInteractionUi.Button.ENABLED, ui.nudge);
    }

    @Test
    public void noStats_cheerAndNudgeOffered() {
        FriendInteractionUi ui = ui(FriendStatsState.noStats("u1", CARD), false, false, null, null);
        assertEquals(FriendInteractionUi.Button.ENABLED, ui.cheer);
        assertEquals(FriendInteractionUi.Button.ENABLED, ui.nudge);
    }

    @Test
    public void loadingErrorOrGone_showNothing() {
        assertFalse(ui(FriendStatsState.loading("u1"), false, false, null, null).cheerVisible());
        assertFalse(ui(null, false, false, null, null).cheerVisible());
        assertFalse(ui(FriendStatsState.notFriend("u1"), false, false, null, null).nudgeVisible());
        assertFalse(ui(FriendStatsState.error("u1", new RuntimeException()), false, false, null, null)
                .cheerVisible());
    }

    @Test
    public void sentTodayFromLog_disablesAndRelabels() {
        FriendInteractionUi ui = ui(ready("2026-11-02", 0), true, true, null, null);
        assertEquals(FriendInteractionUi.Button.SENT_TODAY, ui.cheer);
        assertEquals(FriendInteractionUi.Button.SENT_TODAY, ui.nudge);
        assertEquals(R.string.interaction_cheer_sent, ui.cheerText());
        assertEquals(R.string.interaction_nudge_sent, ui.nudgeText());
    }

    @Test
    public void sentAndAlreadySent_showSentTodayOnlyForThatType() {
        FriendInteractionUi sent = ui(ready("2026-11-02", 0), false, false, InteractionType.CHEER,
                InteractionResult.SENT);
        assertEquals(FriendInteractionUi.Button.SENT_TODAY, sent.cheer);
        assertEquals(FriendInteractionUi.Button.ENABLED, sent.nudge);
        FriendInteractionUi already = ui(ready("2026-11-02", 0), false, false, InteractionType.NUDGE,
                InteractionResult.ALREADY_SENT);
        assertEquals(FriendInteractionUi.Button.SENT_TODAY, already.nudge);
        assertEquals(FriendInteractionUi.Button.ENABLED, already.cheer);
        assertFalse(already.hasMessage());
    }

    @Test
    public void studiedTodayFromServer_hidesNudge() {
        FriendInteractionUi ui = ui(ready("2026-11-01", 0), false, false, InteractionType.NUDGE,
                InteractionResult.STUDIED_TODAY);
        assertFalse(ui.nudgeVisible());
        assertTrue(ui.cheerVisible());
        assertFalse(ui.hasMessage());
    }

    @Test
    public void notFriendsAndError_showShortMessage() {
        FriendInteractionUi nf = ui(ready("2026-11-02", 0), false, false, InteractionType.CHEER,
                InteractionResult.NOT_FRIENDS);
        assertEquals(R.string.interaction_msg_not_friends, nf.message);
        assertEquals(FriendInteractionUi.Button.ENABLED, nf.cheer);
        FriendInteractionUi err = ui(ready("2026-11-02", 0), false, false, InteractionType.NUDGE,
                InteractionResult.ERROR);
        assertEquals(R.string.interaction_msg_error, err.message);
        assertEquals(FriendInteractionUi.Button.ENABLED, err.nudge);
    }
}
