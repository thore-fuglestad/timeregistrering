package com.thorefuglestad.timer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class WorkDayAdapter extends RecyclerView.Adapter<WorkDayAdapter.ViewHolder> {

    private final List<WorkDay> arbeidsdager;
    private final Context context;
    private final Locale norsk = new Locale("no", "NO");
    private final SimpleDateFormat dbFormat     = new SimpleDateFormat("yyyy-MM-dd", norsk);
    private final SimpleDateFormat visningsFormat = new SimpleDateFormat("EEE d. MMM", norsk);

    public WorkDayAdapter(Context context, List<WorkDay> arbeidsdager) {
        this.context = context;
        this.arbeidsdager = arbeidsdager;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_workday, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WorkDay dag = arbeidsdager.get(position);

        holder.tvDato.setText(formaterDato(dag.date));
        holder.tvTidsrom.setText(formaterTidsrom(dag));

        if (dag.endTime == null) {
            holder.tvVarighet.setText("Pågår...");
            holder.tvAvvik.setText("");
        } else {
            long minutter = dag.getDurationInMinutes();
            long timer = minutter / 60;
            long min   = minutter % 60;
            holder.tvVarighet.setText(timer + " t " + min + " min");

            // Hent normal arbeidstid og beregn avvik
            SharedPreferences prefs = context.getSharedPreferences(
                    SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
            long normalMinutter = prefs.getInt(
                    SettingsActivity.KEY_NORMAL_MINUTTER, SettingsActivity.STANDARD_MINUTTER);

            long avvik = minutter - normalMinutter;
            if (avvik == 0) {
                holder.tvAvvik.setText("");
            } else {
                long avvikAbs = Math.abs(avvik);
                String tekst = (avvik > 0 ? "(+" : "(-") + avvikAbs / 60 + "t " + avvikAbs % 60 + "min)";
                holder.tvAvvik.setText(tekst);
                holder.tvAvvik.setTextColor(avvik > 0 ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828"));
            }
        }

        holder.btnRediger.setOnClickListener(v -> {
            Intent intent = new Intent(context, EditWorkDayActivity.class);
            intent.putExtra("workday_id", dag.id);
            context.startActivity(intent);
        });

        holder.btnSlett.setOnClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle("Slett vakt")
                    .setMessage("Er du sikker på at du vil slette denne vakten?")
                    .setPositiveButton("Slett", (dialog, which) -> {
                        Executors.newSingleThreadExecutor().execute(() ->
                                AppDatabase.getInstance(context).workDayDao().delete(dag));
                        arbeidsdager.remove(position);
                        notifyItemRemoved(position);
                        notifyItemRangeChanged(position, arbeidsdager.size());
                    })
                    .setNegativeButton("Avbryt", null)
                    .show();
        });
    }

    @Override
    public int getItemCount() {
        return arbeidsdager.size();
    }

    public void oppdater(List<WorkDay> nyeListe) {
        arbeidsdager.clear();
        arbeidsdager.addAll(nyeListe);
        notifyDataSetChanged();
    }

    private String formaterDato(String dato) {
        try {
            Date d = dbFormat.parse(dato);
            String s = visningsFormat.format(d);
            return s.substring(0, 1).toUpperCase(norsk) + s.substring(1);
        } catch (ParseException e) {
            return dato;
        }
    }

    private String formaterTidsrom(WorkDay dag) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String start = sdf.format(new Date(dag.startTime));
        String slutt = dag.endTime != null ? sdf.format(new Date(dag.endTime)) : "pågår";
        return start + " – " + slutt;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDato, tvTidsrom, tvVarighet, tvAvvik;
        ImageButton btnRediger, btnSlett;

        ViewHolder(View view) {
            super(view);
            tvDato     = view.findViewById(R.id.tvDato);
            tvTidsrom  = view.findViewById(R.id.tvTidsrom);
            tvVarighet = view.findViewById(R.id.tvVarighet);
            tvAvvik    = view.findViewById(R.id.tvAvvik);
            btnRediger = view.findViewById(R.id.btnRediger);
            btnSlett   = view.findViewById(R.id.btnSlett);
        }
    }
}
