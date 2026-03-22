package com.thorefuglestad.timer;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.util.Calendar;
import java.util.concurrent.Executors;

public class ManualEntryDialog extends DialogFragment {

    // Callback som varsler MainActivity når en ny registrering er lagret
    public interface OnLagretListener {
        void onLagret();
    }

    private OnLagretListener listener;

    public void setOnLagretListener(OnLagretListener listener) {
        this.listener = listener;
    }

    private EditText etDate, etStartTime, etEndTime, etAdjustment;
    private Spinner spinnerKategori;

    private int selectedYear, selectedMonth, selectedDay;
    private int startHour = -1, startMinute = -1;
    private int endHour = -1, endMinute = -1;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_manual_entry, null);

        etDate = view.findViewById(R.id.etDate);
        etStartTime = view.findViewById(R.id.etStartTime);
        etEndTime = view.findViewById(R.id.etEndTime);
        etAdjustment = view.findViewById(R.id.etAdjustment);
        spinnerKategori = view.findViewById(R.id.spinnerKategori);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.kategorier, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerKategori.setAdapter(adapter);

        Calendar now = Calendar.getInstance();
        selectedYear = now.get(Calendar.YEAR);
        selectedMonth = now.get(Calendar.MONTH);
        selectedDay = now.get(Calendar.DAY_OF_MONTH);
        oppdaterDatoFelt();

        etDate.setOnClickListener(v -> {
            new DatePickerDialog(requireContext(), (picker, year, month, day) -> {
                selectedYear = year;
                selectedMonth = month;
                selectedDay = day;
                oppdaterDatoFelt();
            }, selectedYear, selectedMonth, selectedDay).show();
        });

        etStartTime.setOnClickListener(v -> {
            int h = startHour >= 0 ? startHour : now.get(Calendar.HOUR_OF_DAY);
            int m = startMinute >= 0 ? startMinute : now.get(Calendar.MINUTE);
            new TimePickerDialog(requireContext(), (picker, hour, minute) -> {
                startHour = hour;
                startMinute = minute;
                etStartTime.setText(String.format("%02d:%02d", hour, minute));
            }, h, m, true).show();
        });

        etEndTime.setOnClickListener(v -> {
            int h = endHour >= 0 ? endHour : now.get(Calendar.HOUR_OF_DAY);
            int m = endMinute >= 0 ? endMinute : now.get(Calendar.MINUTE);
            new TimePickerDialog(requireContext(), (picker, hour, minute) -> {
                endHour = hour;
                endMinute = minute;
                etEndTime.setText(String.format("%02d:%02d", hour, minute));
            }, h, m, true).show();
        });

        return new AlertDialog.Builder(requireContext())
                .setTitle("Manuell registrering")
                .setView(view)
                .setPositiveButton("Lagre", (dialog, which) -> lagreArbeidsdag())
                .setNegativeButton("Avbryt", null)
                .create();
    }

    private void oppdaterDatoFelt() {
        etDate.setText(String.format("%04d-%02d-%02d",
                selectedYear, selectedMonth + 1, selectedDay));
    }

    private void lagreArbeidsdag() {
        if (startHour < 0 || endHour < 0) {
            Toast.makeText(getContext(), "Fyll inn både start- og sluttid", Toast.LENGTH_SHORT).show();
            return;
        }

        Calendar start = Calendar.getInstance();
        start.set(selectedYear, selectedMonth, selectedDay, startHour, startMinute, 0);
        start.set(Calendar.MILLISECOND, 0);

        Calendar end = Calendar.getInstance();
        end.set(selectedYear, selectedMonth, selectedDay, endHour, endMinute, 0);
        end.set(Calendar.MILLISECOND, 0);

        if (end.before(start)) {
            Toast.makeText(getContext(), "Sluttid kan ikke være før starttid", Toast.LENGTH_SHORT).show();
            return;
        }

        String justeringTekst = etAdjustment.getText().toString().trim();
        long justering = justeringTekst.isEmpty() ? 0 : Long.parseLong(justeringTekst);

        WorkDay workDay = new WorkDay();
        workDay.date = etDate.getText().toString();
        workDay.startTime = start.getTimeInMillis();
        workDay.endTime = end.getTimeInMillis();
        workDay.manualAdjustment = justering;
        workDay.category = spinnerKategori.getSelectedItem().toString();

        // Lagre i databasen, og varsle MainActivity etterpå slik at summene oppdateres
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase.getInstance(requireContext()).workDayDao().insert(workDay);
            if (listener != null) {
                requireActivity().runOnUiThread(() -> listener.onLagret());
            }
        });

        Toast.makeText(getContext(), "Arbeidsdag lagret", Toast.LENGTH_SHORT).show();
    }
}
