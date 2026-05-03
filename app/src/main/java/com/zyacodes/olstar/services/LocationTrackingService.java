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

    // ==================== 500ms GPS UPDATES ====================
    private static final long GPS_UPDATE_INTERVAL_MS = 500;      // Get GPS every 500ms
    private static final long GPS_FASTEST_INTERVAL_MS = 500;    // Minimum 500ms

    // Rate limiting to Firebase (don't flood the database)
    private static final long FIREBASE_WRITE_INTERVAL_MS = 500; // Send to Firebase every 2 seconds
    // ===========================================================

    private long lastFirebaseWriteTime = 0;
    private Location lastSentLocation = null;
    private int locationUpdateCount = 0;
    private int firebaseWriteCount = 0;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private DatabaseReference userLocationRef;
    private boolean isTracking = false;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "LocationTrackingService created - 500ms GPS mode");

        try {
            createNotificationChannel();
            Notification notification = buildNotification();
            startForeground(NOTIFICATION_ID, notification);
            Log.d(TAG, "✅ startForeground called successfully");
            initializeService();
        } catch (Exception e) {
            Log.e(TAG, "❌ Error in onCreate: " + e.getMessage(), e);
            stopSelf();
        }
    }

    private void initializeService() {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Log.e(TAG, "No user logged in, stopping service");
                stopSelf();
                return;
            }

            String uid = user.getUid();
            Log.d(TAG, "Tracking location for user: " + uid);

            userLocationRef = FirebaseDatabase.getInstance(
                            "https://olstar-5e642-default-rtdb.asia-southeast1.firebasedatabase.app/")
                    .getReference("users")
                    .child(uid)
                    .child("currentLocation");

            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            handler.postDelayed(this::startLocationUpdates, 500);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error in initializeService: " + e.getMessage(), e);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("OLStar Driver")
                .setContentText("📍 Location tracking active (500ms GPS)")
                .setSmallIcon(android.R.drawable.ic_dialog_map)
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
                channel.setDescription("Shows when your location is being tracked (500ms updates)");

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

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted. Stopping service.");
            stopSelf();
            return;
        }

        try {
            // Create location request for 500ms updates
            LocationRequest request;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ (API 31+)
                request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, GPS_UPDATE_INTERVAL_MS)
                        .setMinUpdateIntervalMillis(GPS_FASTEST_INTERVAL_MS)
                        .setMaxUpdateDelayMillis(500) // Max delay 1 second
                        .build();
                Log.d(TAG, "Location request created (Android 12+): " + GPS_UPDATE_INTERVAL_MS + "ms interval");
            } else {
                // Older Android
                request = LocationRequest.create()
                        .setInterval(GPS_UPDATE_INTERVAL_MS)
                        .setFastestInterval(GPS_FASTEST_INTERVAL_MS)
                        .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                Log.d(TAG, "Location request created (Legacy): " + GPS_UPDATE_INTERVAL_MS + "ms interval");
            }

            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult result) {
                    Location loc = result.getLastLocation();
                    if (loc != null) {
                        processLocationUpdate(loc);
                    }
                }
            };

            fusedLocationClient.requestLocationUpdates(
                    request,
                    locationCallback,
                    Looper.getMainLooper()
            );

            isTracking = true;

            // Reset counters
            locationUpdateCount = 0;
            firebaseWriteCount = 0;

            Log.d(TAG, "✅ Location updates started successfully!");
            Log.d(TAG, "   📍 GPS interval: " + GPS_UPDATE_INTERVAL_MS + "ms");
            Log.d(TAG, "   🔥 Firebase write interval: " + FIREBASE_WRITE_INTERVAL_MS + "ms");
            Log.d(TAG, "   ⚡ Rate limiting: ~" + (FIREBASE_WRITE_INTERVAL_MS / GPS_UPDATE_INTERVAL_MS) + " GPS updates per Firebase write");

            updateNotification("📍 Tracking active (500ms GPS)");

        } catch (SecurityException e) {
            Log.e(TAG, "Security exception starting location updates: " + e.getMessage());
            stopSelf();
        } catch (Exception e) {
            Log.e(TAG, "Error starting location updates: " + e.getMessage());
            stopSelf();
        }
    }

    /**
     * Process location update with rate limiting to Firebase
     * Receives GPS every 500ms but only writes to Firebase every 2 seconds
     */
    private void processLocationUpdate(Location loc) {
        long now = System.currentTimeMillis();
        locationUpdateCount++;

        // Convert speed from m/s to km/h for logging
        double speedKmh = loc.hasSpeed() ? loc.getSpeed() * 3.6 : 0;

        // Check if we should write to Firebase (rate limiting)
        if (now - lastFirebaseWriteTime >= FIREBASE_WRITE_INTERVAL_MS) {
            // Send to Firebase
            pushLocationToFirebase(loc);
            lastFirebaseWriteTime = now;
            lastSentLocation = loc;
            firebaseWriteCount++;

            Log.d(TAG, String.format("📍 [WRITE #%d] Lat: %.6f, Lng: %.6f, Speed: %.2f km/h, Accuracy: %.1fm | GPS updates received: %d",
                    firebaseWriteCount, loc.getLatitude(), loc.getLongitude(), speedKmh, loc.getAccuracy(), locationUpdateCount));

            // Reset counter after reporting
            locationUpdateCount = 0;
        } else {
            // Just log locally for debugging (VERBOSE level)
            Log.v(TAG, String.format("📍 [GPS #%d] Lat: %.6f, Lng: %.6f, Speed: %.2f km/h (rate limited - not sending to Firebase)",
                    locationUpdateCount, loc.getLatitude(), loc.getLongitude(), speedKmh));
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
                // Convert m/s to km/h for consistency with the web dashboard
                data.put("speed", loc.getSpeed() * 3.6);
            }

            userLocationRef.setValue(data)
                    .addOnSuccessListener(aVoid -> {
                        // Success - already logged in processLocationUpdate
                    })
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
                Log.d(TAG, "Location updates stopped. Total Firebase writes: " + firebaseWriteCount);
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
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}