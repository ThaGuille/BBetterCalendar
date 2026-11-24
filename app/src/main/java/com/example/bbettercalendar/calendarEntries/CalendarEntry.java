package com.example.bbettercalendar.calendarEntries;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.io.Serializable;
import java.util.Calendar;

@Entity(tableName = "calendarEntry")
public class CalendarEntry {

    // rediseno-proyectos (DB v16): valores de priority. La prioridad solo se dice con color en la
    // fila (D-P03); el selector y el orden «Prioridad primero» la dicen con palabras.
    public static final int PRIORITY_NONE = 0;
    public static final int PRIORITY_LOW = 1;
    public static final int PRIORITY_MEDIUM = 2;
    public static final int PRIORITY_HIGH = 3;

    /** Esto tendrá que ser serializable para guardarlo, y usar patrón builder para su creación**/
    @PrimaryKey(autoGenerate = true)
    private int id;

    private String title;
    private String description;
    private String limitDate;
    private Calendar startDayAndHour;
    private Calendar endDayAndHour;
    // Mirror of startDayAndHour/endDayAndHour as epoch millis. Indexable for date-range queries
    // since the Calendar columns are stored as Gson JSON strings (not orderable).
    private long startMillis;
    private long endMillis;
    private boolean[] notifications = new boolean[7];

    //Exclusivos de tareas
    private int repetition;
    // Recurrencia (spec tasks-recurrence). repetition sigue siendo el ordinal base
    // (RepetitionOptions.NONE/DAILY/WEEKLY/MONTHLY); estos campos lo detallan:
    //   repetitionInterval -> "cada X días" cuando repetition == DAILY
    //   repetitionDays     -> bitmask Lun..Dom (bit 0 = lunes) cuando repetition == WEEKLY
    private int repetitionInterval = 1;
    private int repetitionDays;
    private int duration;
    // Objetivo de minutos de concentración para una tarea (spec focus-attribution). 0 = sin objetivo.
    // Distinto de `duration` (que guarda una duración de evento en MILISEGUNDOS y es prácticamente
    // de sólo escritura): targetMinutes se compara contra la suma de FocusEvent.durationMin
    // atribuidos a esta fila para auto-completarla.
    private int targetMinutes;
    private boolean isDone;
    // mejora-visual u1 (DB v15): instante en que se marcó hecha (epoch millis); 0 = no hecha. Lo
    // escribe SOLO CompletionStamp (los 4 sitios que marcan/desmarcan), nunca a mano. Las filas que
    // ya estaban hechas antes de v15 recibieron startMillis (o el instante de la migración).
    private long completedAtMillis;
    // Recurrencia (spec tasks-recurrence): una plantilla (isTemplate) es la definición de la
    // serie — conserva fecha ancla + hora pero se excluye de TODAS las queries de superficie
    // (nunca se pinta ni programa alarmas). Sus ocurrencias son filas propias con templateId
    // apuntando a ella. materializedUntilMillis = marca de agua por plantilla (top-up idempotente).
    private boolean isTemplate;
    private int templateId;
    private long materializedUntilMillis;
    // isDismissed = ocultada de las listas accionables SIN borrar (los datos se conservan para
    // estadísticas/gráficas futuras). Distinto de isDone: "deja de recordármela", no "completada".
    private boolean isDismissed;

    // 0 = tarea suelta (no pertenece a ningún proyecto); si no, el id del Project dueño
    // (spec projects-mvp). Los items de proyecto son CalendarEntry type=TASK normales.
    private int projectId;

    // spec recurrence-calendar-visibility: true = la fila NO se pinta en la pantalla de
    // Calendario, pero sigue apareciendo en las listas de Home. Pensado para series recurrentes
    // (una tarea diaria llenaba el mes entero), por eso las series nuevas nacen ocultas. Las
    // ocurrencias heredan el flag de su plantilla.
    private boolean hiddenInCalendar;

    // rediseno-proyectos (DB v16). Todos con su setter en EventBuilder; las copias de una fila
    // (ocurrencias del materializador, EventBuilder.copyTaskFieldsFrom) los conservan.
    //   priority       -> PRIORITY_NONE / LOW / MEDIUM / HIGH (0..3)
    //   tagId          -> Tag (kind etiqueta) de la tarea; 0 = ninguna
    //   sectionId      -> ProjectSection del proyecto; 0 = ninguna (se pinta en la primera)
    //   blockOnFocus   -> su sesión de Focus arma sola el modo bloqueo (bloqueo suave, D-P12)
    //   completionNote -> nota opcional al completarla; null = sin nota
    @ColumnInfo(defaultValue = "0")
    private int priority;
    @ColumnInfo(defaultValue = "0")
    private int tagId;
    @ColumnInfo(defaultValue = "0")
    private int sectionId;
    @ColumnInfo(defaultValue = "0")
    private boolean blockOnFocus;
    private String completionNote;

    // Transitorio (no persistido): nº de ocurrencias atrasadas que una fila colapsada representa
    // en la sección de atrasadas de Home. 0 = fila normal (tarea suelta o no colapsada).
    @Ignore
    private int seriesMissedCount;

    // Transitorio (no persistido): minutos de concentración ya atribuidos a esta fila
    // (suma de FocusEvent.durationMin con entryId = este id). Lo rellena el ViewModel al
    // enriquecer la lista para pintar el progreso "X/Ym"; no se guarda en la BD.
    @Ignore
    private int attributedMinutes;

    private int type;  //1 = evento, 2 = tarea, 3 = recordatorio

    //private boolean isRepeated;
    //private int daysRepeated;
    //private boolean isDone;
    //private int priority;

    //private Event[] subEvents;  //no tiene sentido que un evento tenga subeventos, pero una tarea sí
    //private boolean hasNotifications;
    //private Location location;
    //public Double[] location

    //private int category;
    //private int color;
    //private int durationMillis;

    public int getId() {return id;}
    public String getTitle() {return title;}
    public String getDescription() {return description;}
    public String getLimitDate() {return limitDate;}
    public Calendar getStartDayAndHour() {return startDayAndHour;}
    public Calendar getEndDayAndHour() {return endDayAndHour;}
    public boolean[] getNotifications() {return notifications;}
    public void setId(int id) {this.id = id;}
    public void setTitle(String title) {this.title = title;}
    public void setDescription(String description) {this.description = description;}
    public void setLimitDate(String limitDate) {this.limitDate = limitDate;}
    public void setStartDayAndHour(Calendar startDayAndHour) {this.startDayAndHour = startDayAndHour;}
    public void setEndDayAndHour(Calendar endDayAndHour) {this.endDayAndHour = endDayAndHour;}
    public void setNotifications(boolean[] notifications) {this.notifications = notifications;}
    public int getRepetition() {return repetition;}
    public void setRepetition(int repetition) {this.repetition = repetition;}
    public int getRepetitionInterval() {return repetitionInterval;}
    public void setRepetitionInterval(int repetitionInterval) {this.repetitionInterval = repetitionInterval;}
    public int getRepetitionDays() {return repetitionDays;}
    public void setRepetitionDays(int repetitionDays) {this.repetitionDays = repetitionDays;}
    public int getDuration() {return duration;}
    public void setDuration(int duration) {this.duration = duration;}
    public int getTargetMinutes() {return targetMinutes;}
    public void setTargetMinutes(int targetMinutes) {this.targetMinutes = targetMinutes;}
    public int getAttributedMinutes() {return attributedMinutes;}
    public void setAttributedMinutes(int attributedMinutes) {this.attributedMinutes = attributedMinutes;}
    public boolean isDone() {return isDone;}
    public void setDone(boolean done) {isDone = done;}
    public long getCompletedAtMillis() {return completedAtMillis;}
    public void setCompletedAtMillis(long completedAtMillis) {this.completedAtMillis = completedAtMillis;}
    public boolean isTemplate() {return isTemplate;}
    public void setTemplate(boolean template) {isTemplate = template;}
    public int getTemplateId() {return templateId;}
    public void setTemplateId(int templateId) {this.templateId = templateId;}
    public long getMaterializedUntilMillis() {return materializedUntilMillis;}
    public void setMaterializedUntilMillis(long materializedUntilMillis) {this.materializedUntilMillis = materializedUntilMillis;}
    public boolean isDismissed() {return isDismissed;}
    public void setDismissed(boolean dismissed) {isDismissed = dismissed;}
    public int getSeriesMissedCount() {return seriesMissedCount;}
    public void setSeriesMissedCount(int seriesMissedCount) {this.seriesMissedCount = seriesMissedCount;}
    public int getType() {return type;}
    public void setType(int type) {this.type = type;}
    public long getStartMillis() {return startMillis;}
    public void setStartMillis(long startMillis) {this.startMillis = startMillis;}
    public long getEndMillis() {return endMillis;}
    public void setEndMillis(long endMillis) {this.endMillis = endMillis;}
    public int getProjectId() {return projectId;}
    public void setProjectId(int projectId) {this.projectId = projectId;}
    public boolean isHiddenInCalendar() {return hiddenInCalendar;}
    public void setHiddenInCalendar(boolean hiddenInCalendar) {this.hiddenInCalendar = hiddenInCalendar;}
    public int getPriority() {return priority;}
    public void setPriority(int priority) {this.priority = priority;}
    public int getTagId() {return tagId;}
    public void setTagId(int tagId) {this.tagId = tagId;}
    public int getSectionId() {return sectionId;}
    public void setSectionId(int sectionId) {this.sectionId = sectionId;}
    public boolean isBlockOnFocus() {return blockOnFocus;}
    public void setBlockOnFocus(boolean blockOnFocus) {this.blockOnFocus = blockOnFocus;}
    public String getCompletionNote() {return completionNote;}
    public void setCompletionNote(String completionNote) {this.completionNote = completionNote;}

    public static class EventBuilder implements Serializable {
        private int id;
        private String title;
        private String description;
        private String limitDate;
        private Calendar startDayAndHour = null;
        private Calendar endDayAndHour = null;
        private boolean[] notifications = new boolean[7];
        private int repetition;
        private int repetitionInterval = 1;
        private int repetitionDays;
        private int duration;
        private int targetMinutes;
        private boolean isDone;
        private boolean isTemplate;
        private int templateId;
        private int type;
        private int projectId;
        private boolean hiddenInCalendar;
        private int priority;
        private int tagId;
        private int sectionId;
        private boolean blockOnFocus;
        private String completionNote;

        public EventBuilder(){}  //hacer constructor con lo mínimo obligatiorio (title, description, limitDate)

        public EventBuilder setEventId(int id){
            this.id = id;
            return this;
        }
        public EventBuilder setEventTitle(String title){
            this.title = title;
            return this;
        }
        public String getEventTitle() {return title;}

        public EventBuilder setEventDescription(String description) {
            this.description = description;
            return this;
        }
        public String getEventDescription() {return description;}

        public EventBuilder setEventDate(String limitDate) {
            this.limitDate = limitDate;
            return this;
        }
        public String getEventDate() {return limitDate;}

        public EventBuilder setEventStartDay(Calendar startDayAndHour) {
            if (this.startDayAndHour == null) {
                this.startDayAndHour = Calendar.getInstance();
            }
            this.startDayAndHour.set(Calendar.DAY_OF_MONTH, startDayAndHour.get(Calendar.DAY_OF_MONTH));
            this.startDayAndHour.set(Calendar.MONTH, startDayAndHour.get(Calendar.MONTH));
            this.startDayAndHour.set(Calendar.YEAR, startDayAndHour.get(Calendar.YEAR));
            return this;
        }
        public Calendar getEventStartDayAndHour() {return startDayAndHour;}
        public Calendar getEventEndDayAndHour() {return endDayAndHour;}

        public EventBuilder setEventEndDay(Calendar endDayAndHour) {
            if (this.endDayAndHour == null) {
                this.endDayAndHour = Calendar.getInstance();
            }
            this.endDayAndHour.set(Calendar.DAY_OF_MONTH, endDayAndHour.get(Calendar.DAY_OF_MONTH));
            this.endDayAndHour.set(Calendar.MONTH, endDayAndHour.get(Calendar.MONTH));
            this.endDayAndHour.set(Calendar.YEAR, endDayAndHour.get(Calendar.YEAR));
            return this;
        }
        public EventBuilder setEventStartHour(Calendar startDayAndHour) {
            if (this.startDayAndHour == null) {
                this.startDayAndHour = Calendar.getInstance();
            }
            this.startDayAndHour.set(Calendar.HOUR_OF_DAY, startDayAndHour.get(Calendar.HOUR_OF_DAY));
            this.startDayAndHour.set(Calendar.MINUTE, startDayAndHour.get(Calendar.MINUTE));
            return this;
        }
        public EventBuilder setEventEndHour(Calendar endDayAndHour) {
            if (this.endDayAndHour == null) {
                this.endDayAndHour = Calendar.getInstance();
            }
            this.endDayAndHour.set(Calendar.HOUR_OF_DAY, endDayAndHour.get(Calendar.HOUR_OF_DAY));
            this.endDayAndHour.set(Calendar.MINUTE, endDayAndHour.get(Calendar.MINUTE));
            return this;
        }
        public EventBuilder setEventStartDayAndHour(Calendar startDayAndHour) {
            this.startDayAndHour = startDayAndHour;
            return this;
        }
        public EventBuilder setEventEndDayAndHour(Calendar endDayAndHour) {
            this.endDayAndHour = endDayAndHour;
            return this;
        }
        public EventBuilder setEventNotifications(boolean[] notifications) {
            this.notifications = notifications;
            return this;
        }
        public boolean[] getEventNotifications() {return notifications;}

        public EventBuilder setEventRepetition(int repetition) {
            this.repetition = repetition;
            return this;
        }
        public int getEventRepetition() {return repetition;}
        public EventBuilder setEventRepetitionInterval(int repetitionInterval) {
            this.repetitionInterval = repetitionInterval;
            return this;
        }
        public EventBuilder setEventRepetitionDays(int repetitionDays) {
            this.repetitionDays = repetitionDays;
            return this;
        }
        public EventBuilder setEventIsTemplate(boolean isTemplate) {
            this.isTemplate = isTemplate;
            return this;
        }
        public EventBuilder setEventTemplateId(int templateId) {
            this.templateId = templateId;
            return this;
        }
        public EventBuilder setEventDuration(int duration) {
            this.duration = duration;
            return this;
        }
        public EventBuilder setEventTargetMinutes(int targetMinutes) {
            this.targetMinutes = targetMinutes;
            return this;
        }
        public int getEventTargetMinutes() {return targetMinutes;}
        public EventBuilder setEventIsDone(boolean isDone) {
            this.isDone = isDone;
            return this;
        }
        public EventBuilder setEventType(int type) {
            this.type = type;
            return this;
        }
        public EventBuilder setEventProjectId(int projectId) {
            this.projectId = projectId;
            return this;
        }
        public int getEventProjectId() {return projectId;}

        public EventBuilder setEventHiddenInCalendar(boolean hiddenInCalendar) {
            this.hiddenInCalendar = hiddenInCalendar;
            return this;
        }
        public boolean getEventHiddenInCalendar() {return hiddenInCalendar;}

        // rediseno-proyectos (DB v16): un setter por campo nuevo.
        public EventBuilder setPriority(int priority) {
            this.priority = priority;
            return this;
        }
        public int getPriority() {return priority;}

        public EventBuilder setTagId(int tagId) {
            this.tagId = tagId;
            return this;
        }
        public int getTagId() {return tagId;}

        public EventBuilder setSectionId(int sectionId) {
            this.sectionId = sectionId;
            return this;
        }
        public int getSectionId() {return sectionId;}

        public EventBuilder setBlockOnFocus(boolean blockOnFocus) {
            this.blockOnFocus = blockOnFocus;
            return this;
        }
        public boolean getBlockOnFocus() {return blockOnFocus;}

        public EventBuilder setCompletionNote(String completionNote) {
            this.completionNote = completionNote;
            return this;
        }
        public String getCompletionNote() {return completionNote;}

        /**
         * Copia en este builder los cinco campos de tarea de la v16 (prioridad, etiqueta, sección,
         * bloqueo al enfocar y nota al completar) de {@code source}. Lo usa toda copia de una fila
         * que se reconstruye con el builder (ocurrencias del materializador; un guardado que reescriba
         * una tarea existente), para que no los pierda volviendo a los valores por defecto.
         */
        public EventBuilder copyTaskFieldsFrom(@NonNull CalendarEntry source) {
            this.priority = source.getPriority();
            this.tagId = source.getTagId();
            this.sectionId = source.getSectionId();
            this.blockOnFocus = source.isBlockOnFocus();
            this.completionNote = source.getCompletionNote();
            return this;
        }

        public CalendarEntry build(){
            return new CalendarEntry(this);
        }
    }

    public CalendarEntry(EventBuilder builder){
        this.id = builder.id;
        this.title = builder.title;
        this.description = builder.description;
        this.limitDate = builder.limitDate;
        this.startDayAndHour = builder.startDayAndHour;
        this.endDayAndHour = builder.endDayAndHour;
        this.startMillis = builder.startDayAndHour != null ? builder.startDayAndHour.getTimeInMillis() : 0L;
        this.endMillis = builder.endDayAndHour != null ? builder.endDayAndHour.getTimeInMillis() : this.startMillis;
        this.notifications = builder.notifications;
        this.repetition = builder.repetition;
        this.repetitionInterval = builder.repetitionInterval;
        this.repetitionDays = builder.repetitionDays;
        this.duration = builder.duration;
        this.targetMinutes = builder.targetMinutes;
        this.isDone = builder.isDone;
        this.isTemplate = builder.isTemplate;
        this.templateId = builder.templateId;
        this.type = builder.type;
        this.projectId = builder.projectId;
        this.hiddenInCalendar = builder.hiddenInCalendar;
        this.priority = builder.priority;
        this.tagId = builder.tagId;
        this.sectionId = builder.sectionId;
        this.blockOnFocus = builder.blockOnFocus;
        this.completionNote = builder.completionNote;
    }
    public CalendarEntry(){}
}
