package com.example.bbettercalendar.ui.social;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.bbettercalendar.R;
import com.example.bbettercalendar.social.interaction.InteractionResult;
import com.example.bbettercalendar.social.interaction.InteractionType;
import com.example.bbettercalendar.social.interaction.NudgeEligibility;
import com.example.bbettercalendar.social.stats.RankingState;

import java.time.LocalDate;

/**
 * F5-u8: mapper puro de los controles de animo y empujon del perfil de un amigo (02 §8; D-034). Entra el estado
 * de las stats del amigo, mi fecha de hoy, lo ya enviado hoy (registro local) y el ultimo resultado de enviar;
 * sale el estado de cada boton y el mensaje corto. Sin Android ni reloj. Nunca hay contador de animos (02 §10).
 *
 * <ul>
 *   <li>Animo: visible siempre que el amigo se conozca (stats listas o aun sin stats); "enviado hoy"
 *       (deshabilitado) tras {@code SENT} / {@code ALREADY_SENT} o si el registro de hoy ya lo tiene.</li>
 *   <li>Empujon: solo si {@link NudgeEligibility} lo ofrece (el amigo no ha estudiado hoy); {@code STUDIED_TODAY}
 *       del servidor lo oculta; mismo estado "enviado hoy".</li>
 *   <li>{@code NOT_FRIENDS} y {@code ERROR}: una frase corta.</li>
 * </ul>
 */
final class FriendInteractionUi {

    enum Button {
        /** No se ofrece. */
        HIDDEN,
        /** Se puede pulsar. */
        ENABLED,
        /** Ya enviado hoy: visible, deshabilitado. */
        SENT_TODAY
    }

    final Button cheer;
    final Button nudge;
    /** Recurso de la frase corta, o 0 si no hay. */
    @StringRes
    final int message;

    private FriendInteractionUi(Button cheer, Button nudge, int message) {
        this.cheer = cheer;
        this.nudge = nudge;
        this.message = message;
    }

    /**
     * @param state      stats del amigo (null = aun sin estado: nada visible)
     * @param myToday    mi fecha civil de hoy
     * @param cheerSent  el registro local dice que el animo de hoy ya salio
     * @param nudgeSent  el registro local dice que el empujon de hoy ya salio
     * @param lastType   tipo del ultimo envio de esta visita, o null
     * @param lastResult su resultado, o null
     */
    @NonNull
    static FriendInteractionUi from(@Nullable RankingState.FriendStatsState state, @NonNull LocalDate myToday,
                                    boolean cheerSent, boolean nudgeSent,
                                    @Nullable InteractionType lastType, @Nullable InteractionResult lastResult) {
        boolean known = state != null && (state.status == RankingState.FriendStatsState.Status.READY
                || state.status == RankingState.FriendStatsState.Status.NO_STATS);
        if (!known) {
            return new FriendInteractionUi(Button.HIDDEN, Button.HIDDEN, 0);
        }
        boolean cheerDone = cheerSent || delivered(InteractionType.CHEER, lastType, lastResult);
        boolean nudgeDone = nudgeSent || delivered(InteractionType.NUDGE, lastType, lastResult);
        boolean studied = lastType == InteractionType.NUDGE && lastResult == InteractionResult.STUDIED_TODAY;

        Button cheerButton = cheerDone ? Button.SENT_TODAY : Button.ENABLED;
        Button nudgeButton;
        if (studied || !NudgeEligibility.offered(state.stats, myToday)) {
            nudgeButton = Button.HIDDEN;
        } else {
            nudgeButton = nudgeDone ? Button.SENT_TODAY : Button.ENABLED;
        }
        return new FriendInteractionUi(cheerButton, nudgeButton, messageFor(lastResult));
    }

    private static boolean delivered(InteractionType type, @Nullable InteractionType lastType,
                                     @Nullable InteractionResult lastResult) {
        return lastType == type
                && (lastResult == InteractionResult.SENT || lastResult == InteractionResult.ALREADY_SENT);
    }

    @StringRes
    private static int messageFor(@Nullable InteractionResult result) {
        if (result == InteractionResult.NOT_FRIENDS) {
            return R.string.interaction_msg_not_friends;
        }
        if (result == InteractionResult.ERROR) {
            return R.string.interaction_msg_error;
        }
        return 0;
    }

    boolean cheerVisible() {
        return cheer != Button.HIDDEN;
    }

    boolean nudgeVisible() {
        return nudge != Button.HIDDEN;
    }

    boolean hasMessage() {
        return message != 0;
    }

    /** Texto del boton de animo. */
    @StringRes
    int cheerText() {
        return cheer == Button.SENT_TODAY ? R.string.interaction_cheer_sent : R.string.interaction_cheer;
    }

    /** Texto del boton de empujon. */
    @StringRes
    int nudgeText() {
        return nudge == Button.SENT_TODAY ? R.string.interaction_nudge_sent : R.string.interaction_nudge;
    }
}
