package com.thorefuglestad.timer;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

// Forteller Room at denne klassen representerer en tabell i databasen
@Entity(tableName = "work_days")
public class WorkDay {

    // Primærnøkkel som genereres automatisk
    @PrimaryKey(autoGenerate = true)
    public int id;

    // Dato for arbeidsdagen, f.eks. "2023-10-27"
    public String date;

    // Starttidspunkt i millisekunder (System.currentTimeMillis())
    public long startTime;

    // Sluttidspunkt i millisekunder — null betyr at vakten fortsatt pågår
    public Long endTime;

    // Manuell justering i minutter (kan være positiv eller negativ)
    public long manualAdjustment;

    // Regner ut total varighet i minutter
    // Returnerer 0 hvis vakten ikke er avsluttet ennå
    public long getDurationInMinutes() {
        if (endTime == null) {
            return 0;
        }
        long durationMillis = endTime - startTime;
        long durationMinutes = durationMillis / 1000 / 60;
        return durationMinutes + manualAdjustment;
    }
}
