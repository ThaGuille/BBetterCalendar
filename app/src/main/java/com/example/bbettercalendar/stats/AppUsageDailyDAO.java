package com.example.bbettercalendar.stats;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

// mejora-visual u1 (DB v15): histórico diario de uso por app (tabla app_usage_daily). Síncronas:
// se llaman desde el executor (regla #3). Los días son fechas ISO "YYYY-MM-DD" y los rangos son
// inclusivos en los dos extremos (BETWEEN), igual que DailyStatDAO.getRange.
@Dao
public interface AppUsageDailyDAO {

    // REPLACE sobre la PK (day, packageName) -> repetir la instantánea de un día no duplica filas.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<AppUsageDaily> rows);

    /** Todas las filas del rango, por día y luego por paquete. */
    @Query("SELECT * FROM app_usage_daily WHERE day BETWEEN :startDay AND :endDay "
            + "ORDER BY day, packageName")
    List<AppUsageDaily> getBetween(String startDay, String endDay);

    /** Filas de UNA app en el rango, por día (los días sin fila no tuvieron uso guardado). */
    @Query("SELECT * FROM app_usage_daily WHERE packageName = :packageName "
            + "AND day BETWEEN :startDay AND :endDay ORDER BY day")
    List<AppUsageDaily> getForPackage(String packageName, String startDay, String endDay);

    /** Minutos de todas las apps sumados por día dentro del rango (días sin filas no salen). */
    @Query("SELECT day, COALESCE(SUM(minutes), 0) AS minutes FROM app_usage_daily "
            + "WHERE day BETWEEN :startDay AND :endDay GROUP BY day ORDER BY day")
    List<DayMinutes> getDayTotals(String startDay, String endDay);
}
