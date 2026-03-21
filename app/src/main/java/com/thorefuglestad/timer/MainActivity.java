package com.thorefuglestad.timer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "timeregistrering_prefs";
    private static final String KEY_IS_RUNNING = "is_running";

    private final Locale norsk = new Locale("no", "NO");
    private final SimpleDateFormat datoFormat  = new SimpleDateFormat("yyyy-MM-dd", norsk);
    private final SimpleDateFormat datoVisning = new SimpleDateFormat("EEEE d. MMMM yyyy", norsk);
    private final SimpleDateFormat klokkeSdf   = new SimpleDateFormat("HH:mm", norsk);
    private final SimpleDateFormat sekundSdf   = new SimpleDateFormat("ss", norsk);

    private TextView tvDato, tvKlokke, tvSekunder;
    private Button btnToggleDay;

    private MaterialCardView cardFilterDag, cardFilterUke, cardFilterMnd, cardFilterAr;
    private TextView tvPeriodeDag, tvSumDag;
    private TextView tvPeriodeUke, tvSumUke;
    private TextView tvPeriodeMnd, tvSumMnd;
    private TextView tvPeriodeAr,  tvSumAr;

    private WorkDayAdapter adapter;

    private String aktivtFilter = null;

    // Offset for navigasjon: 0 = nåværende periode, -1 = forrige, +1 = neste
    private int dagOffset = 0, ukeOffset = 0, mndOffset = 0, arOffset = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable klokkeTikk;
    private Runnable oppdateringsRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AppDatabase.getInstance(this);

        tvDato     = findViewById(R.id.tvDato);
        tvKlokke   = findViewById(R.id.tvKlokke);
        tvSekunder = findViewById(R.id.tvSekunder);
        btnToggleDay = findViewById(R.id.btnToggleDay);
        Button btnManualEntry = findViewById(R.id.btnManualEntry);

        cardFilterDag = findViewById(R.id.cardFilterDag);
        cardFilterUke = findViewById(R.id.cardFilterUke);
        cardFilterMnd = findViewById(R.id.cardFilterMnd);
        cardFilterAr  = findViewById(R.id.cardFilterAr);
        tvPeriodeDag = findViewById(R.id.tvPeriodeDag); tvSumDag = findViewById(R.id.tvSumDag);
        tvPeriodeUke = findViewById(R.id.tvPeriodeUke); tvSumUke = findViewById(R.id.tvSumUke);
        tvPeriodeMnd = findViewById(R.id.tvPeriodeMnd); tvSumMnd = findViewById(R.id.tvSumMnd);
        tvPeriodeAr  = findViewById(R.id.tvPeriodeAr);  tvSumAr  = findViewById(R.id.tvSumAr);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);

        adapter = new WorkDayAdapter(this, new ArrayList<>());
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        recyclerView.setAdapter(adapter);

        btnToggleDay.setOnClickListener(v -> {
            if (erVaktAktiv()) avsluttVakt(); else startVakt();
        });

        btnManualEntry.setOnClickListener(v -> {
            ManualEntryDialog dialog = new ManualEntryDialog();
            dialog.setOnLagretListener(() -> {
                lastInnData();
                oppdaterFilterknapper();
            });
            dialog.show(getSupportFragmentManager(), "manuell");
        });

        findViewById(R.id.btnInnstillinger).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        findViewById(R.id.btnStatistikk).setOnClickListener(v ->
                startActivity(new Intent(this, StatisticsActivity.class)));

        cardFilterDag.setOnClickListener(v -> velgFilter("dag"));
        cardFilterUke.setOnClickListener(v -> velgFilter("uke"));
        cardFilterMnd.setOnClickListener(v -> velgFilter("mnd"));
        cardFilterAr.setOnClickListener(v  -> velgFilter("ar"));

        // Pil-navigasjon
        findViewById(R.id.btnPrevDag).setOnClickListener(v -> { dagOffset--; oppdaterFilterknapper(); lastInnData(); });
        findViewById(R.id.btnNextDag).setOnClickListener(v -> { dagOffset++; oppdaterFilterknapper(); lastInnData(); });
        findViewById(R.id.btnPrevUke).setOnClickListener(v -> { ukeOffset--; oppdaterFilterknapper(); lastInnData(); });
        findViewById(R.id.btnNextUke).setOnClickListener(v -> { ukeOffset++; oppdaterFilterknapper(); lastInnData(); });
        findViewById(R.id.btnPrevMnd).setOnClickListener(v -> { mndOffset--; oppdaterFilterknapper(); lastInnData(); });
        findViewById(R.id.btnNextMnd).setOnClickListener(v -> { mndOffset++; oppdaterFilterknapper(); lastInnData(); });
        findViewById(R.id.btnPrevAr).setOnClickListener(v  -> { arOffset--;  oppdaterFilterknapper(); lastInnData(); });
        findViewById(R.id.btnNextAr).setOnClickListener(v  -> { arOffset++;  oppdaterFilterknapper(); lastInnData(); });
    }

    @Override
    protected void onResume() {
        super.onResume();
        oppdaterKnappTekst();
        oppdaterFilterknapper();
        lastInnData();
        startKlokke();
        startOppdatering();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(klokkeTikk);
        handler.removeCallbacks(oppdateringsRunnable);
    }

    // ── Klokke ──────────────────────────────────────────────────────────────

    // Oppdaterer dato, time:minutt og sekunder hvert sekund
    private void startKlokke() {
        klokkeTikk = new Runnable() {
            @Override
            public void run() {
                Date now = new Date();
                // Første bokstav stor (norsk locale gir små bokstaver)
                String dato = datoVisning.format(now);
                dato = dato.substring(0, 1).toUpperCase(norsk) + dato.substring(1);
                tvDato.setText(dato);
                tvKlokke.setText(klokkeSdf.format(now));
                tvSekunder.setText(":" + sekundSdf.format(now));
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(klokkeTikk);
    }

    // ── Filterknapper ───────────────────────────────────────────────────────

    private void velgFilter(String filter) {
        aktivtFilter = filter.equals(aktivtFilter) ? null : filter;
        oppdaterKortUtseende();
        lastInnData();
    }

    // Setter farge på hvert kort — valgt kort blir mørkere
    private void oppdaterKortUtseende() {
        int[] normalFarger = {
            getColor(R.color.colorKortDag),
            getColor(R.color.colorKortUke),
            getColor(R.color.colorKortMnd),
            getColor(R.color.colorKortAr)
        };
        String[] filtre = {"dag", "uke", "mnd", "ar"};

        for (int i = 0; i < filtre.length; i++) {
            boolean valgt = filtre[i].equals(aktivtFilter);
            MaterialCardView card = kortFor(filtre[i]);
            // Valgt: mørkere (50% svart blandet inn), uvalgt: original farge
            card.setCardBackgroundColor(valgt ? darken(normalFarger[i]) : normalFarger[i]);
            card.setStrokeWidth(valgt ? 3 : 0);
            tekstFor(filtre[i], true).setTextColor(Color.WHITE);
            tekstFor(filtre[i], false).setTextColor(Color.WHITE);
        }
    }

    // Gjør en farge ~30% mørkere
    private int darken(int color) {
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(color, hsv);
        hsv[2] *= 0.7f;
        return android.graphics.Color.HSVToColor(hsv);
    }

    private MaterialCardView kortFor(String filter) {
        switch (filter) {
            case "uke": return cardFilterUke;
            case "mnd": return cardFilterMnd;
            case "ar":  return cardFilterAr;
            default:    return cardFilterDag;
        }
    }

    private TextView tekstFor(String filter, boolean periode) {
        switch (filter) {
            case "uke": return periode ? tvPeriodeUke : tvSumUke;
            case "mnd": return periode ? tvPeriodeMnd : tvSumMnd;
            case "ar":  return periode ? tvPeriodeAr  : tvSumAr;
            default:    return periode ? tvPeriodeDag : tvSumDag;
        }
    }

    private void oppdaterFilterknapper() {
        Calendar dagCal = Calendar.getInstance(); dagCal.add(Calendar.DAY_OF_MONTH, dagOffset);
        Calendar ukeCal = Calendar.getInstance(); ukeCal.add(Calendar.WEEK_OF_YEAR, ukeOffset);
        Calendar mndCal = Calendar.getInstance(); mndCal.add(Calendar.MONTH, mndOffset);
        Calendar arCal  = Calendar.getInstance(); arCal.add(Calendar.YEAR, arOffset);

        String dagLabel = new SimpleDateFormat("dd.MM.yy", norsk).format(dagCal.getTime());
        String ukeLabel = "Uke " + ukeCal.get(Calendar.WEEK_OF_YEAR);
        String mndRaw   = new SimpleDateFormat("MMM", norsk).format(mndCal.getTime());
        String mndLabel = mndRaw.substring(0, 1).toUpperCase(norsk) + mndRaw.substring(1)
                + " " + mndCal.get(Calendar.YEAR);
        String arLabel  = String.valueOf(arCal.get(Calendar.YEAR));

        Executors.newSingleThreadExecutor().execute(() -> {
            String dagSum = formaterSum(hentListe("dag"));
            String ukeSum = formaterSum(hentListe("uke"));
            String mndSum = formaterSum(hentListe("mnd"));
            String arSum  = formaterSum(hentListe("ar"));
            runOnUiThread(() -> {
                tvPeriodeDag.setText(dagLabel); tvSumDag.setText(dagSum);
                tvPeriodeUke.setText(ukeLabel); tvSumUke.setText(ukeSum);
                tvPeriodeMnd.setText(mndLabel); tvSumMnd.setText(mndSum);
                tvPeriodeAr.setText(arLabel);   tvSumAr.setText(arSum);
                oppdaterKortUtseende();
            });
        });
    }

    private List<WorkDay> hentListe(String filter) {
        if (filter.equals("dag")) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, dagOffset);
            return AppDatabase.getInstance(this).workDayDao().getByDate(datoFormat.format(cal.getTime()));
        }
        String[] range = datoIntervall(filter);
        return AppDatabase.getInstance(this).workDayDao().getByDateRange(range[0], range[1]);
    }

    private String formaterSum(List<WorkDay> liste) {
        long totalt = 0;
        for (WorkDay dag : liste) {
            if (dag.endTime != null) {
                totalt += dag.getDurationInMinutes();
            } else {
                totalt += (System.currentTimeMillis() - dag.startTime) / 1000 / 60 + dag.manualAdjustment;
            }
        }
        return (totalt / 60) + "t " + (totalt % 60) + "min";
    }

    private String[] datoIntervall(String filter) {
        Calendar cal = Calendar.getInstance();
        switch (filter) {
            case "dag":
                cal.add(Calendar.DAY_OF_MONTH, dagOffset);
                String dag = datoFormat.format(cal.getTime());
                return new String[]{dag, dag};
            case "uke":
                cal.add(Calendar.WEEK_OF_YEAR, ukeOffset);
                int dagerSidenMandag = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;
                cal.add(Calendar.DAY_OF_MONTH, -dagerSidenMandag);
                String ukeStart = datoFormat.format(cal.getTime());
                cal.add(Calendar.DAY_OF_MONTH, 6);
                return new String[]{ukeStart, datoFormat.format(cal.getTime())};
            case "mnd":
                cal.add(Calendar.MONTH, mndOffset);
                cal.set(Calendar.DAY_OF_MONTH, 1);
                String mndStart = datoFormat.format(cal.getTime());
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
                return new String[]{mndStart, datoFormat.format(cal.getTime())};
            case "ar":
                cal.add(Calendar.YEAR, arOffset);
                cal.set(Calendar.DAY_OF_YEAR, 1);
                String arStart = datoFormat.format(cal.getTime());
                cal.set(Calendar.DAY_OF_YEAR, cal.getActualMaximum(Calendar.DAY_OF_YEAR));
                return new String[]{arStart, datoFormat.format(cal.getTime())};
            default:
                return new String[]{"0000-01-01", "9999-12-31"};
        }
    }

    // ── Data ────────────────────────────────────────────────────────────────

    private void lastInnData() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<WorkDay> liste = hentAktivListe();
            runOnUiThread(() -> adapter.oppdater(liste));
        });
    }

    private List<WorkDay> hentAktivListe() {
        if (aktivtFilter == null) {
            return AppDatabase.getInstance(this).workDayDao().getAllWorkDays();
        } else {
            return hentListe(aktivtFilter);
        }
    }

    // Oppdaterer filterknapper hvert minutt under pågående vakt
    private void startOppdatering() {
        oppdateringsRunnable = new Runnable() {
            @Override
            public void run() {
                oppdaterFilterknapper();
                if (erVaktAktiv()) handler.postDelayed(this, 60_000);
            }
        };
        handler.post(oppdateringsRunnable);
    }

    // ── Vakt ────────────────────────────────────────────────────────────────

    private boolean erVaktAktiv() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_IS_RUNNING, false);
    }

    private void oppdaterKnappTekst() {
        if (erVaktAktiv()) {
            btnToggleDay.setText("Avslutt dag");
            btnToggleDay.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.colorStoppRed)));
        } else {
            btnToggleDay.setText("Start dag");
            btnToggleDay.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.colorStartGreen)));
        }
    }

    private void startVakt() {
        long now = System.currentTimeMillis();
        WorkDay workDay = new WorkDay();
        workDay.date = datoFormat.format(new Date(now));
        workDay.startTime = now;
        workDay.endTime = null;
        workDay.manualAdjustment = 0;

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase.getInstance(this).workDayDao().insert(workDay);
            List<WorkDay> oppdatertListe = hentAktivListe();
            runOnUiThread(() -> {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_IS_RUNNING, true).apply();
                btnToggleDay.setText("Avslutt dag");
                btnToggleDay.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.colorStoppRed)));
                adapter.oppdater(oppdatertListe);
                oppdaterFilterknapper();
                startOppdatering();
                Toast.makeText(this, "Vakt startet", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void avsluttVakt() {
        long now = System.currentTimeMillis();
        Executors.newSingleThreadExecutor().execute(() -> {
            WorkDay apneVakt = AppDatabase.getInstance(this).workDayDao().getOpenWorkDay();
            if (apneVakt != null) {
                apneVakt.endTime = now;
                AppDatabase.getInstance(this).workDayDao().update(apneVakt);
            }
            List<WorkDay> oppdatertListe = hentAktivListe();
            runOnUiThread(() -> {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_IS_RUNNING, false).apply();
                btnToggleDay.setText("Start dag");
                btnToggleDay.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.colorStartGreen)));
                adapter.oppdater(oppdatertListe);
                oppdaterFilterknapper();
                Toast.makeText(this, "Vakt avsluttet", Toast.LENGTH_SHORT).show();
            });
        });
    }
}
