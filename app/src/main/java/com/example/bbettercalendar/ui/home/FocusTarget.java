package com.example.bbettercalendar.ui.home;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bbettercalendar.ui.focus.TaskFocusRequest;

// Objetivo de concentración "vinculado" al timer de Home (spec focus-attribution, decisión: el
// botón "focus this" enruta por el ÚNICO timer que vive en HomeFragment). Cualquier pantalla
// (lista de hoy de Home, detalle de proyecto) fija aquí la tarea a la que atribuir la sesión y
// navega a Home; HomeFragment lo lee para arrancar la sesión vinculada y, al completar cada
// sesión, pasa el entryId para registrar el FocusEvent atribuido.
//
// Estado en memoria (estático): sobrevive a cambios de configuración y a la recreación del
// fragment, pero NO a la muerte del proceso — si el proceso muere a mitad de una sesión vinculada,
// la sesión restaurada vuelve a ser genérica (entryId 0). Persistir esto queda fuera del MVP.
// Rediseño de Proyectos (u4): FocusSessionViewModel guarda la tarea de una sesión viva en su
// SavedStateHandle y la devuelve aquí al restaurarse, así que los ciclos de una tarea siguen
// atribuyéndose a ella tras la muerte del proceso.
public final class FocusTarget {

    private static int entryId = 0;
    private static String title = null;
    // Objetivo de minutos del item (spec focus-attribution): la sesión de concentración vinculada
    // arranca el pomodoro con esta duración en vez de la del timer por defecto. 0 = sin objetivo.
    // Rediseño de Proyectos (D-P11): ahora es la meta que se reparte en ciclos (TaskCyclePlan).
    private static int targetMinutes = 0;
    // Pedir arranque automático UNA vez: lo consume HomeFragment al llegar a Home (p. ej. tras
    // pulsar "focus this" en el detalle de proyecto y navegar). Evita que un objetivo aún no
    // cumplido re-arranque el timer en cada onResume después de cada pomodoro.
    private static boolean pendingAutoStart = false;
    // Rediseño de Proyectos (§3.5): lo que trae un TaskFocusRequest. attributedMinutes sólo vale si
    // attributedKnown (el set(...) antiguo no lo sabe: el ViewModel lo lee de la base).
    private static int attributedMinutes = 0;
    private static boolean attributedKnown = false;
    private static boolean blockOnFocus = false;
    private static int projectId = 0;
    private static String projectName = null;
    private static String projectIconKey = null;
    private static int projectColorIndex = 0;

    private FocusTarget() { }

    /**
     * Vincula el timer a una tarea/item y pide arranque automático. entryId 0 desvincula.
     * newTargetMinutes es el objetivo del item (duración con la que arranca el pomodoro); 0 = usar
     * la duración por defecto del timer de Home.
     */
    public static void set(int newEntryId, String newTitle, int newTargetMinutes) {
        entryId = newEntryId;
        title = newTitle;
        targetMinutes = newTargetMinutes;
        pendingAutoStart = newEntryId != 0;
        attributedMinutes = 0;
        attributedKnown = false;
        blockOnFocus = false;
        projectId = 0;
        projectName = null;
        projectIconKey = null;
        projectColorIndex = 0;
    }

    /**
     * Rediseño de Proyectos: vincula el timer a la tarea de {@code request} con todos sus datos
     * (meta, minutos ya atribuidos, bloqueo al enfocar, proyecto). Pide arranque automático como
     * {@link #set(int, String, int)}; quien arranca la sesión en el acto lo consume.
     */
    public static void set(@NonNull TaskFocusRequest request) {
        set(request.getEntryId(), request.getTitle(), request.getGoalMinutes());
        attributedMinutes = request.getAttributedMinutes();
        attributedKnown = true;
        blockOnFocus = request.isBlockOnFocus();
        projectId = request.getProjectId();
        projectName = request.getProjectName();
        projectIconKey = request.getProjectIconKey();
        projectColorIndex = request.getProjectColorIndex();
    }

    public static void clear() {
        set(0, null, 0);
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

    /** Objetivo de minutos del item vinculado; 0 = usar la duración por defecto del timer. */
    public static int getTargetMinutes() {
        return targetMinutes;
    }

    /** La meta de la tarea vinculada (= {@link #getTargetMinutes()}). */
    public static int getGoalMinutes() {
        return targetMinutes;
    }

    /** Minutos ya atribuidos al vincularla; sólo fiable si {@link #isAttributedMinutesKnown()}. */
    public static int getAttributedMinutes() {
        return attributedMinutes;
    }

    /** Se vinculó con un {@link TaskFocusRequest} (trae sus minutos atribuidos). */
    public static boolean isAttributedMinutesKnown() {
        return attributedKnown;
    }

    /** «Bloqueo al enfocar» de la tarea vinculada. */
    public static boolean isBlockOnFocus() {
        return blockOnFocus;
    }

    /** Proyecto de la tarea vinculada; 0 = suelta o desconocido. */
    public static int getProjectId() {
        return projectId;
    }

    @Nullable
    public static String getProjectName() {
        return projectName;
    }

    @Nullable
    public static String getProjectIconKey() {
        return projectIconKey;
    }

    public static int getProjectColorIndex() {
        return projectColorIndex;
    }

    /** Los datos vinculados como petición (para guardarlos y restaurarlos); null sin tarea. */
    @Nullable
    public static TaskFocusRequest toRequest() {
        if (entryId == 0) {
            return null;
        }
        return new TaskFocusRequest(entryId, title, targetMinutes, attributedMinutes, blockOnFocus,
                projectId, projectName, projectIconKey, projectColorIndex);
    }
}
