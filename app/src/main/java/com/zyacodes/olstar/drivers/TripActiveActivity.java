package com.zyacodes.olstar.drivers;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.*;
import com.google.android.libraries.navigation.*;
import com.google.android.libraries.navigation.Waypoint;
import com.google.android.libraries.navigation.NavigationView;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.zyacodes.olstar.R;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class TripActiveActivity extends AppCompatActivity {

    private static final int LOCATION_REQUEST_CODE = 1000;

    private NavigationView navigationView;
    private Navigator navigator;
    private String pendingNavAddress = null;

    private TextView tvDestination, tvDistance;
    private SeekBar slideAction;

    private String pickupAddress, dropOffAddress;
    private double destinationLat, destinationLng;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private DatabaseReference statusRef;
    private String tripId;
    private String currentStatus = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_active);

        navigationView = findViewById(R.id.navigation_view);
        navigationView.onCreate(savedInstanceState);

        tvDestination = findViewById(R.id.tvDestination);
        tvDistance = findViewById(R.id.tvDistance);
        slideAction = findViewById(R.id.slideArrivedPickup);
        slideAction.setVisibility(SeekBar.GONE);

        pickupAddress = getIntent().getStringExtra("pickup");
        dropOffAddress = getIntent().getStringExtra("dropOff");
        tripId = getIntent().getStringExtra("tripId");

        tvDestination.setText(pickupAddress != null ? "Pickup: " + pickupAddress : "Drop-off: " + dropOffAddress);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        statusRef = FirebaseDatabase.getInstance(
                        "https://olstar-5e642-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("schedules")
                .child(tripId)
                .child("status");

        // Request location permission
        checkLocationPermission();

        // Listen to status changes (Confirmed, Arrived, On Route, Completed)
        listenToStatus();
    }

    // ---------------- PERMISSION ----------------
    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_REQUEST_CODE
            );
        } else {
            initNavigation();
            startDistanceUpdates();
        }
    }

    // ---------------- NAVIGATION ----------------
    private void initNavigation() {
        NavigationApi.getNavigator(this, new NavigationApi.NavigatorListener() {
            @Override
            public void onNavigatorReady(@NonNull Navigator nav) {
                navigator = nav;
                navigator.setAudioGuidance(Navigator.AudioGuidance.VOICE_ALERTS_AND_GUIDANCE);
                navigator.setTaskRemovedBehavior(Navigator.TaskRemovedBehavior.QUIT_SERVICE);

                // If we already have a pending address from Firebase
                if (pendingNavAddress != null) {
                    navigateToAddress(pendingNavAddress);
                    pendingNavAddress = null;
                }
            }

            @Override
            public void onError(int errorCode) {
                // handle SDK initialization errors if needed
            }
        });
    }

    private void navigateToAddress(String address) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> list = geocoder.getFromLocationName(address, 1);
                if (list == null || list.isEmpty()) return;

                Address a = list.get(0);
                destinationLat = a.getLatitude();
                destinationLng = a.getLongitude();

                if (navigator != null) {
                    Waypoint dest = Waypoint.builder()
                            .setLatLng(destinationLat, destinationLng)
                            .build();

                    runOnUiThread(() -> navigator.setDestination(dest)); // this is all you need
                } else {
                    pendingNavAddress = address;
                }
            } catch (Exception ignored) {}
        });
    }

    // ---------------- DISTANCE ----------------
    private void startDistanceUpdates() {
        LocationRequest req = LocationRequest.create()
                .setInterval(2000)
                .setFastestInterval(1000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult res) {
                Location loc = res.getLastLocation();
                if (loc == null || destinationLat == 0 || destinationLng == 0) return;

                float[] d = new float[1];
                Location.distanceBetween(
                        loc.getLatitude(), loc.getLongitude(),
                        destinationLat, destinationLng, d
                );

                tvDistance.setText((int) d[0] + " m remaining");

                // No need to call navigator.setCurrentLocation()
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper());
        }
    }

    // ---------------- STATUS LISTENER ----------------
    // ---------------- STATUS LISTENER ----------------
    private void listenToStatus() {
        statusRef.addValueEventListener(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                currentStatus = snapshot.getValue(String.class);
                String navAddress = null;

                // Decide destination based on status
                if ("Pending".equals(currentStatus) || "Confirmed".equals(currentStatus)) {
                    tvDestination.setText("Pickup: " + pickupAddress);
                    navAddress = pickupAddress;
                    slideAction.setVisibility(SeekBar.GONE);
                } else if ("Arrived".equals(currentStatus) || "On Route".equals(currentStatus)) {
                    tvDestination.setText("Drop-off: " + dropOffAddress);
                    navAddress = dropOffAddress;
                    slideAction.setVisibility("Arrived".equals(currentStatus) ? SeekBar.VISIBLE : SeekBar.GONE);
                } else if ("Completed".equals(currentStatus)) {
                    tvDestination.setText("Trip Completed");
                    slideAction.setVisibility(SeekBar.GONE);
                }

                // Update navigator destination
                if (navAddress != null) {
                    if (navigator != null) {
                        navigateToAddress(navAddress);
                    } else {
                        pendingNavAddress = navAddress;
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
        });
    }

    // ---------------- LIFECYCLE ----------------
    @Override protected void onStart() { super.onStart(); navigationView.onStart(); }
    @Override protected void onResume() { super.onResume(); navigationView.onResume(); }
    @Override protected void onPause() { super.onPause(); navigationView.onPause(); }
    @Override protected void onStop() { super.onStop(); navigationView.onStop(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (navigator != null) navigator.cleanup();
        if (locationCallback != null)
            fusedLocationClient.removeLocationUpdates(locationCallback);
        navigationView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        navigationView.onSaveInstanceState(outState);
    }
}
