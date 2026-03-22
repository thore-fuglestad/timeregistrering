package com.thorefuglestad.timer;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

// DAO (Data Access Object) — definerer alle databaseoperasjoner for WorkDay
@Dao
public interface WorkDayDao {

    // Setter inn en ny arbeidsdag i databasen
    @Insert
    void insert(WorkDay workDay);

    // Oppdaterer en eksisterende rad (matcher på id)
    @Update
    void update(WorkDay workDay);

    // Sletter en rad fra databasen
    @Delete
    void delete(WorkDay workDay);

    // Henter alle arbeidsdager sortert etter dato (nyeste øverst)
    @Query("SELECT * FROM work_days ORDER BY startTime DESC")
    List<WorkDay> getAllWorkDays();

    // Finner en pågående vakt — altså en rad der sluttidspunktet ikke er satt
    // Returnerer null hvis ingen vakt pågår
    @Query("SELECT * FROM work_days WHERE endTime IS NULL LIMIT 1")
    WorkDay getOpenWorkDay();

    // Henter én spesifikk arbeidsdag basert på ID — brukes av redigeringsskjermen
    @Query("SELECT * FROM work_days WHERE id = :id LIMIT 1")
    WorkDay getById(int id);

    // Henter alle arbeidsdager for en gitt dato, nyeste vakt øverst
    @Query("SELECT * FROM work_days WHERE date = :dato ORDER BY startTime DESC")
    List<WorkDay> getByDate(String dato);

    // Henter arbeidsdager innenfor et datointervall — brukes av filterknappene
    @Query("SELECT * FROM work_days WHERE date BETWEEN :startDato AND :sluttDato ORDER BY startTime DESC")
    List<WorkDay> getByDateRange(String startDato, String sluttDato);

    // Sletter alle vakter for en gitt dato
    @Query("DELETE FROM work_days WHERE date = :dato")
    void slettAllePaDato(String dato);

    // Sletter alle vakter i et datointervall
    @Query("DELETE FROM work_days WHERE date BETWEEN :startDato AND :sluttDato")
    void slettAlleIDatoIntervall(String startDato, String sluttDato);

    // Henter alle arbeidsdager for en gitt kategori
    @Query("SELECT * FROM work_days WHERE category = :kategori ORDER BY startTime DESC")
    List<WorkDay> getByCategory(String kategori);

    // Henter arbeidsdager for en dato, filtrert på kategori
    @Query("SELECT * FROM work_days WHERE date = :dato AND category = :kategori ORDER BY startTime DESC")
    List<WorkDay> getByDateAndCategory(String dato, String kategori);

    // Henter arbeidsdager i et datointervall, filtrert på kategori
    @Query("SELECT * FROM work_days WHERE date BETWEEN :startDato AND :sluttDato AND category = :kategori ORDER BY startTime DESC")
    List<WorkDay> getByDateRangeAndCategory(String startDato, String sluttDato, String kategori);

    // Sletter alle vakter i databasen
    @Query("DELETE FROM work_days")
    void slettAlt();
}
