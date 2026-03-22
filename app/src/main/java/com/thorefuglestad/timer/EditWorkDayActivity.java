package com.thorefuglestad.timer;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

public class EditWorkDayActivity extends AppCompatActivity {

    private EditText etDato, etStartTid, etSluttTid, etJustering;
    private Spinner spinnerKategori;

    // Arbeidsdagen som redigeres, lastet fra databasen
    private WorkDay workDay;

    // Valgte tidsverdier brukes når vi bygger Calendar for lagring
    private int selectedYear, selectedMonth, selectedDay;
    private int startHour, startMinute;
    private int endHour, endMinute;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);

        etDato = findViewById(R.id.etDato);
        etStartTid = findViewById(R.id.etStartTid);
        etSluttTid = findViewById(R.id.etSluttTid);
        etJustering = findViewById(R.id.etJustering);
        spinnerKategori = findViewById(R.id.spinnerKategori);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.kategorier, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerKategori.setAdapter(adapter);
        Button btnLagre = findViewById(R.id.btnLagre);
        Button btnSlett = findViewById(R.id.btnSlett);

        int workdayId = getIntent().getIntExtra("workday_id", -1);

        // Last arbeidsdagen fra databasen og fyll inn feltene
        Executors.newSingleThreadExecutor().execute(() -> {
            workDay = AppDatabase.getInstance(this).workDayDao().getById(workdayId);

            runOnUiThread(() -> {
                if (workDay == null) {
                    Toast.makeText(this, "Fant ikke registreringen", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                fyllInnFelter();
            });
        });

        btnLagre.setOnClickListener(v -> lagreEndringer());
        btnSlett.setOnClickListener(v -> bekreftSletting());
    }

    // Viser bekreftelsesdialog før sletting
    private void bekreftSletting() {
        new AlertDialog.Builder(this)
                .setTitle("Slett vakt")
                .setMessage("Er du sikker på at du vil slette denne vakten?")
                .setPositiveButton("Slett", (dialog, which) -> slettVakt())
                .setNegativeButton("Avbryt", null)
                .show();
    }

    private void slettVakt() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase.getInstance(this).workDayDao().delete(workDay);
            runOnUiThread(() -> {
                Toast.makeText(this, "Vakt slettet", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    // Fyller feltene med eksisterende data fra arbeidsdagen
    private void fyllInnFelter() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());

        // Dato
        etDato.setText(workDay.date);
        Calendar cal = Calendar.getInstance();
        String[] datoDeler = workDay.date.split("-");
        selectedYear = Integer.parseInt(datoDeler[0]);
        selectedMonth = Integer.parseInt(datoDeler[1]) - 1;
        selectedDay = Integer.parseInt(datoDeler[2]);

        // Starttid
        cal.setTimeInMillis(workDay.startTime);
        startHour = cal.get(Calendar.HOUR_OF_DAY);
        startMinute = cal.get(Calendar.MINUTE);
        etStartTid.setText(sdf.format(new Date(workDay.startTime)));

        // Sluttid (kan være null hvis vakten pågår)
        if (workDay.endTime != null) {
            cal.setTimeInMillis(workDay.endTime);
            endHour = cal.get(Calendar.HOUR_OF_DAY);
            endMinute = cal.get(Calendar.MINUTE);
            etSluttTid.setText(sdf.format(new Date(workDay.endTime)));
        }

        // Justering
        etJustering.setText(String.valueOf(workDay.manualAdjustment));

        // Kategori — forhåndsvelg hvis satt
        if (workDay.category != null) {
            ArrayAdapter<CharSequence> spinnerAdapter =
                    (ArrayAdapter<CharSequence>) spinnerKategori.getAdapter();
            int pos = spinnerAdapter.getPosition(workDay.category);
            if (pos >= 0) spinnerKategori.setSelection(pos);
        }

        // Klikk-lyttere for dato og klokkeslett
        etDato.setOnClickListener(v -> {
            new DatePickerDialog(this, (picker, year, month, day) -> {
                selectedYear = year;
                selectedMonth = month;
                selectedDay = day;
                etDato.setText(String.format("%04d-%02d-%02d", year, month + 1, day));
            }, selectedYear, selectedMonth, selectedDay).show();
        });

        etStartTid.setOnClickListener(v -> {
            new TimePickerDialog(this, (picker, hour, minute) -> {
                startHour = hour;
                startMinute = minute;
                etStartTid.setText(String.format("%02d:%02d", hour, minute));
            }, startHour, startMinute, true).show();
        });

        etSluttTid.setOnClickListener(v -> {
            new TimePickerDialog(this, (picker, hour, minute) -> {
                endHour = hour;
                endMinute = minute;
                etSluttTid.setText(String.format("%02d:%02d", hour, minute));
            }, endHour, endMinute, true).show();
        });
    }

    // Lagrer endringene i databasen
    private void lagreEndringer() {
        Calendar start = Calendar.getInstance();
        start.set(selectedYear, selectedMonth, selectedDay, startHour, startMinute, 0);
        start.set(Calendar.MILLISECOND, 0);

        workDay.date = etDato.getText().toString();
        workDay.startTime = start.getTimeInMillis();

        // Sett sluttid kun hvis feltet er fylt inn
        if (!etSluttTid.getText().toString().isEmpty()) {
            Calendar slutt = Calendar.getInstance();
            slutt.set(selectedYear, selectedMonth, selectedDay, endHour, endMinute, 0);
            slutt.set(Calendar.MILLISECOND, 0);
            workDay.endTime = slutt.getTimeInMillis();
        }

        String justeringTekst = etJustering.getText().toString().trim();
        workDay.manualAdjustment = justeringTekst.isEmpty() ? 0 : Long.parseLong(justeringTekst);
        workDay.category = spinnerKategori.getSelectedItem().toString();

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase.getInstance(this).workDayDao().update(workDay);
            runOnUiThread(() -> {
                Toast.makeText(this, "Lagret", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }
}
