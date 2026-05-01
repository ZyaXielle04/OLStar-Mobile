package com.zyacodes.olstar.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.zyacodes.olstar.R;

import java.util.HashMap;
import java.util.Map;

public class LocationTracker implements LocationListener {

    private static final String TAG = "LocationTracker";
    private Context context;
    private LocationManager locationManager;
    private DatabaseReference userLocationRef;
    private boolean isTracking = false;
    private String currentUserId;

    public LocationTracker(Context context) {
        this.context = context;

        // Get current logged-in user
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

            // Initialize Firebase reference
            userLocationRef = FirebaseDatabase.getInstance(
                            "https://olstar-5e642-default-rtdb.asia-southeast1.firebasedatabase.app/")
                    .getReference("users")
                    .child(currentUserId)
                    .child("currentLocation");

            Log.d(TAG, "LocationTracker initialized for user: " + currentUserId);
        } else {
            Log.e(TAG, "No user logged in");
        }
    }

    public void startTracking() {
        if (currentUserId == null) {
            Log.e(TAG, "Cannot start tracking: No user logged in");
            return;
        }

        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted");
            return;
        }

        try {
            locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

            if (locationManager != null) {
                // Check if GPS is enabled
                boolean isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

                if (isGPSEnabled) {
                    locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            5000,  // Update every 5 seconds
                            5,     // Minimum distance change 5 meters
                            this
                    );
                    isTracking = true;
                    Log.d(TAG, "✅ GPS location tracking started");

                    // Get last known location immediately
                    Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (lastLocation != null) {
                        updateLocationInFirebase(lastLocation);
                    }

                } else if (isNetworkEnabled) {
                    locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            5000,
                            5,
                            this
                    );
                    isTracking = true;
                    Log.d(TAG, "✅ Network location tracking started");

                    Location lastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    if (lastLocation != null) {
                        updateLocationInFirebase(lastLocation);
                    }

                } else {
                    Log.w(TAG, "⚠️ No location providers available. Please enable GPS.");
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception starting location tracking: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error starting location tracking: " + e.getMessage());
        }
    }

    public void stopTracking() {
        if (locationManager != null && isTracking) {
            try {
                locationManager.removeUpdates(this);
                isTracking = false;
                Log.d(TAG, "🛑 Location tracking stopped");
            } catch (SecurityException e) {
                Log.e(TAG, "Error stopping location tracking: " + e.getMessage());
            }
        }

        // Optional: Clear location from Firebase when stopping
        if (userLocationRef != null) {
            userLocationRef.removeValue()
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Location cleared from Firebase"))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to clear location: " + e.getMessage()));
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void updateLocationInFirebase(Location location) {
        if (userLocationRef == null || currentUserId == null) {
            return;
        }

        Map<String, Object> locationData = new HashMap<>();
        locationData.put("latitude", location.getLatitude());
        locationData.put("longitude", location.getLongitude());
        locationData.put("timestamp", System.currentTimeMillis());
        locationData.put("accuracy", location.getAccuracy());
        locationData.put("provider", location.getProvider());

        if (location.hasSpeed()) {
            locationData.put("speed", location.getSpeed());
        }

        if (location.hasBearing()) {
            locationData.put("bearing", location.getBearing());
        }

        if (location.hasAltitude()) {
            locationData.put("altitude", location.getAltitude());
        }

        userLocationRef.setValue(locationData)
                .addOnSuccessListener(aVoid ->
                        Log.d(TAG, "📍 Location updated: " + location.getLatitude() + ", " + location.getLongitude()))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Failed to update location: " + e.getMessage()));
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            updateLocationInFirebase(location);
        }
    }

    @Override
    public void onLocationChanged(java.util.List<Location> locations) {
        if (locations != null && !locations.isEmpty()) {
            updateLocationInFirebase(locations.get(locations.size() - 1));
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // Not needed but required by interface
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG, "Provider enabled: " + provider);
        // Restart tracking if needed
        if (!isTracking && hasLocationPermission()) {
            startTracking();
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG, "Provider disabled: " + provider);
    }

    public boolean isTracking() {
        return isTracking;
    }
}