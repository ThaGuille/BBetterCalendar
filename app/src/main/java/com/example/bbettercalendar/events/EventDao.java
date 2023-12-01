package com.example.bbettercalendar.events;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.bbettercalendar.events.Event;

import java.util.List;

@Dao
public interface EventDao {
    @Insert
    void insert(Event event);

    @Update
    void update(Event event);

    @Delete
    void delete(Event event);

    @Query("SELECT * FROM event")
    List<Event> getAllEvents();

    @Query("SELECT * FROM event WHERE id = :id")
    Event getEventById(int id);

    @Query("SELECT * FROM event WHERE limitDate = :date")
    List<Event> getEventsByDate(String date);
}