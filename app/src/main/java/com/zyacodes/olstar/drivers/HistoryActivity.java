package com.zyacodes.olstar.drivers;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerHistory;
    private TextView tvEmptyHistory;
    private EditText etFilterDate;
    private ImageButton btnClearDateFilter;
    private HistoryAdapter adapter;
    private List<TripModel> historyList;
    private List<TripModel> fullHistoryList; // Store all trips for filtering

    private DatabaseReference schedulesRef;
    private String currentUserPhone;

    // RFID data cache - plateNumber -> RFIDCardData
    private Map<String, RFIDCardData> rfidCache = new HashMap<>();

    // Date format for display
    private SimpleDateFormat displayDateFormat;
    private SimpleDateFormat firebaseDateFormat;
    private SimpleDateFormat inputTimeFormat;

    // Helper class for RFID data
    private static class RFIDCardData {
        double balance;
        long lastUpdated;

        RFIDCardData(double balance, long lastUpdated) {
            this.balance = balance;
            this.lastUpdated = lastUpdated;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // Initialize date formatters with Asia/Manila timezone
        TimeZone manilaTimeZone = TimeZone.getTimeZone("Asia/Manila");
        displayDateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        displayDateFormat.setTimeZone(manilaTimeZone);

        firebaseDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        firebaseDateFormat.setTimeZone(manilaTimeZone);

        inputTimeFormat = new SimpleDateFormat("h:mma", Locale.getDefault());
        inputTimeFormat.setTimeZone(manilaTimeZone);

        recyclerHistory = findViewById(R.id.recyclerHistory);
        tvEmptyHistory = findViewById(R.id.tvEmptyHistory);
        etFilterDate = findViewById(R.id.etFilterDate);
        btnClearDateFilter = findViewById(R.id.btnClearDateFilter);

        recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        historyList = new ArrayList<>();
        fullHistoryList = new ArrayList<>();
        adapter = new HistoryAdapter(this, historyList);
        recyclerHistory.setAdapter(adapter);

        loadUserFromPrefs();

        schedulesRef = FirebaseDatabase.getInstance(
                        "https://olstar-5e642-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("schedules");

        setupDateFilter();
        loadRFIDDataAndHistory();
        setupBottomNav();
    }

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

    private void setupDateFilter() {
        // Set default date to today (UTC+8)
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"));
        etFilterDate.setText(displayDateFormat.format(calendar.getTime()));

        // Date picker listener
        etFilterDate.setOnClickListener(v -> showDatePicker());

        // Clear filter button
        btnClearDateFilter.setOnClickListener(v -> clearDateFilter());
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"));
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    Calendar selectedDate = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"));
                    selectedDate.set(selectedYear, selectedMonth, selectedDay, 0, 0, 0);
                    selectedDate.set(Calendar.MILLISECOND, 0);

                    String dateStr = firebaseDateFormat.format(selectedDate.getTime());
                    etFilterDate.setText(displayDateFormat.format(selectedDate.getTime()));
                    btnClearDateFilter.setVisibility(ImageButton.VISIBLE);

                    filterHistoryByDate(dateStr);
                },
                year, month, day
        );

        datePickerDialog.show();
    }

    private void clearDateFilter() {
        etFilterDate.setText("");
        btnClearDateFilter.setVisibility(ImageButton.GONE);
        filterHistoryByDate(null);
    }

    private void filterHistoryByDate(String filterDate) {
        if (filterDate == null || filterDate.isEmpty()) {
            // Show all trips
            historyList.clear();
            historyList.addAll(fullHistoryList);
        } else {
            // Filter trips by date
            historyList.clear();
            for (TripModel trip : fullHistoryList) {
                if (trip.getDate() != null && trip.getDate().equals(filterDate)) {
                    historyList.add(trip);
                }
            }
        }

        // Sort the history list by time ascending
        sortHistoryByTime();

        adapter.notifyDataSetChanged();
        tvEmptyHistory.setVisibility(historyList.isEmpty() ? TextView.VISIBLE : TextView.GONE);
    }

    // Method to sort trips by time in ascending order (earliest to latest)
    private void sortHistoryByTime() {
        Collections.sort(historyList, new Comparator<TripModel>() {
            @Override
            public int compare(TripModel trip1, TripModel trip2) {
                String time1 = trip1.getTime();
                String time2 = trip2.getTime();

                // Handle null or empty times
                if (time1 == null || time1.isEmpty() || time1.equals("null")) {
                    return 1; // Put null times at the end
                }
                if (time2 == null || time2.isEmpty() || time2.equals("null")) {
                    return -1; // Put null times at the end
                }

                try {
                    // Parse times using your database format (e.g., "3:00AM", "8:55PM")
                    Date date1 = inputTimeFormat.parse(time1);
                    Date date2 = inputTimeFormat.parse(time2);

                    if (date1 == null && date2 == null) return 0;
                    if (date1 == null) return 1;
                    if (date2 == null) return -1;

                    // Compare times (earliest first)
                    return date1.compareTo(date2);
                } catch (ParseException e) {
                    Log.e("HistoryActivity", "Error parsing time: " + time1 + " or " + time2, e);
                    // If parsing fails, compare as strings
                    return time1.compareTo(time2);
                }
            }
        });
    }

    private void loadRFIDDataAndHistory() {
        DatabaseReference rfidRef = FirebaseDatabase.getInstance(
                "https://olstar-5e642-default-rtdb.asia-southeast1.firebasedatabase.app/"
        ).getReference("rfidCards");

        rfidRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot rfidSnapshot) {
                rfidCache.clear();

                for (DataSnapshot cardSnapshot : rfidSnapshot.getChildren()) {
                    String plateNumber = cardSnapshot.child("plateNumber").getValue(String.class);
                    if (plateNumber != null && !plateNumber.isEmpty()) {
                        Double balance = cardSnapshot.child("balance").getValue(Double.class);
                        Long lastUpdated = cardSnapshot.child("lastUpdated").getValue(Long.class);

                        rfidCache.put(plateNumber, new RFIDCardData(
                                balance != null ? balance : 0.0,
                                lastUpdated != null ? lastUpdated : 0L
                        ));
                    }
                }

                Log.d("HistoryActivity", "RFID cache loaded with " + rfidCache.size() + " entries");
                loadHistory();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(HistoryActivity.this, "Failed to load RFID data", Toast.LENGTH_SHORT).show();
                Log.e("HistoryActivity", "RFID load cancelled: " + error.getMessage());
                loadHistory();
            }
        });
    }

    private void loadHistory() {
        // Get current date in UTC+8 for default filter
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"));
        String defaultFilterDate = firebaseDateFormat.format(calendar.getTime());

        schedulesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                fullHistoryList.clear();
                historyList.clear();

                for (DataSnapshot tripSnapshot : snapshot.getChildren()) {

                    String tripId = tripSnapshot.getKey();
                    String driverPhone = tripSnapshot.child("current")
                            .child("cellPhone")
                            .getValue(String.class);

                    if (driverPhone == null) {
                        continue;
                    }

                    driverPhone = driverPhone.trim();
                    if (!driverPhone.equals(currentUserPhone)) {
                        continue;
                    }

                    String status = tripSnapshot.child("status").getValue(String.class);
                    if (status == null || !"Completed".equalsIgnoreCase(status.trim())) {
                        continue;
                    }

                    String pickup = getStringValue(tripSnapshot, "pickup");
                    String dropOff = getStringValue(tripSnapshot, "dropOff");
                    String date = getStringValue(tripSnapshot, "date");
                    String time = getStringValue(tripSnapshot, "time");
                    String flightNumber = getStringValue(tripSnapshot, "flightNumber");
                    String clientName = getStringValue(tripSnapshot, "clientName");
                    String tripType = getStringValue(tripSnapshot, "tripType");
                    String driverRate = getStringValue(tripSnapshot, "driverRate");
                    String contactNumber = getStringValue(tripSnapshot, "contactNumber");
                    String driverName = getStringValue(tripSnapshot, "driverName");
                    String transportUnit = getStringValue(tripSnapshot, "transportUnit");
                    String unitType = getStringValue(tripSnapshot, "unitType");
                    String plateNumber = getStringValue(tripSnapshot, "plateNumber");
                    String color = getStringValue(tripSnapshot, "color");
                    String company = getStringValue(tripSnapshot, "company");
                    String pax = getStringValue(tripSnapshot, "pax");

                    if (date == null) continue;

                    RFIDCardData rfidData = rfidCache.get(plateNumber);
                    double rfidBalance = rfidData != null ? rfidData.balance : 0.0;
                    long rfidLastUpdated = rfidData != null ? rfidData.lastUpdated : 0L;

                    TripModel trip = new TripModel(
                            tripId, pickup, dropOff, status, date, time, flightNumber,
                            clientName, tripType, driverRate, contactNumber, driverName,
                            driverPhone, transportUnit, unitType, plateNumber, color,
                            rfidBalance, rfidLastUpdated, pax, company
                    );
                    fullHistoryList.add(trip);
                }

                // Sort all trips by time ascending
                sortFullHistoryByTime();

                // Set initial filter to today's date
                String currentFilter = etFilterDate.getText().toString();
                if (currentFilter.isEmpty()) {
                    // No filter set, default to today
                    etFilterDate.setText(displayDateFormat.format(calendar.getTime()));
                    filterHistoryByDate(defaultFilterDate);
                } else {
                    // Apply existing filter
                    String filterDate = defaultFilterDate;
                    try {
                        Date displayDate = displayDateFormat.parse(currentFilter);
                        if (displayDate != null) {
                            filterDate = firebaseDateFormat.format(displayDate);
                        }
                    } catch (ParseException e) {
                        filterDate = defaultFilterDate;
                    }
                    filterHistoryByDate(filterDate);
                }

                tvEmptyHistory.setVisibility(historyList.isEmpty() ? TextView.VISIBLE : TextView.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(HistoryActivity.this, "Failed to load trips.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Sort the full history list by time ascending
    private void sortFullHistoryByTime() {
        Collections.sort(fullHistoryList, new Comparator<TripModel>() {
            @Override
            public int compare(TripModel trip1, TripModel trip2) {
                String time1 = trip1.getTime();
                String time2 = trip2.getTime();

                // Handle null or empty times
                if (time1 == null || time1.isEmpty() || time1.equals("null")) {
                    return 1;
                }
                if (time2 == null || time2.isEmpty() || time2.equals("null")) {
                    return -1;
                }

                try {
                    Date date1 = inputTimeFormat.parse(time1);
                    Date date2 = inputTimeFormat.parse(time2);

                    if (date1 == null && date2 == null) return 0;
                    if (date1 == null) return 1;
                    if (date2 == null) return -1;

                    return date1.compareTo(date2);
                } catch (ParseException e) {
                    Log.e("HistoryActivity", "Error parsing time: " + time1 + " or " + time2, e);
                    return time1.compareTo(time2);
                }
            }
        });
    }

    private String getStringValue(DataSnapshot snapshot, String key) {
        Object val = snapshot.child(key).getValue();
        return val != null ? val.toString() : null;
    }

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