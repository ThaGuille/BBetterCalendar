package com.example.bbettercalendar.ui.projects;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

/**
 * Fragment-result keys and bundle helpers between the Projects sub-screens (quick add, task editor,
 * project form, tag editor) and {@link ProjectsFragment}. The sub-screens only {@code set} a result; the
 * fragment handles all of them. Results travel through the NavHostFragment's child FragmentManager, which
 * is the {@code getParentFragmentManager()} of every destination.
 */
public final class ProjectsResults {

    /** A task was saved: projectId, sectionName, isNew. */
    public static final String TASK_SAVED = "projects_result_task_saved";
    /** The task editor asks to delete a task (the screen hides it and deletes after Undo expires): entryId. */
    public static final String TASK_DELETE_REQUESTED = "projects_result_task_delete_requested";
    /** A project was saved: projectId, isNew. */
    public static final String PROJECT_SAVED = "projects_result_project_saved";
    /** The project form asks to archive: projectId. */
    public static final String PROJECT_ARCHIVE_REQUESTED = "projects_result_project_archive_requested";
    /** The project form asks to delete: projectId. */
    public static final String PROJECT_DELETE_REQUESTED = "projects_result_project_delete_requested";
    /** A tag or area was saved: kind (Tag.KIND_*), tagId. */
    public static final String TAG_SAVED = "projects_result_tag_saved";

    public static final String[] ALL_KEYS = {TASK_SAVED, TASK_DELETE_REQUESTED, PROJECT_SAVED,
            PROJECT_ARCHIVE_REQUESTED, PROJECT_DELETE_REQUESTED, TAG_SAVED};

    private static final String ARG_PROJECT_ID = "projectId";
    private static final String ARG_SECTION_NAME = "sectionName";
    private static final String ARG_IS_NEW = "isNew";
    private static final String ARG_ENTRY_ID = "entryId";
    private static final String ARG_KIND = "kind";
    private static final String ARG_TAG_ID = "tagId";

    private ProjectsResults() {}

    // ---------------------------------------------------------------- bundles

    @NonNull
    public static Bundle taskSaved(int projectId, @Nullable String sectionName, boolean isNew) {
        Bundle b = new Bundle();
        b.putInt(ARG_PROJECT_ID, projectId);
        b.putString(ARG_SECTION_NAME, sectionName);
        b.putBoolean(ARG_IS_NEW, isNew);
        return b;
    }

    @NonNull
    public static Bundle taskDeleteRequested(int entryId) {
        Bundle b = new Bundle();
        b.putInt(ARG_ENTRY_ID, entryId);
        return b;
    }

    @NonNull
    public static Bundle projectSaved(int projectId, boolean isNew) {
        Bundle b = new Bundle();
        b.putInt(ARG_PROJECT_ID, projectId);
        b.putBoolean(ARG_IS_NEW, isNew);
        return b;
    }

    @NonNull
    public static Bundle projectArchiveRequested(int projectId) {
        Bundle b = new Bundle();
        b.putInt(ARG_PROJECT_ID, projectId);
        return b;
    }

    @NonNull
    public static Bundle projectDeleteRequested(int projectId) {
        Bundle b = new Bundle();
        b.putInt(ARG_PROJECT_ID, projectId);
        return b;
    }

    @NonNull
    public static Bundle tagSaved(int kind, int tagId) {
        Bundle b = new Bundle();
        b.putInt(ARG_KIND, kind);
        b.putInt(ARG_TAG_ID, tagId);
        return b;
    }

    // ---------------------------------------------------------------- readers

    public static int projectId(@NonNull Bundle b) {
        return b.getInt(ARG_PROJECT_ID, 0);
    }

    @Nullable
    public static String sectionName(@NonNull Bundle b) {
        return b.getString(ARG_SECTION_NAME);
    }

    public static boolean isNew(@NonNull Bundle b) {
        return b.getBoolean(ARG_IS_NEW, false);
    }

    public static int entryId(@NonNull Bundle b) {
        return b.getInt(ARG_ENTRY_ID, 0);
    }

    public static int kind(@NonNull Bundle b) {
        return b.getInt(ARG_KIND, 0);
    }

    public static int tagId(@NonNull Bundle b) {
        return b.getInt(ARG_TAG_ID, 0);
    }

    // ---------------------------------------------------------------- setters for sub-screens

    /** Sub-screens call this with their {@code getParentFragmentManager()}. */
    public static void post(@NonNull FragmentManager navHostChildManager, @NonNull String key,
                            @NonNull Bundle result) {
        navHostChildManager.setFragmentResult(key, result);
    }
}
