package com.example.bbettercalendar.calendarEntries;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface CalendarEntryDAO {
    @Insert
    long insert(CalendarEntry calendarEntry);

    @Update
    void update(CalendarEntry calendarEntry);

    @Delete
    void delete(CalendarEntry calendarEntry);

    @Query("SELECT * FROM CalendarEntry")
    List<CalendarEntry> getAllEvents();

    @Query("SELECT * FROM CalendarEntry")
    LiveData<List<CalendarEntry>> observeAllEvents();

    @Query("SELECT * FROM CalendarEntry WHERE id = :id")
    CalendarEntry getEventById(int id);

    @Query("SELECT * FROM CalendarEntry WHERE limitDate = :date")
    List<CalendarEntry> getEventsByDate(String date);

    @Query("SELECT * FROM CalendarEntry WHERE title = :title")
    List<CalendarEntry> getEventsByTitle(String title);

    // Returns events whose start lies in [startMillis, endMillis] OR whose end lies in the range
    // OR which span across the entire range (multi-day events that started before and end after).
    @Query("SELECT * FROM CalendarEntry WHERE " +
            "(startMillis BETWEEN :startMillis AND :endMillis) OR " +
            "(endMillis BETWEEN :startMillis AND :endMillis) OR " +
            "(startMillis <= :startMillis AND endMillis >= :endMillis)")
    LiveData<List<CalendarEntry>> getEventsBetween(long startMillis, long endMillis);
}