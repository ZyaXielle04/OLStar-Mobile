package com.zyacodes.olstar.drivers;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.*;
import com.zyacodes.olstar.R;
import com.zyacodes.olstar.adapters.TripAdapter;
import com.zyacodes.olstar.models.TripModel;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TripsActivity extends AppCompatActivity {

    private RecyclerView rvTrips;
    private TripAdapter adapter;
    private List<TripModel> tripList;
    private TextView tvEmpty;

    private LinearLayout navDashboard, navTrips, navRequests, navSettings;

    private DatabaseReference schedulesRef;
    private String driverPhone;
    private final ZoneId PH_ZONE = ZoneId.of("Asia/Manila");

    private final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("h:mma", Locale.ENGLISH); // 7:30AM / 11:45PM

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trips);

        rvTrips = findViewById(R.id.rvTrips);
        rvTrips.setLayoutManager(new LinearLayoutManager(this));

        tvEmpty = findViewById(R.id.tvEmpty);

        tripList = new ArrayList<>();
        adapter = new TripAdapter(tripList);
        rvTrips.setAdapter(adapter);

        // Bottom Navigation
        navDashboard = findViewById(R.id.navDashboard);
        navTrips = findViewById(R.id.navTrips);
        navRequests = findViewById(R.id.navRequests);
        navSettings = findViewById(R.id.navSettings);

        setupNavBar();

        loadUserFromPrefs();
        setupFirebase();
        loadTodayTrips();
    }

    private void setupNavBar() {
        // Dashboard click
        navDashboard.setOnClickListener(v -> {
            startActivity(new Intent(TripsActivity.this, DashboardActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        });

        // Trips is current, just highlight (no action)
        navTrips.setOnClickListener(v -> { });

        // Requests click
        navRequests.setOnClickListener(v -> {
            startActivity(new Intent(TripsActivity.this, RequestsActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        });

        // Settings click
        navSettings.setOnClickListener(v -> {
            startActivity(new Intent(TripsActivity.this, SettingsActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        });
    }

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
                "https://olstar-5e642-default-rtdb.asia-southeast1.firebasedatabase.app/"
        );
        schedulesRef = db.getReference("schedules");
    }

    private void loadTodayTrips() {
        LocalDate today = LocalDate.now(PH_ZONE);

        schedulesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                tripList.clear();

                for (DataSnapshot sched : snapshot.getChildren()) {
                    String phone = sched.child("current")
                            .child("cellPhone")
                            .getValue(String.class);

                    if (phone == null || !phone.equals(driverPhone)) continue;

                    String dateStr = sched.child("date").getValue(String.class);
                    if (dateStr == null) continue;

                    LocalDate tripDate;
                    try {
                        tripDate = LocalDate.parse(dateStr);
                    } catch (Exception e) {
                        continue;
                    }

                    if (!tripDate.equals(today)) continue;

                    String pickup = sched.child("pickup").getValue(String.class);
                    String dropOff = sched.child("dropOff").getValue(String.class);
                    String status = sched.child("status").getValue(String.class);
                    String time = sched.child("time").getValue(String.class);

                    tripList.add(new TripModel(
                            sched.getKey(),
                            pickup,
                            dropOff,
                            status,
                            dateStr,
                            time
                    ));
                }

                // Sort trips by time ascending
                tripList.sort((t1, t2) -> {
                    try {
                        LocalTime time1 = LocalTime.parse(t1.getTime(), TIME_FORMATTER);
                        LocalTime time2 = LocalTime.parse(t2.getTime(), TIME_FORMATTER);
                        return time1.compareTo(time2);
                    } catch (Exception e) {
                        return 0;
                    }
                });

                // Show/hide empty state
                if (tripList.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    rvTrips.setVisibility(View.GONE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    rvTrips.setVisibility(View.VISIBLE);
                }

                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TripsActivity.this,
                        error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}
