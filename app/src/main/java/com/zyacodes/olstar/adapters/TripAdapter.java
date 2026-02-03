package com.zyacodes.olstar.adapters;

import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.DatabaseReference;
import com.zyacodes.olstar.R;
import com.zyacodes.olstar.drivers.TripActiveActivity;
import com.zyacodes.olstar.models.TripModel;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TripAdapter extends RecyclerView.Adapter<TripAdapter.ViewHolder> {

    private final List<TripModel> trips;

    public TripAdapter(List<TripModel> trips) {
        this.trips = trips;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_trip, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        TripModel trip = trips.get(position);
        Context context = h.itemView.getContext();

        // ---------------- SET TEXT ----------------
        h.tvTripId.setText("Trip ID: " + trip.getTripId());
        h.tvFlightNo.setText("Flight No.: " + trip.getFlightNumber());
        h.tvPickup.setText("Pickup: " + trip.getPickup());
        h.tvDropoff.setText("Drop-off: " + trip.getDropOff());
        h.tvDate.setText("Date: " + trip.getDate());
        h.tvTime.setText("Time: " + trip.getTime());
        h.tvStatus.setText("Status: " + trip.getStatus());

        // Assuming you have a button in your item_trip.xml called btnFlightAware
        Button btnFlightAware = h.itemView.findViewById(R.id.btnFlightAware);

        btnFlightAware.setOnClickListener(v -> {
            String flightNumber = trip.getFlightNumber();
            if (flightNumber != null && !flightNumber.isEmpty()) {
                flightNumber = flightNumber.replace(" ", ""); // remove spaces
                String url = "https://flightaware.com/live/flight/" + flightNumber;
                Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));

                try {
                    v.getContext().startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(v.getContext(), "Cannot open FlightAware. No browser found.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(v.getContext(), "Flight number not available", Toast.LENGTH_SHORT).show();
            }
        });


        // ---------------- REAL-TIME ETA ----------------
        DatabaseReference etaRef = FirebaseDatabase.getInstance(
                        "https://olstar-5e642-default-rtdb.asia-southeast1.firebasedatabase.app/"
                ).getReference("schedules")
                .child(trip.getTripId())
                .child("ETA");

        // Remove old listener if exists
        if (h.etaListener != null) {
            etaRef.removeEventListener(h.etaListener);
        }

        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int pos = h.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return; // ViewHolder no longer valid

                if (snapshot.exists()) {
                    // SAFELY get "est"
                    Object estObj = snapshot.child("est").getValue();
                    String est;
                    if (estObj == null) {
                        est = "N/A";
                    } else if (estObj instanceof String) {
                        est = (String) estObj;
                    } else {
                        est = estObj.toString();
                    }

                    // SAFELY get "timestamp"
                    Object timestampObj = snapshot.child("timestamp").getValue();
                    String timestamp;
                    if (timestampObj == null) {
                        timestamp = "N/A";
                    } else if (timestampObj instanceof String) {
                        timestamp = (String) timestampObj;
                    } else { // probably Long/Integer
                        try {
                            long tsLong = Long.parseLong(timestampObj.toString());
                            timestamp = new SimpleDateFormat("h:mma", Locale.getDefault())
                                    .format(new Date(tsLong * 1000)); // assuming timestamp in seconds
                        } catch (NumberFormatException e) {
                            timestamp = timestampObj.toString();
                        }
                    }

                    h.ETA.setText("Estimated Time of Arrival: " + est);
                    h.ETAUpdate.setText("Last updated: " + timestamp);
                } else {
                    h.ETA.setText("Estimated Time of Arrival: N/A");
                    h.ETAUpdate.setText("Last updated: N/A");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        h.etaListener = listener;
        etaRef.addValueEventListener(listener);

        // ---------------- CONFIRM SLIDE ----------------
        h.slideConfirm.setVisibility(View.GONE);
        h.slideConfirm.setEnabled(false);
        h.slideConfirm.setProgress(0);
        h.slideConfirm.setOnSeekBarChangeListener(null);

        if ("Pending".equalsIgnoreCase(trip.getStatus())) {
            h.slideConfirm.setVisibility(View.VISIBLE);
            h.slideConfirm.setEnabled(true);
            h.slideConfirm.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    if (progress >= 95) {
                        seekBar.setEnabled(false);
                        int pos = h.getAdapterPosition();
                        if (pos == RecyclerView.NO_POSITION) return;
                        TripModel confirmedTrip = trips.get(pos);

                        FirebaseDatabase.getInstance(
                                        "https://olstar-5e642-default-rtdb.asia-southeast1.firebasedatabase.app/"
                                )
                                .getReference("schedules")
                                .child(confirmedTrip.getTripId())
                                .child("status")
                                .setValue("Confirmed")
                                .addOnSuccessListener(aVoid -> {
                                    Intent intent = new Intent(context, TripActiveActivity.class);
                                    intent.putExtra("tripId", confirmedTrip.getTripId());
                                    intent.putExtra("pickup", confirmedTrip.getPickup());
                                    intent.putExtra("dropOff", confirmedTrip.getDropOff());
                                    intent.putExtra("status", "Confirmed");
                                    context.startActivity(intent);
                                });
                    }
                }

                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {
                    if (seekBar.isEnabled()) seekBar.setProgress(0);
                }
            });
        }

        // ---------------- BACK TO ACTIVE TRIP ----------------
        h.backToActiveTrip.setVisibility(View.GONE);
        h.backToActiveTrip.setOnClickListener(null);

        if ("Confirmed".equalsIgnoreCase(trip.getStatus()) ||
                "Arrived".equalsIgnoreCase(trip.getStatus()) ||
                "On Route".equalsIgnoreCase(trip.getStatus()) ||
                "Ongoing".equalsIgnoreCase(trip.getStatus())) {

            h.backToActiveTrip.setVisibility(View.VISIBLE);
            h.backToActiveTrip.setOnClickListener(v -> {
                int pos = h.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                TripModel activeTrip = trips.get(pos);

                Intent intent = new Intent(context, TripActiveActivity.class);
                intent.putExtra("tripId", activeTrip.getTripId());
                intent.putExtra("pickup", activeTrip.getPickup());
                intent.putExtra("dropOff", activeTrip.getDropOff());
                intent.putExtra("status", activeTrip.getStatus());
                context.startActivity(intent);
            });
        }

        // ---------------- SET ALARM ----------------
        h.btnSetAlarm.setVisibility(View.VISIBLE);
        h.btnSetAlarm.setOnClickListener(v -> {
            int pos = h.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            TripModel alarmTrip = trips.get(pos);

            try {
                SimpleDateFormat sdf12 = new SimpleDateFormat("h:mma", Locale.getDefault());
                Date date = sdf12.parse(alarmTrip.getTime());
                if (date != null) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(date);

                    String type = alarmTrip.getTripType() != null ? alarmTrip.getTripType() : "";
                    if ("Departure".equalsIgnoreCase(type) || "Special".equalsIgnoreCase(type)) {
                        cal.add(Calendar.HOUR_OF_DAY, -1);
                    } else if ("Arrival".equalsIgnoreCase(type)) {
                        cal.add(Calendar.MINUTE, -20);
                    }

                    Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
                    intent.putExtra(AlarmClock.EXTRA_HOUR, cal.get(Calendar.HOUR_OF_DAY));
                    intent.putExtra(AlarmClock.EXTRA_MINUTES, cal.get(Calendar.MINUTE));
                    intent.putExtra(AlarmClock.EXTRA_MESSAGE, "Trip #1 Pickup at " + alarmTrip.getPickup());
                    intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);

                    if ("Departure".equalsIgnoreCase(type) || "Special".equalsIgnoreCase(type)) {
                        intent.putExtra(AlarmClock.EXTRA_ALARM_SNOOZE_DURATION, 10);
                    } else if ("Arrival".equalsIgnoreCase(type)) {
                        intent.putExtra(AlarmClock.EXTRA_ALARM_SNOOZE_DURATION, 5);
                    }

                    if (intent.resolveActivity(context.getPackageManager()) != null) {
                        context.startActivity(intent);
                    } else {
                        Toast.makeText(context, "No alarm app found", Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (ParseException | SecurityException e) {
                e.printStackTrace();
                Toast.makeText(context, "Cannot create alarm: invalid time or permission denied.", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return trips.size();
    }

    // ---------------- VIEW HOLDER ----------------
    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView tvTripId, tvFlightNo, tvPickup, tvDropoff, tvDate, tvTime, tvStatus;
        TextView ETA, ETAUpdate;
        SeekBar slideConfirm;
        Button backToActiveTrip, btnSetAlarm;

        ValueEventListener etaListener; // keep reference to remove listener if reused

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTripId = itemView.findViewById(R.id.tvTripId);
            tvFlightNo = itemView.findViewById(R.id.tvFlightNo);
            tvPickup = itemView.findViewById(R.id.tvPickup);
            tvDropoff = itemView.findViewById(R.id.tvDropoff);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            ETA = itemView.findViewById(R.id.ETA);
            ETAUpdate = itemView.findViewById(R.id.ETAUpdate);
            slideConfirm = itemView.findViewById(R.id.slideConfirm);
            backToActiveTrip = itemView.findViewById(R.id.backToActiveTrip);
            btnSetAlarm = itemView.findViewById(R.id.btnSetAlarm);
        }
    }
}
