package com.example.bbettercalendar.ui.social;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.social.block.BlockState;
import com.example.bbettercalendar.social.block.BlockedCard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * L-u10: lo que pinta «Usuarios bloqueados» a partir de {@link BlockState}. Puro (sin Android): una fila por
 * bloqueado en el orden del repositorio (el más nuevo primero), con el nombre o, si falta, el {@code @usuario}.
 */
final class BlockedRows {

    enum Status { LOADING, ERROR, EMPTY, READY, GONE }

    static final class Row {
        @NonNull final String uid;
        @NonNull final String name;
        @NonNull final String handleText;
        final int avatarColor;

        Row(@NonNull String uid, @NonNull String name, @NonNull String handleText, int avatarColor) {
            this.uid = uid;
            this.name = name;
            this.handleText = handleText;
            this.avatarColor = avatarColor;
        }
    }

    @NonNull final Status status;
    @NonNull final List<Row> rows;

    private BlockedRows(@NonNull Status status, @NonNull List<Row> rows) {
        this.status = status;
        this.rows = rows;
    }

    /** Sin estado todavía = cargando; sin sesión = la pantalla sobra (GONE). */
    @NonNull
    static BlockedRows from(@Nullable BlockState state) {
        if (state == null) {
            return new BlockedRows(Status.LOADING, Collections.<Row>emptyList());
        }
        switch (state.status) {
            case SIGNED_OUT:
                return new BlockedRows(Status.GONE, Collections.<Row>emptyList());
            case ERROR:
                return new BlockedRows(Status.ERROR, Collections.<Row>emptyList());
            case READY:
                List<Row> rows = new ArrayList<>();
                for (BlockedCard c : state.blocked) {
                    String name = c.displayName.trim().isEmpty() ? c.handle : c.displayName;
                    rows.add(new Row(c.uid, name, "@" + c.handle, c.avatarColor));
                }
                return new BlockedRows(rows.isEmpty() ? Status.EMPTY : Status.READY, rows);
            case LOADING:
            default:
                return new BlockedRows(Status.LOADING, Collections.<Row>emptyList());
        }
    }
}
