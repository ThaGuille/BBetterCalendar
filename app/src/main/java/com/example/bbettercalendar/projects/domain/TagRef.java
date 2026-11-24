package com.example.bbettercalendar.projects.domain;

/** Room-free reference to a tag or area. */
public final class TagRef {
    public final long id;
    public final String name;
    public final String iconKey;
    public final String colorKey;
    public final int kind;

    public TagRef(long id, String name, String iconKey, String colorKey, int kind) {
        this.id = id;
        this.name = name;
        this.iconKey = iconKey;
        this.colorKey = colorKey;
        this.kind = kind;
    }
}
