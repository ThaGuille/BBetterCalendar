package com.example.bbettercalendar.social.stats;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.social.friends.FriendCard;

import java.util.Collections;
import java.util.List;

/**
 * Lo que publica {@link RankingRepository#observeRanking()} (F3-u5): el ranking de la semana con sus estados,
 * como {@code FriendsState}. Inmutable. {@link FriendStatsState} es lo que publica
 * {@link RankingRepository#observeFriendStats(String)} para el perfil de un amigo.
 */
public final class RankingState {

    public enum Status {
        /** Sin sesión. */
        SIGNED_OUT,
        /** Con sesión; esperando mi documento, la lista de amigos o el primer documento de cada amigo. */
        LOADING,
        /** {@link #ranking} y {@link #friends} están puestos (sin amigos: grupo de 1 y sin puesto). */
        READY,
        /** Falló el listener de mi documento o el de la lista de amigos; {@link #error} no es null. */
        ERROR
    }

    @NonNull
    public final Status status;
    /** Solo en {@code READY}. */
    @Nullable
    public final Ranking ranking;
    /**
     * En {@code READY}, las tarjetas (nombre, {@code @usuario}, color) de los amigos del ranking, en el orden de
     * {@code FriendsRepository}; vacía en los demás estados. Las filas de {@link Ranking} solo traen el uid.
     */
    @NonNull
    public final List<FriendCard> friends;
    @Nullable
    public final Throwable error;

    private RankingState(@NonNull Status status, @Nullable Ranking ranking, @NonNull List<FriendCard> friends,
                         @Nullable Throwable error) {
        this.status = status;
        this.ranking = ranking;
        this.friends = friends;
        this.error = error;
    }

    @NonNull
    public static RankingState signedOut() {
        return new RankingState(Status.SIGNED_OUT, null, Collections.<FriendCard>emptyList(), null);
    }

    @NonNull
    public static RankingState loading() {
        return new RankingState(Status.LOADING, null, Collections.<FriendCard>emptyList(), null);
    }

    @NonNull
    public static RankingState ready(@NonNull Ranking ranking, @NonNull List<FriendCard> friends) {
        return new RankingState(Status.READY, ranking, Collections.unmodifiableList(friends), null);
    }

    @NonNull
    public static RankingState error(@NonNull Throwable error) {
        return new RankingState(Status.ERROR, null, Collections.<FriendCard>emptyList(), error);
    }

    /** La tarjeta del amigo {@code uid} en {@code READY}, o null (también para mi uid). */
    @Nullable
    public FriendCard friend(@NonNull String uid) {
        for (FriendCard c : friends) {
            if (c.uid.equals(uid)) {
                return c;
            }
        }
        return null;
    }

    /** {@code READY} con al menos un amigo: solo entonces hay «Nº entre amigos». */
    public boolean hasFriends() {
        return status == Status.READY && !friends.isEmpty();
    }

    /**
     * Las stats de un amigo para su perfil (02 §9), con la semántica del ranking ya aplicada a mis claves de hoy
     * ({@link RankingCalculator#applySemantics}): semana y día ajenos a 0, racha solo de esta semana o de la
     * anterior; {@code bestWeekStreak} y {@code totalMinutes} tal como se publicaron. Inmutable.
     */
    public static final class FriendStatsState {

        public enum Status {
            /** Sin sesión. */
            SIGNED_OUT,
            /** Esperando la lista de amigos o el primer documento de ese amigo. */
            LOADING,
            /** {@link #stats} no es null. */
            READY,
            /** Es amigo, pero aún no tiene stats (sin documento, malformado o lectura denegada): no son ceros. */
            NO_STATS,
            /** No está (o ya no está) en mi lista de amigos: p. ej. tras eliminarlo. */
            NOT_FRIEND,
            /** Falló la lista de amigos y de ese amigo no se sabe nada; {@link #error} no es null. */
            ERROR
        }

        @NonNull
        public final String uid;
        @NonNull
        public final Status status;
        /** Solo en {@code READY}, con la semántica aplicada. */
        @Nullable
        public final FriendStats stats;
        /** Su tarjeta si se conoce (en {@code READY} y {@code NO_STATS}). */
        @Nullable
        public final FriendCard card;
        @Nullable
        public final Throwable error;

        private FriendStatsState(@NonNull String uid, @NonNull Status status, @Nullable FriendStats stats,
                                 @Nullable FriendCard card, @Nullable Throwable error) {
            this.uid = uid;
            this.status = status;
            this.stats = stats;
            this.card = card;
            this.error = error;
        }

        @NonNull
        public static FriendStatsState signedOut(@NonNull String uid) {
            return new FriendStatsState(uid, Status.SIGNED_OUT, null, null, null);
        }

        @NonNull
        public static FriendStatsState loading(@NonNull String uid) {
            return new FriendStatsState(uid, Status.LOADING, null, null, null);
        }

        @NonNull
        public static FriendStatsState ready(@NonNull String uid, @NonNull FriendStats stats,
                                             @Nullable FriendCard card) {
            return new FriendStatsState(uid, Status.READY, stats, card, null);
        }

        @NonNull
        public static FriendStatsState noStats(@NonNull String uid, @Nullable FriendCard card) {
            return new FriendStatsState(uid, Status.NO_STATS, null, card, null);
        }

        @NonNull
        public static FriendStatsState notFriend(@NonNull String uid) {
            return new FriendStatsState(uid, Status.NOT_FRIEND, null, null, null);
        }

        @NonNull
        public static FriendStatsState error(@NonNull String uid, @NonNull Throwable error) {
            return new FriendStatsState(uid, Status.ERROR, null, null, error);
        }
    }
}
