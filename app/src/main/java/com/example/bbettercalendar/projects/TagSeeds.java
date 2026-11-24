package com.example.bbettercalendar.projects;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.example.bbettercalendar.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * rediseno-proyectos (DB v16, spec §3.7): las 4 etiquetas de tarea y las 4 áreas de proyecto que
 * trae la app, más el nombre de la sección «Tareas» que recibe cada proyecto en la migración.
 *
 * <p>Los nombres nunca están escritos aquí: son recursos de texto (bloque
 * {@code <!-- rediseno-proyectos: seeds -->} de {@code strings_projects.xml}) y se resuelven con el
 * {@link Context} que se pase, así que salen en el idioma del móvil en el momento de sembrar
 * (decisión del usuario d8). Los siembran {@code DBMigration.migration15To16(Context)} al actualizar
 * y el callback {@code onCreate} de la base de datos en una instalación nueva.
 */
public final class TagSeeds {

    /** Resuelve un recurso de texto; {@code context::getString} en la app, un mapa en los tests JVM. */
    public interface Names {
        @NonNull
        String get(@StringRes int resId);
    }

    /** Una fila predefinida de la tabla {@code tag}, con el nombre aún como recurso. */
    public static final class Seed {
        @StringRes
        public final int nameRes;
        public final String iconKey;
        public final String colorKey;
        public final int kind;
        public final int position;

        Seed(@StringRes int nameRes, String iconKey, String colorKey, int kind, int position) {
            this.nameRes = nameRes;
            this.iconKey = iconKey;
            this.colorKey = colorKey;
            this.kind = kind;
            this.position = position;
        }
    }

    // Claves de color de etiqueta (spec §3.7): bb_primary, bb_secondary, bb_project_4 (seafoam) y
    // bb_on_surface_muted. Las áreas no tienen color: su colorKey es vacío.
    public static final String COLOR_PRIMARY = "primary";
    public static final String COLOR_SECONDARY = "secondary";
    public static final String COLOR_SEAFOAM = "seafoam";
    public static final String COLOR_MUTED = "muted";
    public static final String NO_COLOR = "";

    @StringRes
    public static final int SECTION_NAME_RES = R.string.projects_seed_section_tasks;

    private static final List<Seed> SEEDS = Collections.unmodifiableList(Arrays.asList(
            new Seed(R.string.projects_seed_tag_writing, "pen", COLOR_PRIMARY, Tag.KIND_TAG, 0),
            new Seed(R.string.projects_seed_tag_reading, "book", COLOR_SECONDARY, Tag.KIND_TAG, 1),
            new Seed(R.string.projects_seed_tag_errand, "bag", COLOR_SEAFOAM, Tag.KIND_TAG, 2),
            new Seed(R.string.projects_seed_tag_call, "phone", COLOR_MUTED, Tag.KIND_TAG, 3),
            new Seed(R.string.projects_seed_area_studies, "cap", NO_COLOR, Tag.KIND_AREA, 0),
            new Seed(R.string.projects_seed_area_work, "code", NO_COLOR, Tag.KIND_AREA, 1),
            new Seed(R.string.projects_seed_area_home, "home", NO_COLOR, Tag.KIND_AREA, 2),
            new Seed(R.string.projects_seed_area_personal, "heart", NO_COLOR, Tag.KIND_AREA, 3)));

    private TagSeeds() { }

    /** Las 8 filas predefinidas, en orden: 4 etiquetas (kind 0) y luego 4 áreas (kind 1). */
    @NonNull
    public static List<Seed> seeds() {
        return SEEDS;
    }

    /** Las 8 etiquetas y áreas con su nombre resuelto por {@code names}. */
    @NonNull
    public static List<Tag> build(@NonNull Names names) {
        List<Tag> out = new ArrayList<>(SEEDS.size());
        for (Seed s : SEEDS) {
            out.add(new Tag(names.get(s.nameRes), s.iconKey, s.colorKey, s.kind, s.position));
        }
        return out;
    }

    /** Las 8 etiquetas y áreas en el idioma de {@code context} en este momento. */
    @NonNull
    public static List<Tag> build(@NonNull Context context) {
        return build(context::getString);
    }

    /** Nombre de la sección que recibe cada proyecto en la migración («Tasks» / «Tareas»). */
    @NonNull
    public static String sectionName(@NonNull Names names) {
        return names.get(SECTION_NAME_RES);
    }

    @NonNull
    public static String sectionName(@NonNull Context context) {
        return sectionName((Names) context::getString);
    }
}
