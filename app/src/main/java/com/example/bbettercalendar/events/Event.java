package com.example.bbettercalendar.events;

import android.location.Location;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;
import java.util.Date;

@Entity
public class Event {

    /** Esto tendrá que ser serializable para guardarlo, y usar patrón builder para su creación**/
    @PrimaryKey(autoGenerate = true)
    private int id;

    private String title;
    private String description;
    private Date limitDate;

    //todo Guillem -> añadir el resto de atributos
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

    public static class EventBuilder implements Serializable {
        private int id;
        private String title;
        private String description;
        private Date limitDate;

        public EventBuilder(){}  //hacer constructor con lo mínimo obligatiorio (title, description, limitDate)

        public EventBuilder setEventId(int id){
            this.id = id;
            return this;
        }
        public EventBuilder setEventName(String title){
            this.title = title;
            return this;
        }
        public EventBuilder setEventType(String description) {
            this.description = description;
            return this;
        }
        public EventBuilder setEventUrl(Date limitDate) {
            this.limitDate = limitDate;
            return this;
        }
        // todo añadir el resto de setters

        public Event build(){
            return new Event(this);
        }
    }

    public Event(EventBuilder builder){
        this.id = builder.id;
        this.title = builder.title;
        this.description = builder.description;
        this.limitDate = builder.limitDate;
        // todo añadir el resto de setters
    }
}
