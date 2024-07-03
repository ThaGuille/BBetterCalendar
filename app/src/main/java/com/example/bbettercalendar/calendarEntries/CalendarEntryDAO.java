package com.example.bbettercalendar.calendarEntries;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface CalendarEntryDAO {
    @Insert
    void insert(CalendarEntry calendarEntry);

    @Update
    void update(CalendarEntry calendarEntry);

    @Delete
    void delete(CalendarEntry calendarEntry);

    @Query("SELECT * FROM CalendarEntry")
    List<CalendarEntry> getAllEvents();

    @Query("SELECT * FROM CalendarEntry WHERE id = :id")
    CalendarEntry getEventById(int id);

    @Query("SELECT * FROM CalendarEntry WHERE limitDate = :date")
    List<CalendarEntry> getEventsByDate(String date);

    @Query("SELECT * FROM CalendarEntry WHERE title = :title")
    List<CalendarEntry> getEventsByTitle(String title);
}