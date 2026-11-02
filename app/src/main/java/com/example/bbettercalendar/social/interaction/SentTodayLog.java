package com.example.bbettercalendar.social.interaction;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Registro por dispositivo de lo ya enviado hoy (F5-u5; D-035): le dice al perfil de un amigo que el ánimo o el
 * empujón de hoy ya salió, sin esperar a que el servidor responda {@code already}. No es la autoridad (lo es la
 * respuesta del servidor); es solo memoria para la pantalla.
 *
 * <p>Cada entrada es {@code miUid|amigo|tipo|dayKey}. Es <b>por mi uid</b>: otra cuenta en el mismo móvil no ve
 * lo que envió la anterior. En <b>cada lectura y cada escritura</b> se purgan las entradas de otros días y de
 * otros uids (y las malformadas), así que el fichero nunca guarda más que lo de hoy de la cuenta actual.
 */
final class SentTodayLog {

    private static final char SEP = '|';

    private final SentLogStore store;

    SentTodayLog(@NonNull SentLogStore store) {
        this.store = store;
    }

    /** ¿Ya se envió hoy ese tipo a ese amigo desde esta cuenta? */
    boolean contains(@NonNull String me, @NonNull String friendUid, @NonNull InteractionType type,
                     @NonNull String dayKey) {
        return purged(me, dayKey).contains(entry(me, friendUid, type, dayKey));
    }

    void record(@NonNull String me, @NonNull String friendUid, @NonNull InteractionType type,
                @NonNull String dayKey) {
        Set<String> entries = purged(me, dayKey);
        if (entries.add(entry(me, friendUid, type, dayKey))) {
            store.write(entries);
        }
    }

    void clear() {
        store.clear();
    }

    /** Lo guardado que sigue vigente (mío y de hoy); si la purga quitó algo, ya está escrito. */
    @NonNull
    private Set<String> purged(@NonNull String me, @NonNull String dayKey) {
        Set<String> all = store.read();
        Set<String> keep = new HashSet<>();
        for (String e : all) {
            if (isMineToday(e, me, dayKey)) {
                keep.add(e);
            }
        }
        if (keep.size() != all.size()) {
            store.write(keep);
        }
        return keep;
    }

    private static boolean isMineToday(@NonNull String e, @NonNull String me, @NonNull String dayKey) {
        String[] parts = e.split("\\|", -1);
        return parts.length == 4 && parts[0].equals(me) && parts[3].equals(dayKey);
    }

    @NonNull
    private static String entry(@NonNull String me, @NonNull String friendUid, @NonNull InteractionType type,
                                @NonNull String dayKey) {
        return me + SEP + friendUid + SEP + type.wire + SEP + dayKey;
    }
}
