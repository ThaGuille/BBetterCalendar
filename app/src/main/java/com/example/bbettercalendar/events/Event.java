package com.example.bbettercalendar.events;

import android.location.Location;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;

@Entity(tableName = "event")
public class Event {

    /** Esto tendrá que ser serializable para guardarlo, y usar patrón builder para su creación**/
    @PrimaryKey(autoGenerate = true)
    private int id;

    private String title;
    private String description;
    private String limitDate;
    private Calendar startDayAndHour;
    private Calendar endDayAndHour;
    private boolean[] notifications = new boolean[7];

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
    
    public static class EventBuilder implements Serializable {
        private int id;
        private String title;
        private String description;
        private String limitDate;
        private Calendar startDayAndHour = Calendar.getInstance();
        private Calendar endDayAndHour = Calendar.getInstance();
        private boolean[] notifications = new boolean[7];

        public EventBuilder(){}  //hacer constructor con lo mínimo obligatiorio (title, description, limitDate)

        public EventBuilder setEventId(int id){
            this.id = id;
            return this;
        }
        public EventBuilder setEventTitle(String title){
            this.title = title;
            return this;
        }
        public EventBuilder setEventDescription(String description) {
            this.description = description;
            return this;
        }
        public EventBuilder setEventDate(String limitDate) {
            this.limitDate = limitDate;
            return this;
        }
        public EventBuilder setEventStartDay(Calendar startDayAndHour) {
            this.startDayAndHour.set(Calendar.DAY_OF_MONTH, startDayAndHour.get(Calendar.DAY_OF_MONTH));
            this.startDayAndHour.set(Calendar.MONTH, startDayAndHour.get(Calendar.MONTH));
            this.startDayAndHour.set(Calendar.YEAR, startDayAndHour.get(Calendar.YEAR));
            return this;
        }
        public EventBuilder setEventEndDay(Calendar endDayAndHour) {
            this.endDayAndHour.set(Calendar.DAY_OF_MONTH, endDayAndHour.get(Calendar.DAY_OF_MONTH));
            this.endDayAndHour.set(Calendar.MONTH, endDayAndHour.get(Calendar.MONTH));
            this.endDayAndHour.set(Calendar.YEAR, endDayAndHour.get(Calendar.YEAR));
            return this;
        }
        public EventBuilder setEventStartHour(Calendar startDayAndHour) {
            this.startDayAndHour.set(Calendar.HOUR_OF_DAY, startDayAndHour.get(Calendar.HOUR_OF_DAY));
            this.startDayAndHour.set(Calendar.MINUTE, startDayAndHour.get(Calendar.MINUTE));
            return this;
        }
        public EventBuilder setEventEndHour(Calendar endDayAndHour) {
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
        public boolean[] getNotifications() {
            return notifications;
        }

        public Event build(){
            return new Event(this);
        }
    }

    public Event(EventBuilder builder){
        this.id = builder.id;
        this.title = builder.title;
        this.description = builder.description;
        this.limitDate = builder.limitDate;
        this.startDayAndHour = builder.startDayAndHour;
        this.endDayAndHour = builder.endDayAndHour;
        this.notifications = builder.notifications;

    }
    public Event(){}
}
