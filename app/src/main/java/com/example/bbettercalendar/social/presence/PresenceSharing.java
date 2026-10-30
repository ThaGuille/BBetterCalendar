package com.example.bbettercalendar.social.presence;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.social.account.AgeGate;
import com.example.bbettercalendar.social.account.SocialPrefs;

import java.time.Year;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * El interruptor «Compartir mi estado en directo» (F4.7, D-032), por dispositivo y por uid en
 * {@link SocialPrefs}; no es recíproco y no va a Firestore. Apagado, {@link PresencePublisher} solo escribe
 * {@code idle}.
 *
 * <p><b>Valor por defecto (d4 = B).</b> Si la persona nunca lo tocó en este móvil: encendido con 18 años o más
 * según el año de nacimiento guardado ({@link AgeGate}); apagado con 14-17 y con el año desconocido (cuenta
 * como menor).
 *
 * <p><b>Lectura.</b> La de las preferencias es asíncrona: {@link #readSharing} entrega el valor resuelto en el
 * hilo de E/S y lo deja en caché; {@link #isSharing} contesta al momento con la caché y, mientras no se sabe,
 * devuelve false («año aún no leído» cuenta como apagado). {@link #setSharing} cambia la caché en el acto,
 * lo guarda y avisa al publicador, que escribe {@code idle} al apagar o vuelve a publicar al encender.
 * Es la API del interruptor de Mi perfil (u8).
 */
@Singleton
public class PresenceSharing {

    /** Avisa de un cambio del interruptor (lo escucha {@link PresencePublisher}). */
    interface Listener {
        void onSharingChanged(@NonNull String uid, boolean on);
    }

    private final SocialPrefs prefs;
    private final IntSupplier currentYear;
    private final Map<String, Boolean> cache = new ConcurrentHashMap<>();
    @Nullable
    private volatile Listener listener;

    @Inject
    public PresenceSharing(SocialPrefs prefs) {
        this(prefs, () -> Year.now().getValue());
    }

    /** Para tests: año actual fijo. */
    PresenceSharing(@NonNull SocialPrefs prefs, @NonNull IntSupplier currentYear) {
        this.prefs = prefs;
        this.currentYear = currentYear;
    }

    void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    /**
     * Valor conocido para {@code uid}; false mientras no se ha leído (y entonces pide la lectura). Se puede
     * llamar desde cualquier hilo.
     */
    public boolean isSharing(@NonNull String uid) {
        Boolean known = cache.get(uid);
        if (known == null) {
            readSharing(uid, value -> { });
            return false;
        }
        return known;
    }

    /**
     * Entrega el valor resuelto para {@code uid}: lo elegido en este móvil o, si nunca se tocó, el de d4. Con
     * caché llega en el acto en el hilo que llama; si no, en el hilo de E/S de {@link SocialPrefs}.
     */
    public void readSharing(@NonNull String uid, @NonNull Consumer<Boolean> callback) {
        Boolean known = cache.get(uid);
        if (known != null) {
            callback.accept(known);
            return;
        }
        prefs.readPresenceSharing(uid, stored -> {
            if (stored != null) {
                callback.accept(resolve(uid, stored));
                return;
            }
            prefs.readBirthYear(birthYear -> callback.accept(resolve(uid, defaultFor(birthYear))));
        });
    }

    /** Cambia el interruptor de {@code uid} (Mi perfil). Cualquier hilo. */
    public void setSharing(@NonNull String uid, boolean on) {
        cache.put(uid, on);
        prefs.savePresenceSharing(uid, on);
        Listener l = listener;
        if (l != null) {
            l.onSharingChanged(uid, on);
        }
    }

    /** d4 = B: encendido solo con 18+; con el año desconocido, como menor. */
    boolean defaultFor(@Nullable Integer birthYear) {
        return birthYear != null
                && AgeGate.age(birthYear, currentYear.getAsInt()) >= AgeGate.ADULT_AGE;
    }

    /** Guarda lo leído salvo que un {@link #setSharing} llegara antes (gana el cambio de la persona). */
    private boolean resolve(@NonNull String uid, boolean read) {
        Boolean previous = cache.putIfAbsent(uid, read);
        return previous != null ? previous : read;
    }
}
