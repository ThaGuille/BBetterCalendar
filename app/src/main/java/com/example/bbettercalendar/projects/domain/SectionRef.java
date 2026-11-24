package com.example.bbettercalendar.projects.domain;

/** Room-free reference to a project section. */
public final class SectionRef {
    public final long id;
    public final String name;
    public final int position;

    public SectionRef(long id, String name, int position) {
        this.id = id;
        this.name = name;
        this.position = position;
    }
}
