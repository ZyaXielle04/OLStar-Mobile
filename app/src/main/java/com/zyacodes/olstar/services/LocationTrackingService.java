package com.zyacodes.olstar.services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.zyacodes.olstar.R;

import java.util.HashMap;
import java.util.Map;

public class LocationTrackingService extends Service {

    private static final String TAG = "LocationTrackingService";
    private static final String CHANNEL_ID = "location_tracking_channel";
    private static final int NOTIFICATION_ID = 123456;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private DatabaseReference userLocationRef;
    private boolean isTracking = false;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "LocationTrackingService created");

        try {
            // Create notification channel FIRST
            createNotificationChannel();

            // MUST call startForeground IMMEDIATELY (within 5 seconds)
            Notification notification = buildNotification();
            startForeground(NOTIFICATION_ID, notification);
            Log.d(TAG, "✅ startForeground called successfully");

            // Now do the rest of initialization
            initializeService();

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in onCreate: " + e.getMessage(), e);
            stopSelf();
        }
    }

    private void initializeService() {
        try {
            // Check if user is logged in
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Log.e(TAG, "No user logged in, stopping service");
                stopSelf();
                return;
            }

            String uid = user.getUid();
            Log.d(TAG, "Tracking location for user: " + uid);

            // Initialize Firebase reference
            userLocationRef = FirebaseDatabase.getInstance(
                            "https://olstar-5e642-default-rtdb.asia-southeast1.firebasedatabase.app/")
                    .getReference("users")
                    .child(uid)
                    .child("currentLocation");

            // Initialize location client
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

            // Start location updates after a small delay
            handler.postDelayed(this::startLocationUpdates, 1000);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in initializeService: " + e.getMessage(), e);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("OLStar Driver")
                .setContentText("Location tracking is active")
                .setSmallIcon(android.R.drawable.ic_dialog_map) // Using system icon
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Location Tracking",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Shows when your location is being tracked");

                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                    Log.d(TAG, "Notification channel created");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error creating notification channel: " + e.getMessage());
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand called with action: " + (intent != null ? intent.getAction() : "null"));

        // Handle stop action if needed
        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().equals("STOP_TRACKING")) {
                Log.d(TAG, "Stop tracking requested");
                stopTracking();
                stopSelf();
                return START_NOT_STICKY;
            }
        }

        return START_STICKY;
    }

    private void startLocationUpdates() {
        if (isTracking) {
            Log.d(TAG, "Location updates already running");
            return;
        }

        // Check location permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted. Stopping service.");
            stopSelf();
            return;
        }

        try {
            // Create location request
            LocationRequest request;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ way
                request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                        .setMinUpdateIntervalMillis(2000)
                        .build();
            } else {
                // Older Android
                request = LocationRequest.create()
                        .setInterval(5000)
                        .setFastestInterval(2000)
                        .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            }

            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult result) {
                    Location loc = result.getLastLocation();
                    if (loc != null) {
                        pushLocationToFirebase(loc);
                    }
                }
            };

            fusedLocationClient.requestLocationUpdates(
                    request,
                    locationCallback,
                    Looper.getMainLooper()
            );

            isTracking = true;
            Log.d(TAG, "✅ Location updates started successfully");

            // Update notification to show tracking is active
            updateNotification("Tracking active");

        } catch (SecurityException e) {
            Log.e(TAG, "Security exception starting location updates: " + e.getMessage());
            stopSelf();
        } catch (Exception e) {
            Log.e(TAG, "Error starting location updates: " + e.getMessage());
            stopSelf();
        }
    }

    private void updateNotification(String text) {
        try {
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("OLStar Driver")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_dialog_map)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .build();

            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.notify(NOTIFICATION_ID, notification);
        } catch (Exception e) {
            Log.e(TAG, "Error updating notification: " + e.getMessage());
        }
    }

    private void pushLocationToFirebase(@NonNull Location loc) {
        if (userLocationRef == null) {
            Log.e(TAG, "Firebase reference is null");
            return;
        }

        try {
            Map<String, Object> data = new HashMap<>();
            data.put("latitude", loc.getLatitude());
            data.put("longitude", loc.getLongitude());
            data.put("timestamp", System.currentTimeMillis());
            data.put("accuracy", loc.getAccuracy());

            if (loc.hasSpeed()) {
                data.put("speed", loc.getSpeed());
            }

            userLocationRef.setValue(data)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Location updated: " +
                            loc.getLatitude() + ", " + loc.getLongitude()))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to update location: " + e.getMessage()));
        } catch (Exception e) {
            Log.e(TAG, "Error pushing location: " + e.getMessage());
        }
    }

    private void stopTracking() {
        if (locationCallback != null && fusedLocationClient != null) {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback);
                isTracking = false;
                Log.d(TAG, "Location updates stopped");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping location updates: " + e.getMessage());
            }
        }

        // Clear location from Firebase when stopping
        if (userLocationRef != null) {
            userLocationRef.removeValue()
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Location cleared from Firebase"))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to clear location: " + e.getMessage()));
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "LocationTrackingService is being destroyed");
        stopTracking();

        // Remove any pending handlers
        handler.removeCallbacksAndMessages(null);

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}