package com.example.bbettercalendar.ui.social;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.social.friends.FriendCard;
import com.example.bbettercalendar.social.stats.Ranking;
import com.example.bbettercalendar.social.stats.RankingState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * F3-u7: mapper puro (sin Android) de {@link RankingState} a lo que pinta la pestaña Ranking de Amigos
 * (02 §6). Una fila por persona (yo y mis amigos) en el orden y con los puestos que ya trae
 * {@link Ranking} (competición estándar: los empates comparten puesto), con nombre, color de avatar y
 * valor de la categoría elegida. No hay «último» ni marca de castigo: un amigo a 0 es una fila más.
 */
public final class RankingRows {

    public enum Category { MINUTES, CONSISTENCY }

    public enum Status {
        /** Sin sesión: no se pinta nada. */
        HIDDEN,
        LOADING,
        ERROR,
        /** Con sesión pero sin amigos: estado vacío con la acción de invitar. */
        EMPTY,
        READY
    }

    /** Una fila del ranking. {@code handle} es null en la mía; {@code name} es null si aún no se conoce. */
    public static final class Item {
        @NonNull
        public final String uid;
        public final int position;
        @Nullable
        public final String name;
        @Nullable
        public final String handle;
        public final int avatarColor;
        /** Minutos de la semana (con tope) en Minutos; días con foco de la semana en Constancia. */
        public final int value;
        /** Objetivo semanal de esa persona; solo se enseña en Constancia (0 = sin stats). */
        public final int goal;
        public final boolean isMe;

        Item(@NonNull String uid, int position, @Nullable String name, @Nullable String handle,
             int avatarColor, int value, int goal, boolean isMe) {
            this.uid = uid;
            this.position = position;
            this.name = name;
            this.handle = handle;
            this.avatarColor = avatarColor;
            this.value = value;
            this.goal = goal;
            this.isMe = isMe;
        }

        /** Alcanzó su objetivo semanal (solo con objetivo): para destacar sin castigar al resto. */
        public boolean goalMet() {
            return goal > 0 && value >= goal;
        }
    }

    @NonNull
    public final Status status;
    @NonNull
    public final Category category;
    @NonNull
    public final List<Item> items;

    private RankingRows(@NonNull Status status, @NonNull Category category, @NonNull List<Item> items) {
        this.status = status;
        this.category = category;
        this.items = items;
    }

    /**
     * @param state       el estado del repositorio; null cuenta como {@code LOADING}
     * @param category    la categoría elegida
     * @param myName      mi nombre visible, o null si mi perfil aún no ha llegado
     * @param myColor     mi color de avatar (índice de la paleta)
     */
    @NonNull
    public static RankingRows from(@Nullable RankingState state, @NonNull Category category,
                                   @Nullable String myName, int myColor) {
        if (state == null || state.status == RankingState.Status.LOADING) {
            return new RankingRows(Status.LOADING, category, Collections.<Item>emptyList());
        }
        switch (state.status) {
            case SIGNED_OUT:
                return new RankingRows(Status.HIDDEN, category, Collections.<Item>emptyList());
            case ERROR:
                return new RankingRows(Status.ERROR, category, Collections.<Item>emptyList());
            default:
                break;
        }
        Ranking ranking = state.ranking;
        if (ranking == null || state.friends.isEmpty()) {
            return new RankingRows(Status.EMPTY, category, Collections.<Item>emptyList());
        }
        List<Ranking.Row> source = category == Category.MINUTES ? ranking.minutes : ranking.consistency;
        List<Item> items = new ArrayList<>(source.size());
        for (Ranking.Row r : source) {
            if (r.isMe) {
                items.add(new Item(r.uid, r.position, myName, null, myColor, r.value, r.goal, true));
                continue;
            }
            FriendCard card = state.friend(r.uid);
            items.add(new Item(r.uid, r.position,
                    card != null ? card.displayName : null,
                    card != null ? card.handle : r.handle,
                    card != null ? card.avatarColor : 0,
                    r.value, r.goal, false));
        }
        return new RankingRows(Status.READY, category, Collections.unmodifiableList(items));
    }
}
