package com.example.bbettercalendar.ui.common.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.example.bbettercalendar.R;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class ProjectAccentsTest {

    @Test
    public void colorResFor_cyclesTheFourProjectColours() {
        assertEquals(R.color.bb_project_1, ProjectAccents.colorResFor(0));
        assertEquals(R.color.bb_project_2, ProjectAccents.colorResFor(1));
        assertEquals(R.color.bb_project_3, ProjectAccents.colorResFor(2));
        assertEquals(R.color.bb_project_4, ProjectAccents.colorResFor(3));
        assertEquals(R.color.bb_project_1, ProjectAccents.colorResFor(4));
        assertEquals(R.color.bb_project_3, ProjectAccents.colorResFor(10));
    }

    @Test
    public void colorResFor_negativeIndexesWrapAround() {
        assertEquals(R.color.bb_project_4, ProjectAccents.colorResFor(-1));
        assertEquals(R.color.bb_project_3, ProjectAccents.colorResFor(-2));
        assertEquals(R.color.bb_project_1, ProjectAccents.colorResFor(-4));
        assertEquals(R.color.bb_project_4, ProjectAccents.colorResFor(Integer.MIN_VALUE + 3));
    }

    @Test
    public void projectIconRes_everyKeyHasItsOwnIcon() {
        Set<Integer> seen = new HashSet<>();
        for (String key : ProjectAccents.PROJECT_ICON_KEYS) {
            assertEquals(key, true, seen.add(ProjectAccents.projectIconRes(key)));
        }
        assertEquals(8, seen.size());
        assertEquals(R.drawable.ic_project_cap, ProjectAccents.projectIconRes("cap"));
        assertEquals(R.drawable.ic_project_plane, ProjectAccents.projectIconRes("plane"));
        assertEquals(R.drawable.ic_project_image, ProjectAccents.projectIconRes("image"));
    }

    @Test
    public void projectIconRes_unknownOrNullFallsBackToBox() {
        assertEquals(R.drawable.ic_project_box, ProjectAccents.projectIconRes("box"));
        assertEquals(R.drawable.ic_project_box, ProjectAccents.projectIconRes("rocket"));
        assertEquals(R.drawable.ic_project_box, ProjectAccents.projectIconRes(""));
        assertEquals(R.drawable.ic_project_box, ProjectAccents.projectIconRes(null));
    }

    @Test
    public void tagIconRes_everyKeyHasItsOwnIcon() {
        Set<Integer> seen = new HashSet<>();
        for (String key : ProjectAccents.TAG_ICON_KEYS) {
            assertEquals(key, true, seen.add(ProjectAccents.tagIconRes(key)));
        }
        assertEquals(8, seen.size());
        assertEquals(R.drawable.ic_tag_bag, ProjectAccents.tagIconRes("bag"));
        assertEquals(R.drawable.ic_tag_bulb, ProjectAccents.tagIconRes("bulb"));
    }

    @Test
    public void tagIconRes_unknownOrNullFallsBackToPen() {
        assertEquals(R.drawable.ic_tag_pen, ProjectAccents.tagIconRes("pen"));
        assertEquals(R.drawable.ic_tag_pen, ProjectAccents.tagIconRes("anchor"));
        assertEquals(R.drawable.ic_tag_pen, ProjectAccents.tagIconRes(null));
    }

    @Test
    public void tagColorRes_mapsEachKeyToItsToken() {
        assertEquals(R.color.bb_primary, ProjectAccents.tagColorRes("primary"));
        assertEquals(R.color.bb_secondary, ProjectAccents.tagColorRes("secondary"));
        assertEquals(R.color.bb_project_4, ProjectAccents.tagColorRes("seafoam"));
        assertEquals(R.color.bb_on_surface_muted, ProjectAccents.tagColorRes("muted"));
    }

    @Test
    public void tagColorRes_unknownOrNullFallsBackToMuted() {
        assertEquals(R.color.bb_on_surface_muted, ProjectAccents.tagColorRes("magenta"));
        assertEquals(R.color.bb_on_surface_muted, ProjectAccents.tagColorRes(null));
        assertNotEquals(R.color.bb_primary, ProjectAccents.tagColorRes(null));
    }
}
