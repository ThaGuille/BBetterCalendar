package com.example.bbettercalendar.ui.focus;

import androidx.annotation.Nullable;

import com.example.bbettercalendar.social.SocialRank;
import com.example.bbettercalendar.social.stats.Ranking;
import com.example.bbettercalendar.social.stats.RankingState;

/**
 * F3-u8: «Nº entre amigos» de Cycle complete. Mapper puro del {@link RankingState} a mi puesto en Minutos de
 * esta semana (02 §6), o null cuando no hay puesto que mostrar (d5: el tile enseña un guion).
 */
public final class CycleRank {

    private CycleRank() { }

    /**
     * Mi puesto (competición estándar: un empate comparte puesto) entre yo y mis amigos, solo con el estado
     * {@code READY} y al menos un amigo; en cualquier otro caso null.
     */
    @Nullable
    public static SocialRank from(@Nullable RankingState state) {
        if (state == null || !state.hasFriends() || state.ranking == null) {
            return null;
        }
        Ranking ranking = state.ranking;
        if (ranking.myMinutesPosition < 1 || ranking.groupSize < ranking.myMinutesPosition) {
            return null;
        }
        return new SocialRank(ranking.myMinutesPosition, ranking.groupSize);
    }
}
