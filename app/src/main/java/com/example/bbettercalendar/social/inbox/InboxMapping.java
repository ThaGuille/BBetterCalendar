package com.example.bbettercalendar.social.inbox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * De documentos ({@link InboxBackend.Doc}) a {@link InboxItem} y su orden (F5-u5; 03 §5). Puro. Un documento
 * ilegible o malformado (sin datos, tipo desconocido, faltan campos o tienen otro tipo) se descarta: devuelve
 * null y nunca lanza.
 */
public final class InboxMapping {

    /** La bandeja se lee con este tope, los más nuevos (03 §5). */
    public static final int LIMIT = 50;

    public static final String TYPE = "type";
    public static final String FROM = "from";
    public static final String FROM_HANDLE = "fromHandle";
    public static final String FROM_NAME = "fromName";
    public static final String FROM_COLOR = "fromColor";
    public static final String CREATED_AT = "createdAt";
    public static final String READ = "read";

    private InboxMapping() {
    }

    /** {@code users/{me}/inbox/{id}} → aviso; null si no existe o está malformado. */
    @Nullable
    public static InboxItem item(@NonNull InboxBackend.Doc doc) {
        Map<String, Object> d = doc.data;
        if (d == null) {
            return null;
        }
        try {
            Object type = d.get(TYPE);
            InboxItem.Type t = type instanceof String ? InboxItem.Type.fromWire((String) type) : null;
            Object from = d.get(FROM);
            Object handle = d.get(FROM_HANDLE);
            Object name = d.get(FROM_NAME);
            Object color = d.get(FROM_COLOR);
            Object read = d.get(READ);
            if (t == null || !(from instanceof String) || !(handle instanceof String)
                    || !(name instanceof String) || !(color instanceof Number) || !(read instanceof Boolean)) {
                return null;
            }
            return new InboxItem(doc.id, t, (String) from, (String) handle, (String) name,
                    ((Number) color).intValue(), date(d.get(CREATED_AT)), (Boolean) read);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Los avisos legibles, el más nuevo primero (hora pendiente = el más nuevo; empate por id) y como mucho {@link #LIMIT}. */
    @NonNull
    public static List<InboxItem> items(@NonNull List<InboxBackend.Doc> docs) {
        List<InboxItem> out = new ArrayList<>();
        for (InboxBackend.Doc doc : docs) {
            InboxItem item = item(doc);
            if (item != null) {
                out.add(item);
            }
        }
        Collections.sort(out, NEWEST_FIRST);
        return out.size() > LIMIT ? new ArrayList<>(out.subList(0, LIMIT)) : out;
    }

    private static final Comparator<InboxItem> NEWEST_FIRST = (a, b) -> {
        long ta = a.createdAt == null ? Long.MAX_VALUE : a.createdAt.getTime();
        long tb = b.createdAt == null ? Long.MAX_VALUE : b.createdAt.getTime();
        if (ta != tb) {
            return ta > tb ? -1 : 1;
        }
        return a.id.compareTo(b.id);
    };

    @Nullable
    private static Date date(@Nullable Object value) {
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toDate();
        }
        return value instanceof Date ? (Date) value : null;
    }
}
