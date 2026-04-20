package com.zyacodes.olstar.drivers;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.zyacodes.olstar.GasPaymentDialog;
import com.zyacodes.olstar.MainActivity;
import com.zyacodes.olstar.R;
import com.zyacodes.olstar.controllers.GlobalFabController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvName;
    private TextView tvTotalBookings, tvTodayEarnings, tvPendingBookings;
    private TextView tvWeeklyEarnings, tvCompletedTrips;

    private LinearLayout navDashboard, navTrips, navRequests, navSettings, navHistory, llTodaysBookings, todaySalaryLinear, totalSalaryLinear;

    private DatabaseReference schedulesRef, usersRef;
    private FirebaseAuth auth;

    private String driverPhone;
    private String userId;
    private String driverType;

    private final ZoneId PH_ZONE = ZoneId.of("Asia/Manila");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        GlobalFabController.attach(this, v -> GasPaymentDialog.show(this));

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                });

        auth = FirebaseAuth.getInstance();

        FirebaseDatabase db = FirebaseDatabase.getInstance(
                "https://olstar-5e642-default-rtdb.asia-southeast1.firebasedatabase.app/"
        );

        usersRef = db.getReference("users");
        schedulesRef = db.getReference("schedules");

        initViews();
        setupBottomNavigation();
        loadUserFromPrefs();
        loadDriverName();
        loadDriverTypeFromPrefs();
        checkDriverTypeAndLoadData();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        GasPaymentDialog.handleActivityResult(requestCode, resultCode, data, this);
    }

    private void initViews() {
        tvName = findViewById(R.id.tvName);

        tvTotalBookings = findViewById(R.id.tvTotalBookings);
        tvTodayEarnings = findViewById(R.id.tvTodayEarnings);
        tvPendingBookings = findViewById(R.id.tvPendingBookings);

        tvWeeklyEarnings = findViewById(R.id.tvWeeklyEarnings);
        tvCompletedTrips = findViewById(R.id.tvCompletedTrips);

        todaySalaryLinear = findViewById(R.id.todaySalaryLinear);
        totalSalaryLinear = findViewById(R.id.totalSalaryLinear);

        navDashboard = findViewById(R.id.navDashboard);
        navTrips = findViewById(R.id.navTrips);
        navRequests = findViewById(R.id.navRequests);
        navSettings = findViewById(R.id.navSettings);
        navHistory = findViewById(R.id.navHistory);

        llTodaysBookings = findViewById(R.id.llTodaysBookings);
    }

    private void loadUserFromPrefs() {
        auth = FirebaseAuth.getInstance();
        String authUid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;

        SharedPreferences prefs = getSharedPreferences("login", MODE_PRIVATE);
        driverPhone = prefs.getString("phone", null);
        String prefUid = prefs.getString("userId", null);
        driverType = prefs.getString("driverType", null);

        // Use auth UID first, fallback to prefs UID
        userId = (authUid != null) ? authUid : prefUid;

        if (userId == null || driverPhone == null) {
            // Both failed → redirect to login
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
    }

    private void loadDriverName() {
        if (userId == null) return; // safety

        usersRef.child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) return;

                        String firstName = snapshot.child("firstName").getValue(String.class);
                        String lastName = snapshot.child("lastName").getValue(String.class);
                        tvName.setText(((firstName != null ? firstName : "") + " " +
                                (lastName != null ? lastName : "")).trim());
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(DashboardActivity.this, "Failed to load name", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadDriverTypeFromPrefs() {
        SharedPreferences prefs = getSharedPreferences("login", MODE_PRIVATE);
        driverType = prefs.getString("driverType", null);
        // Default to empty string if null
        if (driverType == null) driverType = "";
    }

    /**
     * Check driver type first, then load data with appropriate visibility
     */
    private void checkDriverTypeAndLoadData() {
        // First try to use driverType from SharedPreferences
        if (driverType != null && !driverType.isEmpty()) {
            applyDriverTypeVisibility(driverType);
            loadTodaysData();
            return;
        }

        // Fallback to Firebase if not in SharedPreferences
        if (userId == null) return;

        usersRef.child(userId).child("driverType")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String driverTypeFromFirebase = snapshot.getValue(String.class);

                        // Save to SharedPreferences for future use
                        if (driverTypeFromFirebase != null) {
                            driverType = driverTypeFromFirebase;
                            getSharedPreferences("login", MODE_PRIVATE)
                                    .edit()
                                    .putString("driverType", driverTypeFromFirebase)
                                    .apply();
                        }

                        applyDriverTypeVisibility(driverTypeFromFirebase);
                        loadTodaysData();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // If error, default to hiding salary sections
                        todaySalaryLinear.setVisibility(LinearLayout.GONE);
                        totalSalaryLinear.setVisibility(LinearLayout.GONE);
                        loadTodaysData();
                        Toast.makeText(DashboardActivity.this,
                                "Failed to load driver type", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void applyDriverTypeVisibility(String type) {
        if (type == null) type = "";

        // Hide salary sections for ALL except "main" drivers
        if ("main".equalsIgnoreCase(type)) {
            // Main driver - show salary sections
            todaySalaryLinear.setVisibility(LinearLayout.VISIBLE);
            totalSalaryLinear.setVisibility(LinearLayout.VISIBLE);
        } else {
            // Not main driver (indirect or direct) - hide salary sections
            todaySalaryLinear.setVisibility(LinearLayout.GONE);
            totalSalaryLinear.setVisibility(LinearLayout.GONE);
        }

        // Log for debugging
        android.util.Log.d("Dashboard", "Driver type: " + type +
                ", Today Salary visible: " + (todaySalaryLinear.getVisibility() == LinearLayout.VISIBLE) +
                ", Cutoff Salary visible: " + (totalSalaryLinear.getVisibility() == LinearLayout.VISIBLE));
    }

    private void loadTodaysData() {
        LocalDate today = LocalDate.now(PH_ZONE);
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("h:mma", Locale.US);

        int day = today.getDayOfMonth();
        LocalDate cutoffStart = (day <= 15)
                ? today.withDayOfMonth(1)
                : today.withDayOfMonth(16);

        LocalDate cutoffEnd = (day <= 15)
                ? today.withDayOfMonth(15)
                : today.withDayOfMonth(today.lengthOfMonth());

        schedulesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                int totalBookingsToday = 0;
                int pendingBookingsToday = 0;
                int completedTrips = 0;

                double todayEarnings = 0.0;
                double semiMonthlyEarnings = 0.0;

                for (DataSnapshot sched : snapshot.getChildren()) {

                    String phone = sched.child("current").child("cellPhone").getValue(String.class);
                    String dateStr = sched.child("date").getValue(String.class);
                    String timeStr = sched.child("time").getValue(String.class);
                    String status = sched.child("status").getValue(String.class);

                    if (phone == null || !phone.equals(driverPhone)) continue;
                    if (dateStr == null || timeStr == null) continue;

                    LocalDate tripDate;
                    LocalTime tripTime;
                    try {
                        tripDate = LocalDate.parse(dateStr);
                        tripTime = LocalTime.parse(timeStr.trim().toUpperCase(), timeFormatter);
                    } catch (Exception e) {
                        continue;
                    }

                    if (tripDate.equals(today)) {
                        totalBookingsToday++;
                        if ("Pending".equalsIgnoreCase(status)) {
                            pendingBookingsToday++;
                        }
                    }

                    if (tripDate.equals(today) && "Completed".equalsIgnoreCase(status)) {
                        todayEarnings += getRate(sched);
                    }

                    if (!tripDate.isBefore(cutoffStart)
                            && !tripDate.isAfter(cutoffEnd)
                            && "Completed".equalsIgnoreCase(status)) {
                        semiMonthlyEarnings += getRate(sched);
                    }

                    if ("Completed".equalsIgnoreCase(status)) {
                        completedTrips++;
                    }
                }

                tvTotalBookings.setText(String.valueOf(totalBookingsToday));
                tvPendingBookings.setText(String.valueOf(pendingBookingsToday));
                tvCompletedTrips.setText(String.valueOf(completedTrips));

                tvTodayEarnings.setText("₱" + String.format(Locale.US, "%.2f", todayEarnings));
                tvWeeklyEarnings.setText("₱" + String.format(Locale.US, "%.2f", semiMonthlyEarnings));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(DashboardActivity.this,
                        error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private double getRate(DataSnapshot sched) {
        Object rateObj = sched.child("driverRate").getValue();
        try {
            if (rateObj instanceof Long) return ((Long) rateObj).doubleValue();
            if (rateObj instanceof Double) return (Double) rateObj;
            if (rateObj != null) return Double.parseDouble(rateObj.toString());
        } catch (Exception ignored) {}
        return 0.0;
    }

    private void setupBottomNavigation() {

        navDashboard.setOnClickListener(v -> {});

        navTrips.setOnClickListener(v -> {
            startActivity(new Intent(this, TripsActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });

        navHistory.setOnClickListener(v -> {
            startActivity(new Intent(this, HistoryActivity.class));
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

        llTodaysBookings.setOnClickListener(v -> {
            startActivity(new Intent(this, TripsActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });
    }
}