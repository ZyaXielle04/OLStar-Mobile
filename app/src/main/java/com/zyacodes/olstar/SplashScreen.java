package com.zyacodes.olstar;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SplashScreen extends AppCompatActivity {

    private static final int SPLASH_DURATION = 1500;
    private static final int LOCATION_PERMISSION_CODE = 2001;
    private static final int GPS_REQUEST_CODE = 2002;
    private static final int NOTIFICATION_PERMISSION_CODE = 2003;

    private static final String TAG = "FCM";
    private static final String PREFS_NAME = "OLStarPrefs";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_FCM_TOKEN_SENT = "fcm_token_sent";

    private SharedPreferences sharedPreferences;
    private OkHttpClient httpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);

        // Initialize SharedPreferences and HTTP client
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        httpClient = new OkHttpClient();

        // Start permission checks
        enforceLocationPermission();
    }

    // 🔁 HARD LOOP PERMISSION CHECK
    private void enforceLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_CODE
            );
        } else {
            enforceGPS();
        }
    }

    // 📡 GPS ENFORCEMENT
    private void enforceGPS() {
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationSettingsRequest.Builder builder =
                new LocationSettingsRequest.Builder()
                        .addLocationRequest(locationRequest)
                        .setAlwaysShow(true);

        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task =
                client.checkLocationSettings(builder.build());

        task.addOnSuccessListener(locationSettingsResponse -> checkNotificationPermission());

        task.addOnFailureListener(e -> {
            if (e instanceof ResolvableApiException) {
                try {
                    ((ResolvableApiException) e)
                            .startResolutionForResult(
                                    SplashScreen.this,
                                    GPS_REQUEST_CODE
                            );
                } catch (IntentSender.SendIntentException ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    // 🔔 Check notification permission (for Android 13+)
    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE);
                return;
            }
        }

        // If all permissions are granted, proceed with FCM setup
        setupFCM();
    }

    // 🔥 Setup Firebase Cloud Messaging
    private void setupFCM() {
        // Get FCM token
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String token = task.getResult();
                        Log.d(TAG, "FCM Token: " + token);

                        // Check if user is logged in (has user_id in SharedPreferences)
                        String userId = sharedPreferences.getString(KEY_USER_ID, null);
                        boolean tokenSent = sharedPreferences.getBoolean(KEY_FCM_TOKEN_SENT, false);

                        if (userId != null && !tokenSent) {
                            // Send token to backend
                            sendTokenToBackend(userId, token);
                        } else if (userId == null) {
                            Log.d(TAG, "User not logged in yet - token will be sent after login");
                            // Store token to send later
                            sharedPreferences.edit().putString("fcm_token", token).apply();
                        }
                    } else {
                        Log.e(TAG, "Failed to get FCM token", task.getException());
                    }

                    // Proceed to main activity after FCM setup
                    proceed();
                });
    }

    // 📤 Send FCM token to backend
    private void sendTokenToBackend(String userId, String token) {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("user_id", userId);
                json.put("fcm_token", token);
                json.put("device_info", new JSONObject()
                        .put("platform", "android")
                        .put("model", Build.MODEL)
                        .put("manufacturer", Build.MANUFACTURER)
                        .put("os_version", Build.VERSION.RELEASE)
                        .put("app_version", getPackageManager()
                                .getPackageInfo(getPackageName(), 0).versionName)
                );

                RequestBody body = RequestBody.create(
                        json.toString(),
                        MediaType.parse("application/json; charset=utf-8")
                );

                Request request = new Request.Builder()
                        .url("https://olstar.onrender.com/api/driver/register-token") // Replace with your server IP
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "FCM token registered successfully");
                        // Mark token as sent
                        sharedPreferences.edit()
                                .putBoolean(KEY_FCM_TOKEN_SENT, true)
                                .apply();
                    } else {
                        Log.e(TAG, "Token registration failed: " + response.body().string());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending token to server: " + e.getMessage());
            }
        }).start();
    }

    // 🚀 PROCEED
    private void proceed() {
        new Handler().postDelayed(() -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }, SPLASH_DURATION);
    }

    // 🔁 PERMISSION RESULT → LOOP
    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enforceGPS();
            } else {
                // If user checked "Don't ask again"
                if (!ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                )) {
                    // Open App Settings
                    Intent intent = new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", getPackageName(), null)
                    );
                    startActivity(intent);
                }

                // Ask again (HARD LOOP)
                new Handler().postDelayed(this::enforceLocationPermission, 500);
            }
        } else if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            // Notification permission result - proceed regardless
            setupFCM();
        }
    }

    // 🔁 GPS RESULT → RECHECK
    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            @Nullable Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == GPS_REQUEST_CODE) {
            enforceLocationPermission();
        }
    }

    // 📝 Method to call after login (add this to your LoginActivity)
    // 📝 Method to call after login (call this from MainActivity)
    public static void onUserLogin(Context context, String userId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Save user ID
        prefs.edit().putString(KEY_USER_ID, userId).apply();

        // Check if we have a stored token to send
        String storedToken = prefs.getString("fcm_token", null);
        boolean tokenSent = prefs.getBoolean(KEY_FCM_TOKEN_SENT, false);

        if (storedToken != null && !tokenSent) {
            // Create a new instance to send the token
            // We can't use ((SplashScreen) context) because context is MainActivity
            sendTokenFromContext(context, userId, storedToken);
        }
    }

    // Helper method to send token from any context
    private static void sendTokenFromContext(Context context, String userId, String token) {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();

                JSONObject json = new JSONObject();
                json.put("user_id", userId);
                json.put("fcm_token", token);
                json.put("device_info", new JSONObject()
                        .put("platform", "android")
                        .put("model", Build.MODEL)
                        .put("manufacturer", Build.MANUFACTURER)
                        .put("os_version", Build.VERSION.RELEASE)
                );

                RequestBody body = RequestBody.create(
                        json.toString(),
                        MediaType.parse("application/json; charset=utf-8")
                );

                Request request = new Request.Builder()
                        .url("http://192.168.1.100:5000/api/driver/register-token")
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "FCM token registered successfully after login");
                        // Mark token as sent
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean(KEY_FCM_TOKEN_SENT, true)
                                .apply();
                    } else {
                        Log.e(TAG, "Token registration failed after login: " + response.body().string());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending token after login: " + e.getMessage());
            }
        }).start();
    }
}