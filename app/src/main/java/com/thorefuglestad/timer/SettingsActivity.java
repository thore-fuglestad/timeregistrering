package com.thorefuglestad.timer;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.textfield.TextInputEditText;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "timeregistrering_prefs";
    public static final String KEY_NORMAL_MINUTTER        = "normal_arbeidstid_minutter";       // legacy
    public static final String KEY_NORMAL_MINUTTER_JOBB   = "normal_arbeidstid_minutter_jobb";
    public static final String KEY_NORMAL_MINUTTER_PRIVAT = "normal_arbeidstid_minutter_privat";
    public static final String KEY_VALGT_KATEGORI         = "valgt_kategori_filter";
    public static final int STANDARD_MINUTTER = 480; // 8 timer

    /** Returnerer normal arbeidstid (minutter) for en gitt kategori. */
    public static int getNormalMinutter(android.content.SharedPreferences prefs, String category) {
        if ("Privat".equals(category))
            return prefs.getInt(KEY_NORMAL_MINUTTER_PRIVAT, STANDARD_MINUTTER);
        return prefs.getInt(KEY_NORMAL_MINUTTER_JOBB, STANDARD_MINUTTER);
    }

    private TextInputEditText etNormalTimerJobb, etNormalTimerPrivat, etTestMnd;
    private Spinner spinnerTestKategori, spinnerEksportKategori;
    private int testAr = -1, testMnd = -1;

    private final ActivityResultLauncher<String> filVelger =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) importerFraUri(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        etNormalTimerJobb   = findViewById(R.id.etNormalTimerJobb);
        etNormalTimerPrivat = findViewById(R.id.etNormalTimerPrivat);
        etTestMnd           = findViewById(R.id.etTestMnd);
        spinnerTestKategori = findViewById(R.id.spinnerTestKategori);
        ArrayAdapter<CharSequence> katAdapter = ArrayAdapter.createFromResource(
                this, R.array.kategorier, android.R.layout.simple_spinner_item);
        katAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTestKategori.setAdapter(katAdapter);

        spinnerEksportKategori = findViewById(R.id.spinnerEksportKategori);
        ArrayAdapter<CharSequence> eksportAdapter = ArrayAdapter.createFromResource(
                this, R.array.kategorier_filter, android.R.layout.simple_spinner_item);
        eksportAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEksportKategori.setAdapter(eksportAdapter);
        Button btnLagreTimer      = findViewById(R.id.btnLagreTimer);
        Button btnGenererTestdata  = findViewById(R.id.btnGenererTestdata);
        Button btnSlettTestdata    = findViewById(R.id.btnSlettTestdata);
        Button btnSlettAlt         = findViewById(R.id.btnSlettAlt);
        Button btnEksporterAlt     = findViewById(R.id.btnEksporterAlt);
        Button btnEksporterMnd     = findViewById(R.id.btnEksporterMnd);
        Button btnImporter         = findViewById(R.id.btnImporter);
        ImageButton btnTilbake    = findViewById(R.id.btnTilbake);

        btnTilbake.setOnClickListener(v -> finish());

        // Vis lagrede verdier
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        etNormalTimerJobb.setText(String.valueOf(
                prefs.getInt(KEY_NORMAL_MINUTTER_JOBB, STANDARD_MINUTTER) / 60));
        etNormalTimerPrivat.setText(String.valueOf(
                prefs.getInt(KEY_NORMAL_MINUTTER_PRIVAT, STANDARD_MINUTTER) / 60));

        btnLagreTimer.setOnClickListener(v -> {
            String jobbTekst   = etNormalTimerJobb.getText()   != null ? etNormalTimerJobb.getText().toString().trim()   : "";
            String privatTekst = etNormalTimerPrivat.getText() != null ? etNormalTimerPrivat.getText().toString().trim() : "";
            if (jobbTekst.isEmpty() || privatTekst.isEmpty()) {
                Toast.makeText(this, "Fyll inn timer for begge kategorier", Toast.LENGTH_SHORT).show();
                return;
            }
            int jobbTimer   = Integer.parseInt(jobbTekst);
            int privatTimer = Integer.parseInt(privatTekst);
            prefs.edit()
                    .putInt(KEY_NORMAL_MINUTTER_JOBB,   jobbTimer   * 60)
                    .putInt(KEY_NORMAL_MINUTTER_PRIVAT, privatTimer * 60)
                    .apply();
            Toast.makeText(this, "Lagret: Jobb " + jobbTimer + "t, Privat " + privatTimer + "t",
                    Toast.LENGTH_SHORT).show();
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

        btnEksporterAlt.setOnClickListener(v -> {
            String kat = eksportKategori();
            Executors.newSingleThreadExecutor().execute(() -> {
                WorkDayDao dao = AppDatabase.getInstance(this).workDayDao();
                List<WorkDay> liste = kat == null ? dao.getAllWorkDays() : dao.getByCategory(kat);
                String filnavn = kat == null ? "vakter_alle" : "vakter_alle_" + kat.toLowerCase();
                eksporter(liste, filnavn);
            });
        });

        btnEksporterMnd.setOnClickListener(v -> {
            if (testAr < 0) {
                Toast.makeText(this, "Velg en måned først", Toast.LENGTH_SHORT).show();
                return;
            }
            String kat = eksportKategori();
            int ar = testAr, mnd = testMnd;
            Executors.newSingleThreadExecutor().execute(() -> {
                String startDato = String.format(Locale.getDefault(), "%04d-%02d-01", ar, mnd + 1);
                Calendar cal = Calendar.getInstance();
                cal.set(ar, mnd, 1);
                String sluttDato = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                        ar, mnd + 1, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
                WorkDayDao dao = AppDatabase.getInstance(this).workDayDao();
                List<WorkDay> liste = kat == null
                        ? dao.getByDateRange(startDato, sluttDato)
                        : dao.getByDateRangeAndCategory(startDato, sluttDato, kat);
                String filnavn = String.format(Locale.getDefault(), "vakter_%04d_%02d", ar, mnd + 1)
                        + (kat != null ? "_" + kat.toLowerCase() : "");
                eksporter(liste, filnavn);
            });
        });

        btnImporter.setOnClickListener(v -> {
            String kat = eksportKategori();
            if (kat == null) {
                Toast.makeText(this, "Velg Jobb eller Privat før import", Toast.LENGTH_SHORT).show();
                return;
            }
            filVelger.launch("*/*");
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

    /** Returnerer null hvis "Alle" er valgt, ellers kategoristrengen. */
    private String eksportKategori() {
        String valg = spinnerEksportKategori.getSelectedItem().toString();
        return valg.equals("Alle") ? null : valg;
    }

    private void bekreftTestdata() {
        String mndNavn = String.format(Locale.getDefault(), "%02d.%04d", testMnd + 1, testAr);
        new AlertDialog.Builder(this)
                .setTitle("Generer testdata")
                .setMessage("Vil du generere vakter for alle hverdager i " + mndNavn + "?")
                .setPositiveButton("Generer", (dialog, which) ->
                        genererTestdata(spinnerTestKategori.getSelectedItem().toString()))
                .setNegativeButton("Avbryt", null)
                .show();
    }

    private void genererTestdata(String kategori) {
        int ar  = testAr;
        int mnd = testMnd;
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int normalMinutter = getNormalMinutter(prefs, kategori);

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
                vakt.category         = kategori;
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

    private void importerFraUri(Uri uri) {
        String kategori = spinnerEksportKategori.getSelectedItem().toString();
        Executors.newSingleThreadExecutor().execute(() -> {
            int importert = 0, hoppetOver = 0;
            SimpleDateFormat tidSdf = new SimpleDateFormat("HH:mm", new Locale("no", "NO"));
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                String linje;
                boolean forsteRad = true;
                WorkDayDao dao = AppDatabase.getInstance(this).workDayDao();

                while ((linje = reader.readLine()) != null) {
                    if (forsteRad) { forsteRad = false; continue; } // hopp over header
                    if (linje.trim().isEmpty()) continue;

                    String[] deler = linje.split(";");
                    if (deler.length < 3) { hoppetOver++; continue; }

                    try {
                        String dato      = deler[0].trim();
                        String startTid  = deler[1].trim();
                        String sluttTid  = deler.length > 2 ? deler[2].trim() : "";
                        long justeringMin = deler.length > 4 ? Long.parseLong(deler[4].trim()) : 0;

                        // Bygg starttidspunkt fra dato + klokkeslett
                        SimpleDateFormat datoTidSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", new Locale("no", "NO"));
                        long startMs = datoTidSdf.parse(dato + " " + startTid).getTime();
                        Long sluttMs = sluttTid.isEmpty() ? null
                                : datoTidSdf.parse(dato + " " + sluttTid).getTime();

                        WorkDay vakt = new WorkDay();
                        vakt.date             = dato;
                        vakt.startTime        = startMs;
                        vakt.endTime          = sluttMs;
                        vakt.manualAdjustment = justeringMin;
                        vakt.category         = kategori;
                        dao.insert(vakt);
                        importert++;
                    } catch (Exception e) {
                        hoppetOver++;
                    }
                }
                reader.close();

                int ferdigImportert = importert;
                int ferdigHoppet = hoppetOver;
                runOnUiThread(() -> Toast.makeText(this,
                        ferdigImportert + " vakter importert" +
                        (ferdigHoppet > 0 ? ", " + ferdigHoppet + " hoppet over" : ""),
                        Toast.LENGTH_LONG).show());

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Import feilet: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
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
