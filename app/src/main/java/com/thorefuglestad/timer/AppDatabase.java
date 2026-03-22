package com.thorefuglestad.timer;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

// Registrerer hvilke entiteter databasen inneholder, og setter versjonsnummeret
@Database(entities = {WorkDay.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    // Abstrakt metode Room implementerer automatisk
    public abstract WorkDayDao workDayDao();

    // Singleton-instansen — volatile sikrer at endringer er synlige på tvers av tråder
    private static volatile AppDatabase INSTANCE;

    // Returnerer den ene databaseinstansen — oppretter den hvis den ikke finnes ennå
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                // Dobbeltsjekk inne i synchronized-blokken for trådsikkerhet
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "timeregistrering_database"
                            )
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
