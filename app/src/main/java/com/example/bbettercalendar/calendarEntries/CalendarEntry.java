package com.example.bbettercalendar.calendarEntries;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.io.Serializable;
import java.util.Calendar;

@Entity(tableName = "calendarEntry")
public class CalendarEntry {

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
    }
    public CalendarEntry(){}
}
