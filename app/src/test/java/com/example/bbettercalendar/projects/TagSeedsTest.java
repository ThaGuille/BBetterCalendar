package com.example.bbettercalendar.projects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.bbettercalendar.R;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * rediseno-proyectos (DB v16, spec §3.7): las etiquetas y áreas que trae la app. Los nombres salen de
 * los recursos (idioma del móvil); aquí un mapa hace de Context con los textos en español.
 */
public class TagSeedsTest {

    private static final Set<String> TAG_ICONS = new HashSet<>(Arrays.asList(
            "pen", "book", "bag", "phone", "code", "heart", "home", "bulb"));
    private static final Set<String> AREA_ICONS = new HashSet<>(Arrays.asList(
            "cap", "book", "code", "plane", "box", "home", "heart", "image"));
    private static final Set<String> TAG_COLORS = new HashSet<>(Arrays.asList(
            "primary", "secondary", "seafoam", "muted"));

    private static final Map<Integer, String> SPANISH = new HashMap<>();
    static {
        SPANISH.put(R.string.projects_seed_section_tasks, "Tareas");
        SPANISH.put(R.string.projects_seed_tag_writing, "escritura");
        SPANISH.put(R.string.projects_seed_tag_reading, "lectura");
        SPANISH.put(R.string.projects_seed_tag_errand, "recado");
        SPANISH.put(R.string.projects_seed_tag_call, "llamada");
        SPANISH.put(R.string.projects_seed_area_studies, "Estudios");
        SPANISH.put(R.string.projects_seed_area_work, "Trabajo");
        SPANISH.put(R.string.projects_seed_area_home, "Casa");
        SPANISH.put(R.string.projects_seed_area_personal, "Personal");
    }

    private static final TagSeeds.Names IN_SPANISH = resId -> {
        String name = SPANISH.get(resId);
        if (name == null) {
            throw new AssertionError("unexpected string resource " + resId);
        }
        return name;
    };

    @Test
    public void theAppShipsFourTaskTagsThenFourAreas() {
        List<Tag> tags = TagSeeds.build(IN_SPANISH);

        assertEquals(8, tags.size());
        assertSeed(tags.get(0), "escritura", "pen", "primary", Tag.KIND_TAG, 0);
        assertSeed(tags.get(1), "lectura", "book", "secondary", Tag.KIND_TAG, 1);
        assertSeed(tags.get(2), "recado", "bag", "seafoam", Tag.KIND_TAG, 2);
        assertSeed(tags.get(3), "llamada", "phone", "muted", Tag.KIND_TAG, 3);
        assertSeed(tags.get(4), "Estudios", "cap", "", Tag.KIND_AREA, 0);
        assertSeed(tags.get(5), "Trabajo", "code", "", Tag.KIND_AREA, 1);
        assertSeed(tags.get(6), "Casa", "home", "", Tag.KIND_AREA, 2);
        assertSeed(tags.get(7), "Personal", "heart", "", Tag.KIND_AREA, 3);
    }

    @Test
    public void iconKeysComeFromTheSpecLists() {
        for (Tag t : TagSeeds.build(IN_SPANISH)) {
            Set<String> allowed = t.kind == Tag.KIND_TAG ? TAG_ICONS : AREA_ICONS;
            assertTrue(t.name + " icon " + t.iconKey, allowed.contains(t.iconKey));
        }
    }

    @Test
    public void tagsHaveATagColourAndAreasHaveNone() {
        int tags = 0;
        int areas = 0;
        for (Tag t : TagSeeds.build(IN_SPANISH)) {
            if (t.kind == Tag.KIND_TAG) {
                tags++;
                assertTrue(t.name + " colour " + t.colorKey, TAG_COLORS.contains(t.colorKey));
            } else {
                areas++;
                assertEquals(t.name + " has no colour", "", t.colorKey);
            }
        }
        assertEquals(4, tags);
        assertEquals(4, areas);
    }

    @Test
    public void namesAreResolvedThroughTheGivenNamesNotHardCoded() {
        TagSeeds.Names english = resId -> resId == R.string.projects_seed_tag_writing ? "writing" : "x" + resId;

        List<Tag> tags = TagSeeds.build(english);

        assertEquals("writing", tags.get(0).name);
        assertEquals("x" + R.string.projects_seed_area_personal, tags.get(7).name);
    }

    @Test
    public void theMigrationSectionIsNamedFromItsResource() {
        assertEquals("Tareas", TagSeeds.sectionName(IN_SPANISH));
    }

    private static void assertSeed(Tag t, String name, String icon, String colour, int kind, int position) {
        assertEquals(name, t.name);
        assertEquals(name + " icon", icon, t.iconKey);
        assertEquals(name + " colour", colour, t.colorKey);
        assertEquals(name + " kind", kind, t.kind);
        assertEquals(name + " position", position, t.position);
    }
}
