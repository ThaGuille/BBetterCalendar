package com.example.bbettercalendar.ui.projects.picker;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import com.example.bbettercalendar.projects.domain.SectionRef;
import com.example.bbettercalendar.projects.domain.TagRef;

import java.util.List;

/**
 * What a task picker needs from its host (the quick-add box, the full editor or the debug gallery):
 * a FragmentManager for the Material date and time dialogs, the change callbacks and the lists of
 * tags and sections of the project being edited.
 */
public interface PickerHost {

    /** FragmentManager that shows the MaterialDatePicker / MaterialTimePicker dialogs. */
    @NonNull
    FragmentManager getPickerFragmentManager();

    /** The draft was changed by a picker (the host refreshes whatever shows its value). */
    void onDraftChanged();

    /** The "Done" of a picker header was tapped. */
    void onDone();

    /** "New" of the tag picker: the host opens the tag editor. */
    void onNewTag();

    /** Long press on a tag of the tag picker: the host opens the tag editor for it. */
    void onEditTag(long tagId);

    @NonNull
    List<TagRef> getTags();

    @NonNull
    List<SectionRef> getSections();
}
