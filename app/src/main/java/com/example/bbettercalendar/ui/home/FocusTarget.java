package com.example.bbettercalendar.ui.home;

// Objetivo de concentración "vinculado" al timer de Home (spec focus-attribution, decisión: el
// botón "focus this" enruta por el ÚNICO timer que vive en HomeFragment). Cualquier pantalla
// (lista de hoy de Home, detalle de proyecto) fija aquí la tarea a la que atribuir la sesión y
// navega a Home; HomeFragment lo lee para arrancar la sesión vinculada y, al completar cada
// sesión, pasa el entryId para registrar el FocusEvent atribuido.
//
// Estado en memoria (estático): sobrevive a cambios de configuración y a la recreación del
// fragment, pero NO a la muerte del proceso — si el proceso muere a mitad de una sesión vinculada,
// la sesión restaurada vuelve a ser genérica (entryId 0). Persistir esto queda fuera del MVP.
public final class FocusTarget {

    private static int entryId = 0;
    private static String title = null;
    // Pedir arranque automático UNA vez: lo consume HomeFragment al llegar a Home (p. ej. tras
    // pulsar "focus this" en el detalle de proyecto y navegar). Evita que un objetivo aún no
    // cumplido re-arranque el timer en cada onResume después de cada pomodoro.
    private static boolean pendingAutoStart = false;

    private FocusTarget() { }

    /** Vincula el timer a una tarea/item y pide arranque automático. entryId 0 desvincula. */
    public static void set(int newEntryId, String newTitle) {
        entryId = newEntryId;
        title = newTitle;
        pendingAutoStart = newEntryId != 0;
    }

    public static void clear() {
        entryId = 0;
        title = null;
        pendingAutoStart = false;
    }

    /** Devuelve true una sola vez si hay arranque pendiente, y lo consume. */
    public static boolean consumePendingAutoStart() {
        boolean pending = pendingAutoStart;
        pendingAutoStart = false;
        return pending;
    }

    public static boolean isSet() {
        return entryId != 0;
    }

    public static int getEntryId() {
        return entryId;
    }

    public static String getTitle() {
        return title;
    }
}
