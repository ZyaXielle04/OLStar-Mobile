package com.zyacodes.olstar.drivers;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.zyacodes.olstar.R;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvTotalBookings, tvTodayEarnings, tvPendingBookings;
    private TextView tvWeeklyEarnings, tvCompletedTrips; // <-- NEW

    private LinearLayout navDashboard, navTrips, navRequests, navSettings;

    private DatabaseReference schedulesRef;

    private String driverPhone;
    private int totalBookingsToday = 0;
    private double todayEarnings = 0.0;

    private final ZoneId PH_ZONE = ZoneId.of("Asia/Manila");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Handle system bar insets
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                });

        initViews();
        setupBottomNavigation();
        loadUserFromPrefs();
        setupFirebase();
        loadTodaysData();
    }

    private void initViews() {
        tvTotalBookings = findViewById(R.id.tvTotalBookings);
        tvTodayEarnings = findViewById(R.id.tvTodayEarnings);
        tvPendingBookings = findViewById(R.id.tvPendingBookings);

        tvWeeklyEarnings = findViewById(R.id.tvWeeklyEarnings); // <-- Initialize Weekly
        tvCompletedTrips = findViewById(R.id.tvCompletedTrips);   // <-- Initialize Completed Trips

        navDashboard = findViewById(R.id.navDashboard);
        navTrips = findViewById(R.id.navTrips);
        navRequests = findViewById(R.id.navRequests);
        navSettings = findViewById(R.id.navSettings);
    }

    private void setupBottomNavigation() {
        navDashboard.setOnClickListener(v -> selectNav(navDashboard));
        navTrips.setOnClickListener(v -> selectNav(navTrips));
        navRequests.setOnClickListener(v -> selectNav(navRequests));
        navSettings.setOnClickListener(v -> selectNav(navSettings));
        selectNav(navDashboard);
    }

    private void selectNav(LinearLayout selected) {
        resetNav(navDashboard);
        resetNav(navTrips);
        resetNav(navRequests);
        resetNav(navSettings);

        TextView label = (TextView) selected.getChildAt(1);
        label.setTextColor(Color.parseColor("#FFFF00"));
    }

    private void resetNav(LinearLayout nav) {
        ImageView icon = (ImageView) nav.getChildAt(0);
        TextView label = (TextView) nav.getChildAt(1);
        icon.setColorFilter(Color.WHITE);
        label.setTextColor(Color.WHITE);
    }

    // ----------------------------
    // Load SharedPreferences
    // ----------------------------
    private void loadUserFromPrefs() {
        SharedPreferences prefs = getSharedPreferences("login", MODE_PRIVATE);
        driverPhone = prefs.getString("phone", null);

        if (driverPhone == null) {
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void setupFirebase() {
        FirebaseDatabase db = FirebaseDatabase.getInstance(
                "https://olstar-5e642-default-rtdb.asia-southeast1.firebasedatabase.app/");
        schedulesRef = db.getReference("schedules");
    }

    // ----------------------------
    // Today’s Bookings + Earnings + Pending + Weekly + Completed Trips
    // ----------------------------
    private void loadTodaysData() {
        LocalDate today = LocalDate.now(PH_ZONE);
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("h:mma", Locale.US);

        // Compute start of week (Sunday)
        LocalDate startOfWeek = today;
        while (startOfWeek.getDayOfWeek() != DayOfWeek.SUNDAY) {
            startOfWeek = startOfWeek.minusDays(1);
        }

        LocalDate finalStartOfWeek = startOfWeek;
        schedulesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int totalBookingsToday = 0;
                double todayEarnings = 0.0;
                int pendingBookingsToday = 0;
                double weeklyEarnings = 0.0;
                int completedTrips = 0;

                for (DataSnapshot sched : snapshot.getChildren()) {

                    String phone = sched.child("current")
                            .child("cellPhone")
                            .getValue(String.class);

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

                    // ----------------------------
                    // Today's Bookings & Pending
                    // ----------------------------
                    if (tripDate.equals(today)) {
                        totalBookingsToday++;
                        if ("Pending".equalsIgnoreCase(status)) {
                            pendingBookingsToday++;
                        }
                    }

                    // ----------------------------
                    // Today's Earnings
                    // ----------------------------
                    if (tripDate.equals(today) && "Completed".equalsIgnoreCase(status)) {
                        Object rateObj = sched.child("driverRate").getValue();
                        if (rateObj != null) {
                            try {
                                if (rateObj instanceof Long) {
                                    todayEarnings += ((Long) rateObj).doubleValue();
                                } else if (rateObj instanceof Double) {
                                    todayEarnings += (Double) rateObj;
                                } else {
                                    todayEarnings += Double.parseDouble(rateObj.toString());
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }

                    // ----------------------------
                    // Weekly Earnings (Completed trips from Sunday to today)
                    // ----------------------------
                    if (!tripDate.isBefore(finalStartOfWeek) && !tripDate.isAfter(today)
                            && "Completed".equalsIgnoreCase(status)) {
                        Object rateObj = sched.child("driverRate").getValue();
                        if (rateObj != null) {
                            try {
                                if (rateObj instanceof Long) {
                                    weeklyEarnings += ((Long) rateObj).doubleValue();
                                } else if (rateObj instanceof Double) {
                                    weeklyEarnings += (Double) rateObj;
                                } else {
                                    weeklyEarnings += Double.parseDouble(rateObj.toString());
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }

                    // ----------------------------
                    // Completed Trips (all time)
                    // ----------------------------
                    if ("Completed".equalsIgnoreCase(status)) {
                        completedTrips++;
                    }
                }

                // Update UI
                tvTotalBookings.setText(String.valueOf(totalBookingsToday));
                tvPendingBookings.setText(String.valueOf(pendingBookingsToday));
                tvTodayEarnings.setText("₱" + String.format(Locale.US, "%.2f", todayEarnings));
                tvWeeklyEarnings.setText("₱" + String.format(Locale.US, "%.2f", weeklyEarnings));
                tvCompletedTrips.setText(String.valueOf(completedTrips));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(DashboardActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}
