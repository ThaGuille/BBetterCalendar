package com.example.bbettercalendar.configuration;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

@Dao
public interface ConfigurationDAO {

    @Insert
    void insert(Configuration configuration);

    @Update
    void update(Configuration configuration);

    @Delete
    void delete(Configuration configuration);

    @Query("SELECT * FROM configuration LIMIT 1")
    Configuration getConfiguration();
}
