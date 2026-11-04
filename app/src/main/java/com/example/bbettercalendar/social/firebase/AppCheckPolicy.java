package com.example.bbettercalendar.social.firebase;

/**
 * L-u3: decide el proveedor de App Check segun la build (L.1). Clase pura, sin Android, para testearla.
 *
 * <ul>
 *   <li>{@link Provider#NONE}: sin FEATURE_SOCIAL o contra el Emulator Suite (el emulador no valida tokens).</li>
 *   <li>{@link Provider#DEBUG}: build debug contra el proyecto real.</li>
 *   <li>{@link Provider#PLAY_INTEGRITY}: build release con FEATURE_SOCIAL.</li>
 * </ul>
 */
public final class AppCheckPolicy {

    public enum Provider { NONE, DEBUG, PLAY_INTEGRITY }

    private AppCheckPolicy() {}

    public static Provider decide(boolean featureSocial, boolean useFirebaseEmulator, boolean debugBuild) {
        if (!featureSocial || useFirebaseEmulator) return Provider.NONE;
        return debugBuild ? Provider.DEBUG : Provider.PLAY_INTEGRITY;
    }
}
