package com.zyacodes.olstar.drivers;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.zyacodes.olstar.R;
import com.zyacodes.olstar.adapters.HistoryAdapter;
import com.zyacodes.olstar.models.TripModel;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerHistory;
    private TextView tvEmptyHistory;
    private HistoryAdapter adapter;
    private List<TripModel> historyList;

    private DatabaseReference schedulesRef;
    private String currentUserPhone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        recyclerHistory = findViewById(R.id.recyclerHistory);
        tvEmptyHistory = findViewById(R.id.tvEmptyHistory);

        recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        historyList = new ArrayList<>();
        adapter = new HistoryAdapter(this, historyList);
        recyclerHistory.setAdapter(adapter);

        loadUserFromPrefs(); // load driver phone from shared prefs

        // Use your RTDB URL
        schedulesRef = FirebaseDatabase.getInstance(
                        "https://olstar-5e642-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("schedules");

        loadHistory();
        setupBottomNav();
    }

    /**
     * Load the driver phone stored in SharedPreferences by DashboardActivity
     */
    private void loadUserFromPrefs() {
        SharedPreferences prefs = getSharedPreferences("login", MODE_PRIVATE);
        currentUserPhone = prefs.getString("phone", null);

        Log.d("HistoryPhone", currentUserPhone);

        if (currentUserPhone == null) {
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_LONG).show();
            finish();
        } else {
            currentUserPhone = currentUserPhone.trim();
        }
    }

    /**
     * Load completed trips under /schedules where /current/cellPhone matches currentUserPhone
     * and status is "Completed", and filter by rolling cutoff range (last month's 1st cutoff → this month's current cutoff)
     */
    private void loadHistory() {
        Date[] cutoffRange = getTargetCutoffRange();
        Date cutoffStart = cutoffRange[0];
        Date cutoffEnd = cutoffRange[1];

        Log.d("HistoryActivity", "Cutoff range: " + cutoffStart + " to " + cutoffEnd);

        schedulesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                historyList.clear();
                Log.d("HistoryActivity", "Total trips in /schedules: " + snapshot.getChildrenCount());

                for (DataSnapshot tripSnapshot : snapshot.getChildren()) {

                    String tripId = tripSnapshot.getKey();
                    String driverPhone = tripSnapshot.child("current")
                            .child("cellPhone")
                            .getValue(String.class);

                    if (driverPhone == null) {
                        Log.d("HistoryActivity", "Trip " + tripId + " skipped: no driver phone");
                        continue;
                    }

                    driverPhone = driverPhone.trim();
                    if (!driverPhone.equals(currentUserPhone)) {
                        Log.d("HistoryActivity", "Trip " + tripId + " skipped: driver phone mismatch (" + driverPhone + ")");
                        continue;
                    }

                    Log.d("HistoryActivity", "Trip " + tripId + ": driver phone matches");

                    // Check status
                    String status = tripSnapshot.child("status").getValue(String.class);
                    if (status == null || !"Completed".equalsIgnoreCase(status.trim())) {
                        Log.d("HistoryActivity", "Trip " + tripId + " skipped: status not completed (" + status + ")");
                        continue;
                    }

                    // Get trip fields
                    String pickup = getStringValue(tripSnapshot, "pickup");
                    String dropOff = getStringValue(tripSnapshot, "dropOff");
                    String date = getStringValue(tripSnapshot, "date");
                    String time = getStringValue(tripSnapshot, "time");
                    String flightNumber = getStringValue(tripSnapshot, "flightNumber");
                    String clientName = getStringValue(tripSnapshot, "clientName");
                    String tripType = getStringValue(tripSnapshot, "tripType");
                    String driverRate = getStringValue(tripSnapshot, "driverRate");

                    if (date == null) {
                        Log.d("HistoryActivity", "Trip " + tripId + " skipped: no date");
                        continue;
                    }

                    Date tripDate = parseTripDate(date.trim());
                    if (tripDate == null) {
                        Log.d("HistoryActivity", "Trip " + tripId + " skipped: invalid date format (" + date + ")");
                        continue;
                    }

                    // Check if trip date is within rolling cutoff
                    if (!tripDate.before(cutoffStart) && !tripDate.after(cutoffEnd)) {
                        TripModel trip = new TripModel(tripId, pickup, dropOff, status,
                                date, time, flightNumber, clientName, tripType, driverRate);
                        historyList.add(trip);
                        Log.d("HistoryActivity", "Trip " + tripId + " added to history: " +
                                pickup + " -> " + dropOff + ", Date: " + date + ", Flight: " + flightNumber);
                    } else {
                        Log.d("HistoryActivity", "Trip " + tripId + " skipped: date out of cutoff (" + date + ")");
                    }
                }

                adapter.notifyDataSetChanged();
                tvEmptyHistory.setVisibility(historyList.isEmpty() ? TextView.VISIBLE : TextView.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(HistoryActivity.this, "Failed to load trips.", Toast.LENGTH_SHORT).show();
                Log.e("HistoryActivity", "Firebase load cancelled: " + error.getMessage());
            }
        });
    }

    // Helper to safely get string from snapshot
    private String getStringValue(DataSnapshot snapshot, String key) {
        Object val = snapshot.child(key).getValue();
        return val != null ? val.toString() : null;
    }

    /**
     * Determine rolling cutoff range:
     * start = first cutoff of last month (1–15)
     * end = this month current cutoff (1–15 or 16–end depending on today)
     */
    private Date[] getTargetCutoffRange() {
        Calendar today = Calendar.getInstance();
        int dayToday = today.get(Calendar.DAY_OF_MONTH);

        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();

        // Start = first cutoff of last month
        start.add(Calendar.MONTH, -1);
        start.set(Calendar.DAY_OF_MONTH, 1);
        setStartOfDay(start);

        // End = this month current cutoff
        if (dayToday <= 15) {
            end.set(Calendar.DAY_OF_MONTH, 15);
        } else {
            end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));
        }
        setEndOfDay(end);

        return new Date[]{start.getTime(), end.getTime()};
    }

    private void setStartOfDay(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    private void setEndOfDay(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
    }

    // Parse date string yyyy-MM-dd
    private Date parseTripDate(String dateString) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateString);
        } catch (ParseException e) {
            return null;
        }
    }

    // Bottom navigation
    private void setupBottomNav() {
        LinearLayout navDashboard = findViewById(R.id.navDashboard);
        LinearLayout navTrips = findViewById(R.id.navTrips);
        LinearLayout navRequests = findViewById(R.id.navRequests);
        LinearLayout navSettings = findViewById(R.id.navSettings);

        navDashboard.setOnClickListener(v -> {
            startActivity(new Intent(this, DashboardActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });

        navTrips.setOnClickListener(v -> {
            startActivity(new Intent(this, TripsActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });

        navRequests.setOnClickListener(v -> {
            startActivity(new Intent(this, RequestsActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });

        navSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });
    }
}
