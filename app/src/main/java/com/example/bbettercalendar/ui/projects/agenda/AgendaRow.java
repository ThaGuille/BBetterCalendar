package com.example.bbettercalendar.ui.projects.agenda;

/** One line of the project agenda sheet, already decided: what kind, which tone, which text. */
public final class AgendaRow {

    public enum Kind { HEADER, TASK, DEADLINE, UNDATED }

    /** Day headers only. Weekday headers use {@link #label}. */
    public enum Day { YESTERDAY, TODAY, TOMORROW, DATE }

    /** Day headers only: ember for overdue days, sage for today. */
    public enum Tone { OVERDUE, TODAY, NORMAL }

    public final Kind kind;
    public final Day day;
    public final Tone tone;
    /** Header date text ("Mon 23") for {@link Day#DATE}, the deadline date for DEADLINE, else null. */
    public final String label;
    /** TASK: "9:00" or "" when it has no time. */
    public final String time;
    public final String title;
    /** TASK: "25 min" or "" when it has no duration. */
    public final String duration;
    public final boolean blocks;
    /** UNDATED: how many pending tasks have no date. */
    public final int count;

    private AgendaRow(Kind kind, Day day, Tone tone, String label, String time, String title,
                      String duration, boolean blocks, int count) {
        this.kind = kind;
        this.day = day;
        this.tone = tone;
        this.label = label;
        this.time = time;
        this.title = title;
        this.duration = duration;
        this.blocks = blocks;
        this.count = count;
    }

    static AgendaRow header(Day day, Tone tone, String label) {
        return new AgendaRow(Kind.HEADER, day, tone, label, null, null, null, false, 0);
    }

    static AgendaRow task(String time, String title, String duration, boolean blocks) {
        return new AgendaRow(Kind.TASK, null, null, null, time, title, duration, blocks, 0);
    }

    static AgendaRow deadline(String dateLabel) {
        return new AgendaRow(Kind.DEADLINE, null, Tone.OVERDUE, dateLabel, null, null, null, false, 0);
    }

    static AgendaRow undated(int count) {
        return new AgendaRow(Kind.UNDATED, null, Tone.NORMAL, null, null, null, null, false, count);
    }
}
