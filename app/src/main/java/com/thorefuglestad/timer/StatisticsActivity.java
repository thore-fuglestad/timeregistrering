package com.thorefuglestad.timer;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;

public class StatisticsActivity extends AppCompatActivity {

    private final Locale norsk = new Locale("no", "NO");

    private RecyclerView recycler;
    private Spinner spinnerKategori;
    private TabLayout tabLayout;
    private List<StatistikkRad> ukeRader = new ArrayList<>();
    private List<StatistikkRad> mndRader = new ArrayList<>();
    private List<StatistikkRad> arRader  = new ArrayList<>();
    private long normalMinutter;
    private android.content.SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        ImageButton btnTilbake = findViewById(R.id.btnTilbake);
        btnTilbake.setOnClickListener(v -> finish());

        recycler = findViewById(R.id.recyclerStatistikk);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        tabLayout = findViewById(R.id.tabLayout);
        tabLayout.addTab(tabLayout.newTab().setText("Uke"));
        tabLayout.addTab(tabLayout.newTab().setText("Måned"));
        tabLayout.addTab(tabLayout.newTab().setText("År"));

        prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        normalMinutter = SettingsActivity.getNormalMinutter(prefs, "Jobb"); // oppdateres ved filter-valg

        spinnerKategori = findViewById(R.id.spinnerKategori);
        ArrayAdapter<CharSequence> katAdapter = ArrayAdapter.createFromResource(
                this, R.array.kategorier_filter, android.R.layout.simple_spinner_item);
        katAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerKategori.setAdapter(katAdapter);

        // Restore lagret kategori
        String lagretKat = prefs.getString(SettingsActivity.KEY_VALGT_KATEGORI, "Alle");
        int lagretPos = katAdapter.getPosition(lagretKat);
        if (lagretPos >= 0) spinnerKategori.setSelection(lagretPos);

        spinnerKategori.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit()
                        .putString(SettingsActivity.KEY_VALGT_KATEGORI, parent.getItemAtPosition(position).toString())
                        .apply();
                lastInnStatistikk();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Første lasting trigges av spinnerens onItemSelected
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { visTab(tab.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void lastInnStatistikk() {
        String valg = spinnerKategori.getSelectedItem().toString();
        String kat = valg.equals("Alle") ? null : valg;
        // Bruk Jobb-normal som referanselinje når "Alle" er valgt
        normalMinutter = kat != null
                ? SettingsActivity.getNormalMinutter(prefs, kat)
                : SettingsActivity.getNormalMinutter(prefs, "Jobb");
        Executors.newSingleThreadExecutor().execute(() -> {
            List<WorkDay> alle = kat == null
                    ? AppDatabase.getInstance(this).workDayDao().getAllWorkDays()
                    : AppDatabase.getInstance(this).workDayDao().getByCategory(kat);
            ukeRader = byggUkeStatistikk(alle);
            mndRader = byggMndStatistikk(alle);
            arRader  = byggArStatistikk(alle);
            runOnUiThread(() -> visTab(tabLayout.getSelectedTabPosition()));
        });
    }

    private void visTab(int pos) {
        if (pos == 0) {
            recycler.setAdapter(new UkeAdapter(ukeRader, normalMinutter));
        } else {
            List<StatistikkRad> rader = pos == 1 ? mndRader : arRader;
            recycler.setAdapter(new MndAdapter(rader, normalMinutter));
        }
    }

    // ── Bygg ukesrader ───────────────────────────────────────────────────────

    private List<StatistikkRad> byggUkeStatistikk(List<WorkDay> alle) {
        SimpleDateFormat dbSdf  = new SimpleDateFormat("yyyy-MM-dd", norsk);
        SimpleDateFormat dagSdf = new SimpleDateFormat("EEE dd.MM", norsk);
        Map<String, List<WorkDay>> gruppert = new LinkedHashMap<>();

        for (WorkDay dag : alle) {
            try {
                Calendar cal = Calendar.getInstance(norsk);
                cal.setTime(dbSdf.parse(dag.date));
                int ar  = cal.getWeekYear();
                int uke = cal.get(Calendar.WEEK_OF_YEAR);
                String nokkel = ar + "-U" + String.format(norsk, "%02d", uke);
                gruppert.computeIfAbsent(nokkel, k -> new ArrayList<>()).add(dag);
            } catch (Exception ignored) {}
        }

        List<StatistikkRad> rader = new ArrayList<>();
        for (Map.Entry<String, List<WorkDay>> entry : gruppert.entrySet()) {
            String[] deler = entry.getKey().split("-U");
            String etikett = "Uke " + Integer.parseInt(deler[1]) + "  ·  " + deler[0];
            rader.add(byggRadMedDagdata(etikett, entry.getValue(), dbSdf, dagSdf));
        }
        return rader;
    }

    // ── Bygg månedssrader ────────────────────────────────────────────────────

    private List<StatistikkRad> byggMndStatistikk(List<WorkDay> alle) {
        SimpleDateFormat dbSdf  = new SimpleDateFormat("yyyy-MM-dd", norsk);
        SimpleDateFormat mndSdf = new SimpleDateFormat("MMMM yyyy", norsk);
        Map<String, List<WorkDay>> gruppert = new LinkedHashMap<>();

        for (WorkDay dag : alle) {
            try {
                Calendar cal = Calendar.getInstance(norsk);
                cal.setTime(dbSdf.parse(dag.date));
                String nokkel = new SimpleDateFormat("yyyy-MM", norsk).format(cal.getTime());
                gruppert.computeIfAbsent(nokkel, k -> new ArrayList<>()).add(dag);
            } catch (Exception ignored) {}
        }

        List<StatistikkRad> rader = new ArrayList<>();
        for (Map.Entry<String, List<WorkDay>> entry : new TreeMap<>(gruppert).entrySet()) {
            try {
                Calendar cal = Calendar.getInstance(norsk);
                cal.setTime(new SimpleDateFormat("yyyy-MM", norsk).parse(entry.getKey()));
                String raw = mndSdf.format(cal.getTime());
                String etikett = raw.substring(0, 1).toUpperCase(norsk) + raw.substring(1);

                // Graf: én søyle per uke
                Map<String, Long> perUke = new TreeMap<>();
                for (WorkDay v : entry.getValue()) {
                    if (v.endTime == null) continue;
                    try {
                        Calendar c = Calendar.getInstance(norsk);
                        c.setTime(dbSdf.parse(v.date));
                        String ukeNokkel = c.getWeekYear() + "-"
                                + String.format(norsk, "%02d", c.get(Calendar.WEEK_OF_YEAR));
                        perUke.merge(ukeNokkel, v.getDurationInMinutes(), Long::sum);
                    } catch (Exception ignored) {}
                }
                List<BarEntry> dagEntries   = new ArrayList<>();
                List<String>   dagEtiketter = new ArrayList<>();
                int i = 0;
                for (Map.Entry<String, Long> d : perUke.entrySet()) {
                    dagEntries.add(new BarEntry(i, d.getValue() / 60f));
                    dagEtiketter.add("Uke " + Integer.parseInt(d.getKey().split("-")[1]));
                    i++;
                }

                // Bygg ukesrader for denne måneden
                Map<String, List<WorkDay>> perUkeMap = new LinkedHashMap<>();
                for (WorkDay v : entry.getValue()) {
                    try {
                        Calendar c = Calendar.getInstance(norsk);
                        c.setTime(dbSdf.parse(v.date));
                        String ukeNokkel = c.getWeekYear() + "-U"
                                + String.format(norsk, "%02d", c.get(Calendar.WEEK_OF_YEAR));
                        perUkeMap.computeIfAbsent(ukeNokkel, k -> new ArrayList<>()).add(v);
                    } catch (Exception ignored) {}
                }
                List<StatistikkRad> barnRader = new ArrayList<>();
                for (Map.Entry<String, List<WorkDay>> u : new TreeMap<>(perUkeMap).entrySet()) {
                    String[] deler = u.getKey().split("-U");
                    String ukeEtikett = "Uke " + Integer.parseInt(deler[1]);
                    barnRader.add(byggRad(ukeEtikett, u.getValue()));
                }

                StatistikkRad rad = byggRad(etikett, entry.getValue());
                rad.dagEntries   = dagEntries;
                rad.dagEtiketter = dagEtiketter;
                rad.barnRader    = barnRader;
                rader.add(rad);
            } catch (Exception ignored) {}
        }

        Collections.reverse(rader);
        return rader;
    }

    // ── Bygg årsrader ────────────────────────────────────────────────────────

    private List<StatistikkRad> byggArStatistikk(List<WorkDay> alle) {
        SimpleDateFormat dbSdf  = new SimpleDateFormat("yyyy-MM-dd", norsk);
        SimpleDateFormat mndSdf = new SimpleDateFormat("MMM", norsk);
        Map<String, List<WorkDay>> gruppert = new LinkedHashMap<>();

        for (WorkDay dag : alle) {
            try {
                Calendar cal = Calendar.getInstance(norsk);
                cal.setTime(dbSdf.parse(dag.date));
                String nokkel = String.valueOf(cal.get(Calendar.YEAR));
                gruppert.computeIfAbsent(nokkel, k -> new ArrayList<>()).add(dag);
            } catch (Exception ignored) {}
        }

        List<StatistikkRad> rader = new ArrayList<>();
        for (Map.Entry<String, List<WorkDay>> entry : new TreeMap<>(gruppert).entrySet()) {
            String etikett = entry.getKey();

            // Graf: én søyle per måned
            Map<String, Long> perMnd = new TreeMap<>();
            for (WorkDay v : entry.getValue()) {
                if (v.endTime == null) continue;
                try {
                    Calendar c = Calendar.getInstance(norsk);
                    c.setTime(dbSdf.parse(v.date));
                    String mndNokkel = String.format(norsk, "%02d", c.get(Calendar.MONTH) + 1);
                    perMnd.merge(mndNokkel, v.getDurationInMinutes(), Long::sum);
                } catch (Exception ignored) {}
            }
            List<BarEntry> dagEntries   = new ArrayList<>();
            List<String>   dagEtiketter = new ArrayList<>();
            int i = 0;
            for (Map.Entry<String, Long> d : perMnd.entrySet()) {
                dagEntries.add(new BarEntry(i, d.getValue() / 60f));
                try {
                    Calendar c = Calendar.getInstance(norsk);
                    c.set(Calendar.MONTH, Integer.parseInt(d.getKey()) - 1);
                    String raw = mndSdf.format(c.getTime());
                    dagEtiketter.add(raw.substring(0, 1).toUpperCase(norsk) + raw.substring(1));
                } catch (Exception e) {
                    dagEtiketter.add(d.getKey());
                }
                i++;
            }

            // Bygg månedssrader for dette året
            Map<String, List<WorkDay>> perMndMap = new LinkedHashMap<>();
            for (WorkDay v : entry.getValue()) {
                try {
                    Calendar c = Calendar.getInstance(norsk);
                    c.setTime(dbSdf.parse(v.date));
                    String mndNokkel = String.format(norsk, "%02d", c.get(Calendar.MONTH) + 1);
                    perMndMap.computeIfAbsent(mndNokkel, k -> new ArrayList<>()).add(v);
                } catch (Exception ignored) {}
            }
            SimpleDateFormat mndNavnSdf = new SimpleDateFormat("MMMM", norsk);
            List<StatistikkRad> barnRader = new ArrayList<>();
            for (Map.Entry<String, List<WorkDay>> m : new TreeMap<>(perMndMap).entrySet()) {
                try {
                    Calendar c = Calendar.getInstance(norsk);
                    c.set(Calendar.MONTH, Integer.parseInt(m.getKey()) - 1);
                    String raw = mndNavnSdf.format(c.getTime());
                    String mndEtikett = raw.substring(0, 1).toUpperCase(norsk) + raw.substring(1);
                    barnRader.add(byggRad(mndEtikett, m.getValue()));
                } catch (Exception ignored) {}
            }

            StatistikkRad rad = byggRad(etikett, entry.getValue());
            rad.dagEntries   = dagEntries;
            rad.dagEtiketter = dagEtiketter;
            rad.barnRader    = barnRader;
            rader.add(rad);
        }

        Collections.reverse(rader);
        return rader;
    }

    // ── Hjelpemetoder ─────────────────────────────────────────────────────────

    private StatistikkRad byggRadMedDagdata(String etikett, List<WorkDay> vakter,
                                             SimpleDateFormat dbSdf, SimpleDateFormat dagSdf) {
        Map<String, Long> perDag = new TreeMap<>();
        for (WorkDay v : vakter) {
            if (v.endTime != null) perDag.merge(v.date, v.getDurationInMinutes(), Long::sum);
        }
        List<BarEntry> dagEntries   = new ArrayList<>();
        List<String>   dagEtiketter = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Long> d : perDag.entrySet()) {
            dagEntries.add(new BarEntry(i, d.getValue() / 60f));
            try {
                String label = dagSdf.format(dbSdf.parse(d.getKey()));
                dagEtiketter.add(label.substring(0, 1).toUpperCase(norsk) + label.substring(1));
            } catch (Exception e) { dagEtiketter.add(d.getKey()); }
            i++;
        }
        StatistikkRad rad = byggRad(etikett, vakter);
        rad.dagEntries   = dagEntries;
        rad.dagEtiketter = dagEtiketter;
        return rad;
    }

    private StatistikkRad byggRad(String etikett, List<WorkDay> vakter) {
        long totaltMin = 0;
        long totalNormal = 0;
        int antall = 0;
        for (WorkDay v : vakter) {
            if (v.endTime != null) {
                totaltMin   += v.getDurationInMinutes();
                totalNormal += SettingsActivity.getNormalMinutter(prefs, v.category);
                antall++;
            }
        }
        long avvikMin = totaltMin - totalNormal;
        return new StatistikkRad(etikett, antall, totaltMin, avvikMin);
    }

    // ── Graf-hjelper ──────────────────────────────────────────────────────────

    static void fyllGraf(BarChart chart, List<BarEntry> entries,
                         List<String> etiketter, long normalMinutter) {
        if (entries.isEmpty()) return;

        BarDataSet dataSet = new BarDataSet(entries, "Timer");
        dataSet.setColor(Color.parseColor("#1A237E"));
        dataSet.setValueTextColor(Color.parseColor("#212121"));
        dataSet.setValueTextSize(9f);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                return value == 0 ? "" : ((int) value) + "t";
            }
        });

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.7f);
        chart.setData(data);

        LimitLine linje = new LimitLine(normalMinutter / 60f, "Normal");
        linje.setLineColor(Color.parseColor("#C62828"));
        linje.setLineWidth(1.5f);
        linje.setTextColor(Color.parseColor("#C62828"));
        linje.setTextSize(9f);
        linje.enableDashedLine(10f, 6f, 0f);

        YAxis venstre = chart.getAxisLeft();
        venstre.removeAllLimitLines();
        venstre.addLimitLine(linje);
        venstre.setAxisMinimum(0f);
        venstre.setTextColor(Color.parseColor("#757575"));
        venstre.setGridColor(Color.parseColor("#E0E0E0"));
        chart.getAxisRight().setEnabled(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelRotationAngle(-45f);
        xAxis.setTextColor(Color.parseColor("#757575"));
        xAxis.setTextSize(9f);
        xAxis.setGridColor(Color.parseColor("#E0E0E0"));
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                int idx = (int) value;
                return (idx >= 0 && idx < etiketter.size()) ? etiketter.get(idx) : "";
            }
        });

        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setTouchEnabled(false);
        chart.animateY(400);
        chart.invalidate();
    }

    // ── Dataklasse ────────────────────────────────────────────────────────────

    static class StatistikkRad {
        String etikett;
        int antallVakter;
        long totaltMinutter;
        long avvikMinutter;
        List<BarEntry>    dagEntries   = new ArrayList<>();
        List<String>      dagEtiketter = new ArrayList<>();
        List<StatistikkRad> barnRader  = new ArrayList<>();

        StatistikkRad(String etikett, int antallVakter, long totaltMinutter, long avvikMinutter) {
            this.etikett        = etikett;
            this.antallVakter   = antallVakter;
            this.totaltMinutter = totaltMinutter;
            this.avvikMinutter  = avvikMinutter;
        }
    }

    // ── Måneds-adapter med utvidbare ukerader ────────────────────────────────

    static class MndAdapter extends RecyclerView.Adapter<MndAdapter.VH> {

        private final List<StatistikkRad> rader;
        private final long normalMinutter;
        private int åpenPos = -1;

        MndAdapter(List<StatistikkRad> rader, long normalMinutter) {
            this.rader = rader;
            this.normalMinutter = normalMinutter;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_statistikk, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            StatistikkRad rad = rader.get(pos);
            h.tvPeriode.setText(rad.etikett);
            h.tvAntallVakter.setText(rad.antallVakter + (rad.antallVakter == 1 ? " vakt" : " vakter"));
            h.tvSumTimer.setText(rad.totaltMinutter / 60 + "t " + rad.totaltMinutter % 60 + "min");

            if (rad.avvikMinutter == 0) {
                h.tvAvvik.setText("");
            } else {
                long abs = Math.abs(rad.avvikMinutter);
                String tekst = (rad.avvikMinutter > 0 ? "(+" : "(-")
                        + abs / 60 + "t " + abs % 60 + "min)";
                h.tvAvvik.setText(tekst);
                h.tvAvvik.setTextColor(rad.avvikMinutter > 0
                        ? Color.parseColor("#2E7D32")
                        : Color.parseColor("#C62828"));
            }

            boolean erÅpen = pos == åpenPos;
            h.barChart.setVisibility(View.GONE);
            if (erÅpen && !rad.barnRader.isEmpty()) {
                h.recyclerBarn.setVisibility(View.VISIBLE);
                h.recyclerBarn.setLayoutManager(new LinearLayoutManager(h.itemView.getContext()));
                h.recyclerBarn.setAdapter(new BarnAdapter(rad.barnRader, normalMinutter));
            } else {
                h.recyclerBarn.setVisibility(View.GONE);
            }

            h.itemView.setOnClickListener(v -> {
                int gammel = åpenPos;
                åpenPos = (pos == åpenPos) ? -1 : pos;
                if (gammel >= 0) notifyItemChanged(gammel);
                notifyItemChanged(pos);
            });
        }

        @Override public int getItemCount() { return rader.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvPeriode, tvAntallVakter, tvSumTimer, tvAvvik;
            BarChart barChart;
            RecyclerView recyclerBarn;
            VH(View v) {
                super(v);
                tvPeriode      = v.findViewById(R.id.tvPeriode);
                tvAntallVakter = v.findViewById(R.id.tvAntallVakter);
                tvSumTimer     = v.findViewById(R.id.tvSumTimer);
                tvAvvik        = v.findViewById(R.id.tvAvvik);
                barChart       = v.findViewById(R.id.barChartUke);
                recyclerBarn   = v.findViewById(R.id.recyclerBarn);
            }
        }
    }

    // ── Under-adapter for uker innenfor en måned ──────────────────────────────

    static class BarnAdapter extends RecyclerView.Adapter<BarnAdapter.VH> {

        private final List<StatistikkRad> rader;
        private final long normalMinutter;

        BarnAdapter(List<StatistikkRad> rader, long normalMinutter) {
            this.rader = rader;
            this.normalMinutter = normalMinutter;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_statistikk_sub, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            StatistikkRad rad = rader.get(pos);
            h.tvPeriode.setText(rad.etikett);
            h.tvAntallVakter.setText(rad.antallVakter + (rad.antallVakter == 1 ? " vakt" : " vakter"));
            h.tvSumTimer.setText(rad.totaltMinutter / 60 + "t " + rad.totaltMinutter % 60 + "min");

            if (rad.avvikMinutter == 0) {
                h.tvAvvik.setText("");
            } else {
                long abs = Math.abs(rad.avvikMinutter);
                String tekst = (rad.avvikMinutter > 0 ? "(+" : "(-")
                        + abs / 60 + "t " + abs % 60 + "min)";
                h.tvAvvik.setText(tekst);
                h.tvAvvik.setTextColor(rad.avvikMinutter > 0
                        ? Color.parseColor("#2E7D32")
                        : Color.parseColor("#C62828"));
            }
        }

        @Override public int getItemCount() { return rader.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvPeriode, tvAntallVakter, tvSumTimer, tvAvvik;
            VH(View v) {
                super(v);
                tvPeriode      = v.findViewById(R.id.tvPeriode);
                tvAntallVakter = v.findViewById(R.id.tvAntallVakter);
                tvSumTimer     = v.findViewById(R.id.tvSumTimer);
                tvAvvik        = v.findViewById(R.id.tvAvvik);
            }
        }
    }

    // ── Adapter med utvidbar graf ─────────────────────────────────────────────

    static class UkeAdapter extends RecyclerView.Adapter<UkeAdapter.VH> {

        private final List<StatistikkRad> rader;
        private final long normalMinutter;
        private int åpenPos = -1;

        UkeAdapter(List<StatistikkRad> rader, long normalMinutter) {
            this.rader = rader;
            this.normalMinutter = normalMinutter;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_statistikk, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            StatistikkRad rad = rader.get(pos);
            h.tvPeriode.setText(rad.etikett);
            h.tvAntallVakter.setText(rad.antallVakter + (rad.antallVakter == 1 ? " vakt" : " vakter"));
            h.tvSumTimer.setText(rad.totaltMinutter / 60 + "t " + rad.totaltMinutter % 60 + "min");

            if (rad.avvikMinutter == 0) {
                h.tvAvvik.setText("");
            } else {
                long abs = Math.abs(rad.avvikMinutter);
                String tekst = (rad.avvikMinutter > 0 ? "(+" : "(-")
                        + abs / 60 + "t " + abs % 60 + "min)";
                h.tvAvvik.setText(tekst);
                h.tvAvvik.setTextColor(rad.avvikMinutter > 0
                        ? Color.parseColor("#2E7D32")
                        : Color.parseColor("#C62828"));
            }

            boolean erÅpen = pos == åpenPos;
            h.barChart.setVisibility(erÅpen ? View.VISIBLE : View.GONE);
            if (erÅpen) fyllGraf(h.barChart, rad.dagEntries, rad.dagEtiketter, normalMinutter);

            h.itemView.setOnClickListener(v -> {
                int gammel = åpenPos;
                åpenPos = (pos == åpenPos) ? -1 : pos;
                if (gammel >= 0) notifyItemChanged(gammel);
                notifyItemChanged(pos);
            });
        }

        @Override public int getItemCount() { return rader.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvPeriode, tvAntallVakter, tvSumTimer, tvAvvik;
            BarChart barChart;
            VH(View v) {
                super(v);
                tvPeriode      = v.findViewById(R.id.tvPeriode);
                tvAntallVakter = v.findViewById(R.id.tvAntallVakter);
                tvSumTimer     = v.findViewById(R.id.tvSumTimer);
                tvAvvik        = v.findViewById(R.id.tvAvvik);
                barChart       = v.findViewById(R.id.barChartUke);
                // Skjul under-RecyclerView — brukes ikke i uke/år-fanen
                v.findViewById(R.id.recyclerBarn).setVisibility(View.GONE);
            }
        }
    }
}
