package com.thorefuglestad.timer;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "timeregistrering_prefs";
    public static final String KEY_NORMAL_MINUTTER = "normal_arbeidstid_minutter";
    public static final int STANDARD_MINUTTER = 480; // 8 timer

    private TextInputEditText etNormalTimer, etTestMnd;
    private int testAr = -1, testMnd = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        etNormalTimer = findViewById(R.id.etNormalTimer);
        etTestMnd     = findViewById(R.id.etTestMnd);
        Button btnLagreTimer      = findViewById(R.id.btnLagreTimer);
        Button btnGenererTestdata  = findViewById(R.id.btnGenererTestdata);
        Button btnSlettTestdata    = findViewById(R.id.btnSlettTestdata);
        Button btnSlettAlt         = findViewById(R.id.btnSlettAlt);
        Button btnEksporterAlt     = findViewById(R.id.btnEksporterAlt);
        Button btnEksporterMnd     = findViewById(R.id.btnEksporterMnd);
        ImageButton btnTilbake    = findViewById(R.id.btnTilbake);

        btnTilbake.setOnClickListener(v -> finish());

        // Vis lagret verdi (standard 8 timer)
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int lagretMinutter = prefs.getInt(KEY_NORMAL_MINUTTER, STANDARD_MINUTTER);
        etNormalTimer.setText(String.valueOf(lagretMinutter / 60));

        btnLagreTimer.setOnClickListener(v -> {
            String tekst = etNormalTimer.getText() != null ? etNormalTimer.getText().toString().trim() : "";
            if (tekst.isEmpty()) {
                Toast.makeText(this, "Fyll inn antall timer", Toast.LENGTH_SHORT).show();
                return;
            }
            int timer = Integer.parseInt(tekst);
            prefs.edit().putInt(KEY_NORMAL_MINUTTER, timer * 60).apply();
            Toast.makeText(this, "Normal arbeidstid satt til " + timer + " timer", Toast.LENGTH_SHORT).show();
        });

        etTestMnd.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(this, (picker, year, month, day) -> {
                testAr  = year;
                testMnd = month;
                etTestMnd.setText(String.format(Locale.getDefault(), "%02d.%04d", month + 1, year));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), 1).show();
        });

        btnGenererTestdata.setOnClickListener(v -> {
            if (testAr < 0) {
                Toast.makeText(this, "Velg en måned først", Toast.LENGTH_SHORT).show();
                return;
            }
            bekreftTestdata();
        });

        btnSlettTestdata.setOnClickListener(v -> {
            if (testAr < 0) {
                Toast.makeText(this, "Velg en måned først", Toast.LENGTH_SHORT).show();
                return;
            }
            String mndNavn = String.format(Locale.getDefault(), "%02d.%04d", testMnd + 1, testAr);
            new AlertDialog.Builder(this)
                    .setTitle("Slett alle vakter")
                    .setMessage("Er du sikker på at du vil slette alle vakter for " + mndNavn + "?")
                    .setPositiveButton("Slett", (dialog, which) -> slettManed())
                    .setNegativeButton("Avbryt", null)
                    .show();
        });

        btnEksporterAlt.setOnClickListener(v ->
                Executors.newSingleThreadExecutor().execute(() -> {
                    List<WorkDay> alle = AppDatabase.getInstance(this).workDayDao().getAllWorkDays();
                    eksporter(alle, "vakter_alle");
                }));

        btnEksporterMnd.setOnClickListener(v -> {
            if (testAr < 0) {
                Toast.makeText(this, "Velg en måned først", Toast.LENGTH_SHORT).show();
                return;
            }
            int ar = testAr, mnd = testMnd;
            Executors.newSingleThreadExecutor().execute(() -> {
                String startDato = String.format(Locale.getDefault(), "%04d-%02d-01", ar, mnd + 1);
                Calendar cal = Calendar.getInstance();
                cal.set(ar, mnd, 1);
                String sluttDato = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                        ar, mnd + 1, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
                List<WorkDay> liste = AppDatabase.getInstance(this).workDayDao()
                        .getByDateRange(startDato, sluttDato);
                eksporter(liste, String.format(Locale.getDefault(), "vakter_%04d_%02d", ar, mnd + 1));
            });
        });

        btnSlettAlt.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Slett all data")
                        .setMessage("Dette vil slette alle registrerte vakter permanent. Er du helt sikker?")
                        .setPositiveButton("Slett alt", (dialog, which) ->
                                Executors.newSingleThreadExecutor().execute(() -> {
                                    AppDatabase.getInstance(this).workDayDao().slettAlt();
                                    runOnUiThread(() -> Toast.makeText(this,
                                            "All data er slettet", Toast.LENGTH_SHORT).show());
                                }))
                        .setNegativeButton("Avbryt", null)
                        .show());

    }

    private void bekreftTestdata() {
        String mndNavn = String.format(Locale.getDefault(), "%02d.%04d", testMnd + 1, testAr);
        new AlertDialog.Builder(this)
                .setTitle("Generer testdata")
                .setMessage("Vil du generere vakter for alle hverdager i " + mndNavn + "?")
                .setPositiveButton("Generer", (dialog, which) -> genererTestdata())
                .setNegativeButton("Avbryt", null)
                .show();
    }

    private void genererTestdata() {
        int ar  = testAr;
        int mnd = testMnd;
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int normalMinutter = prefs.getInt(KEY_NORMAL_MINUTTER, STANDARD_MINUTTER);

        Executors.newSingleThreadExecutor().execute(() -> {
            Random rnd = new Random();
            WorkDayDao dao = AppDatabase.getInstance(this).workDayDao();

            Calendar cal = Calendar.getInstance();
            cal.set(ar, mnd, 1, 0, 0, 0);
            cal.set(Calendar.MILLISECOND, 0);
            int dagerIMnd = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
            int antall = 0;

            for (int dag = 1; dag <= dagerIMnd; dag++) {
                cal.set(Calendar.DAY_OF_MONTH, dag);
                int ukedag = cal.get(Calendar.DAY_OF_WEEK);
                if (ukedag == Calendar.SATURDAY || ukedag == Calendar.SUNDAY) continue;

                // Starttid mellom 07:30 og 08:30
                cal.set(Calendar.HOUR_OF_DAY, 8);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                int startAvvik = rnd.nextInt(61) - 30; // -30 til +30 min
                long startMs = cal.getTimeInMillis() + startAvvik * 60_000L;

                // Varighet: normalMinutter ± opptil 60 min
                int varighetMin = normalMinutter + rnd.nextInt(121) - 60;
                long sluttMs = startMs + varighetMin * 60_000L;

                String dato = String.format(Locale.getDefault(), "%04d-%02d-%02d", ar, mnd + 1, dag);
                WorkDay vakt = new WorkDay();
                vakt.date             = dato;
                vakt.startTime        = startMs;
                vakt.endTime          = sluttMs;
                vakt.manualAdjustment = 0;
                dao.insert(vakt);
                antall++;
            }

            int ferdig = antall;
            runOnUiThread(() -> {
                Toast.makeText(this, ferdig + " vakter generert", Toast.LENGTH_SHORT).show();
                etTestMnd.setText("");
                testAr = testMnd = -1;
            });
        });
    }

    private void eksporter(List<WorkDay> vakter, String filnavn) {
        SimpleDateFormat tidSdf = new SimpleDateFormat("HH:mm", new Locale("no", "NO"));
        try {
            File dir = new File(getCacheDir(), "eksport");
            dir.mkdirs();
            File fil = new File(dir, filnavn + ".csv");

            FileWriter fw = new FileWriter(fil);
            fw.write("Dato;Starttid;Sluttid;Varighet (min);Manuell justering (min)\n");
            for (WorkDay v : vakter) {
                String start = tidSdf.format(new Date(v.startTime));
                String slutt = v.endTime != null ? tidSdf.format(new Date(v.endTime)) : "";
                long varighet = v.endTime != null ? v.getDurationInMinutes() : 0;
                fw.write(v.date + ";" + start + ";" + slutt + ";" + varighet + ";" + v.manualAdjustment + "\n");
            }
            fw.close();

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", fil);
            Intent del = new Intent(Intent.ACTION_SEND);
            del.setType("text/csv");
            del.putExtra(Intent.EXTRA_STREAM, uri);
            del.putExtra(Intent.EXTRA_SUBJECT, filnavn);
            del.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            runOnUiThread(() -> startActivity(Intent.createChooser(del, "Eksporter CSV")));
        } catch (IOException e) {
            runOnUiThread(() -> Toast.makeText(this, "Eksport feilet: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private void slettManed() {
        int ar  = testAr;
        int mnd = testMnd;
        Executors.newSingleThreadExecutor().execute(() -> {
            Calendar cal = Calendar.getInstance();
            cal.set(ar, mnd, 1);
            String startDato = String.format(Locale.getDefault(), "%04d-%02d-01", ar, mnd + 1);
            String sluttDato = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                    ar, mnd + 1, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
            AppDatabase.getInstance(this).workDayDao().slettAlleIDatoIntervall(startDato, sluttDato);
            runOnUiThread(() -> {
                String mndNavn = String.format(Locale.getDefault(), "%02d.%04d", mnd + 1, ar);
                Toast.makeText(this, "Alle vakter for " + mndNavn + " er slettet", Toast.LENGTH_SHORT).show();
                etTestMnd.setText("");
                testAr = testMnd = -1;
            });
        });
    }

}
