package com.example.bbettercalendar.ui.social;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.social.stats.FriendStats;
import com.example.bbettercalendar.social.stats.RankingState;

/**
 * F3-u6: mapper puro del estado de las stats de un amigo ({@link RankingState.FriendStatsState}) a lo que enseña
 * su perfil (02 §9): qué bloque se ve y los números ya preparados (el fragmento solo los mete en cadenas
 * localizadas). Sin Android ni reloj. La semántica del ranking (semana y día ajenos a 0, racha solo de esta
 * semana o la anterior) ya viene aplicada por {@code RankingRepository}; aquí no se vuelve a calcular.
 * Sin control de ánimo (F5, D-030).
 */
final class FriendProfileUi {

    /** Qué bloque ocupa la parte de debajo de la cabecera. */
    enum Body {
        /** Esperando la lista de amigos o su primer documento. */
        LOADING,
        /** Las cinco cifras. */
        STATS,
        /** Es amigo pero aún no publicó stats: se dice, no se enseñan ceros. */
        EMPTY,
        /** No se pudo leer la lista de amigos. */
        ERROR,
        /** Ya no es amigo (o no hay sesión): el fragmento vuelve a Amigos. */
        GONE
    }

    final Body body;
    /** Racha semanal en semanas. */
    final int weekStreak;
    /** Días de foco esta semana frente a su objetivo (d4: «3 de 4 días» con puntos). */
    final int daysDone;
    final int goal;
    /** Puntos del objetivo: uno por día del objetivo; {@link #dotsFilled} de ellos llenos. */
    final int dotsTotal;
    final int dotsFilled;
    final boolean goalMet;
    /** Minutos de esta semana (el valor con tope diario). */
    final int minutesWeek;
    final int bestStreak;
    /** Total de foco partido en horas y minutos sueltos (0..59). */
    final int totalHours;
    final int totalMinutesRest;

    private FriendProfileUi(Body body, int weekStreak, int daysDone, int goal, int minutesWeek, int bestStreak,
                            int totalHours, int totalMinutesRest) {
        this.body = body;
        this.weekStreak = weekStreak;
        this.daysDone = daysDone;
        this.goal = goal;
        this.dotsTotal = goal;
        this.dotsFilled = Math.min(daysDone, goal);
        this.goalMet = goal > 0 && daysDone >= goal;
        this.minutesWeek = minutesWeek;
        this.bestStreak = bestStreak;
        this.totalHours = totalHours;
        this.totalMinutesRest = totalMinutesRest;
    }

    private static FriendProfileUi of(Body body) {
        return new FriendProfileUi(body, 0, 0, 0, 0, 0, 0, 0);
    }

    boolean showStats() {
        return body == Body.STATS;
    }

    boolean showEmpty() {
        return body == Body.EMPTY;
    }

    boolean showLoading() {
        return body == Body.LOADING;
    }

    boolean showError() {
        return body == Body.ERROR;
    }

    boolean isGone() {
        return body == Body.GONE;
    }

    /** El total tiene horas (si no, solo se dicen minutos). */
    boolean totalHasHours() {
        return totalHours > 0;
    }

    /** El total tiene minutos sueltos que decir junto a las horas (o solos si no hay horas). */
    boolean totalHasMinutes() {
        return totalMinutesRest > 0 || totalHours == 0;
    }

    @NonNull
    static FriendProfileUi from(@Nullable RankingState.FriendStatsState state) {
        if (state == null) {
            return of(Body.LOADING);
        }
        switch (state.status) {
            case READY:
                FriendStats s = state.stats;
                if (s == null) {
                    return of(Body.EMPTY);
                }
                int total = Math.max(0, s.totalMinutes);
                return new FriendProfileUi(Body.STATS, Math.max(0, s.weekStreak), Math.max(0, s.daysThisWeek),
                        Math.max(1, s.weeklyGoal), Math.max(0, s.minutesWeekCapped), Math.max(0, s.bestWeekStreak),
                        total / 60, total % 60);
            case NO_STATS:
                return of(Body.EMPTY);
            case ERROR:
                return of(Body.ERROR);
            case NOT_FRIEND:
            case SIGNED_OUT:
                return of(Body.GONE);
            default:
                return of(Body.LOADING);
        }
    }
}
