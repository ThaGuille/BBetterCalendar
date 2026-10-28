package com.example.bbettercalendar.social.stats;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Resultado de {@link RankingCalculator#compute} (F3-u3): las filas de Minutos y de Constancia (yo y mis
 * amigos), la corona, mi puesto en Minutos y el tamaño del grupo (yo más mis amigos). Inmutable.
 */
public final class Ranking {

    /** Sin puesto: no hay amigos con quien compararse. */
    public static final int NO_RANK = 0;

    /**
     * Una fila. {@code value}: en Minutos, los minutos de la semana con tope; en Constancia, los días con
     * foco de la semana. {@code goal}: el objetivo semanal de esa persona (0 si no tiene stats; solo
     * Constancia lo muestra). {@code handle} de mi fila es null: lo pone quien dibuja.
     */
    public static final class Row {
        @NonNull
        public final String uid;
        @Nullable
        public final String handle;
        public final int position;
        public final int value;
        public final int goal;
        public final boolean isMe;

        public Row(@NonNull String uid, @Nullable String handle, int position, int value, int goal,
                   boolean isMe) {
            this.uid = uid;
            this.handle = handle;
            this.position = position;
            this.value = value;
            this.goal = goal;
            this.isMe = isMe;
        }
    }

    @NonNull
    public final List<Row> minutes;
    @NonNull
    public final List<Row> consistency;
    /** Uid del amigo con la corona, o null. Nunca soy yo. */
    @Nullable
    public final String crownUid;
    /** Mi puesto en Minutos (competición estándar), o {@link #NO_RANK} sin amigos. */
    public final int myMinutesPosition;
    /** Yo más mis amigos. */
    public final int groupSize;

    public Ranking(@NonNull List<Row> minutes, @NonNull List<Row> consistency, @Nullable String crownUid,
                   int myMinutesPosition, int groupSize) {
        this.minutes = Collections.unmodifiableList(minutes);
        this.consistency = Collections.unmodifiableList(consistency);
        this.crownUid = crownUid;
        this.myMinutesPosition = myMinutesPosition;
        this.groupSize = groupSize;
    }
}
