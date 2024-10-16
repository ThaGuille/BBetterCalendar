package com.example.bbettercalendar.calendarEntries;

import androidx.room.Entity;
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
    private boolean[] notifications = new boolean[7];

    //Exclusivos de tareas
    private int repetition;
    //todo faltan los días en los que se repite
    private int duration;
    private boolean isDone;

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
    public int getDuration() {return duration;}
    public void setDuration(int duration) {this.duration = duration;}
    public boolean isDone() {return isDone;}
    public void setDone(boolean done) {isDone = done;}
    public int getType() {return type;}
    public void setType(int type) {this.type = type;}
    
    public static class EventBuilder implements Serializable {
        private int id;
        private String title;
        private String description;
        private String limitDate;
        private Calendar startDayAndHour = null;
        private Calendar endDayAndHour = null;
        private boolean[] notifications = new boolean[7];
        private int repetition;
        private int duration;
        private boolean isDone;
        private int type;

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
        public EventBuilder setEventDuration(int duration) {
            this.duration = duration;
            return this;
        }
        public EventBuilder setEventIsDone(boolean isDone) {
            this.isDone = isDone;
            return this;
        }
        public EventBuilder setEventType(int type) {
            this.type = type;
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
        this.notifications = builder.notifications;
        this.repetition = builder.repetition;
        this.duration = builder.duration;
        this.isDone = builder.isDone;
        this.type = builder.type;
    }
    public CalendarEntry(){}
}
