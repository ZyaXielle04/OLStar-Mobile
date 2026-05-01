package com.zyacodes.olstar;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.zyacodes.olstar.drivers.DashboardActivity;
import com.zyacodes.olstar.services.FCMService;
import com.zyacodes.olstar.services.LocationTrackingService;
import com.zyacodes.olstar.utils.LocationTracker;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST = 1000;
    private static final String TAG = "MainActivity";

    private TextInputEditText phoneInput, passwordInput;
    private Button loginBtn;

    private FirebaseAuth mAuth;
    private DatabaseReference usersRef;
    private ProgressDialog progressDialog;

    // Add LocationTracker
    private LocationTracker locationTracker;

    // Biometric
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        phoneInput = findViewById(R.id.phoneInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginBtn = findViewById(R.id.loginBtn);

        mAuth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase
                .getInstance("https://olstar-5e642-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("users");

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Logging in...");
        progressDialog.setCancelable(false);

        checkSavedLogin();
        setupBiometricLogin();

        // Request permission but don't start service yet
        requestLocationPermission();

        loginBtn.setOnClickListener(v -> loginWithPhoneAndPassword());
    }

    // ---------------- AUTO LOGIN ----------------
    private void checkSavedLogin() {
        SharedPreferences prefs = getSharedPreferences("login", MODE_PRIVATE);
        String userId = prefs.getString("userId", null);
        String email = prefs.getString("email", null);
        String password = prefs.getString("password", null);
        String phone = prefs.getString("phone", null);
        String role = prefs.getString("role", null);
        String driverType = prefs.getString("driverType", null);

        if (userId == null || email == null || password == null
                || phone == null || role == null) {
            return;
        }

        if (isUserPertinent(role)) {
            loginWithEmail(email, password, phone, role, driverType);
        }
    }

    // ---------------- LOCATION PERMISSION (without starting service) ----------------
    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST
            );
        }
        // Don't start tracking yet - wait for login
    }

    // Start location tracking after successful login
    private void startLocationTracking() {
        try {
            locationTracker = new LocationTracker(this);
            locationTracker.startTracking();
            Log.d(TAG, "✅ Location tracking started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start location tracking: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to start location tracking", Toast.LENGTH_SHORT).show();
        }
    }

    // Stop location tracking when app closes
    private void stopLocationTracking() {
        if (locationTracker != null) {
            locationTracker.stopTracking();
            Log.d(TAG, "Location tracking stopped");
        }
    }

    // ---------------- BIOMETRIC ----------------
    private void setupBiometricLogin() {
        java.util.concurrent.@org.jspecify.annotations.NonNull Executor executor = ContextCompat.getMainExecutor(this);

        biometricPrompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {

                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {

                        SharedPreferences prefs =
                                getSharedPreferences("login", MODE_PRIVATE);

                        String email = prefs.getString("email", null);
                        String password = prefs.getString("password", null);
                        String phone = prefs.getString("phone", null);
                        String role = prefs.getString("role", null);
                        String driverType = prefs.getString("driverType", null);

                        if (email != null && password != null
                                && phone != null && role != null
                                && isUserPertinent(role)) {
                            loginWithEmail(email, password, phone, role, driverType);
                        } else {
                            Toast.makeText(
                                    MainActivity.this,
                                    "No saved credentials",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }
                });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Login with Fingerprint")
                .setSubtitle("Use fingerprint to login")
                .setNegativeButtonText("Cancel")
                .build();

        if (BiometricManager.from(this)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                == BiometricManager.BIOMETRIC_SUCCESS) {

            findViewById(R.id.fingerprintContainer)
                    .setOnClickListener(v ->
                            biometricPrompt.authenticate(promptInfo));
        }
    }

    // ---------------- PHONE LOGIN ----------------
    private void loginWithPhoneAndPassword() {
        String phone = phoneInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (TextUtils.isEmpty(phone)) {
            phoneInput.setError("Phone number required");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password required");
            return;
        }

        progressDialog.show();

        usersRef.orderByChild("phone").equalTo(phone)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            progressDialog.dismiss();
                            Toast.makeText(
                                    MainActivity.this,
                                    "Phone not registered",
                                    Toast.LENGTH_SHORT
                            ).show();
                            return;
                        }

                        String email = null;
                        String role = null;
                        String driverType = null;

                        for (DataSnapshot userSnap : snapshot.getChildren()) {
                            email = userSnap.child("email").getValue(String.class);
                            role = userSnap.child("role").getValue(String.class);
                            driverType = userSnap.child("driverType").getValue(String.class);
                            break;
                        }

                        if (!isUserPertinent(role)) {
                            progressDialog.dismiss();
                            Toast.makeText(
                                    MainActivity.this,
                                    "Drivers only",
                                    Toast.LENGTH_LONG
                            ).show();
                            return;
                        }

                        loginWithEmail(email, password, phone, role, driverType);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressDialog.dismiss();
                        Log.e(TAG, "Database error: " + error.getMessage());
                        Toast.makeText(MainActivity.this, "Database error", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loginWithEmail(String email, String password, String phone, String role, String driverType) {
        progressDialog.show();

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();

                    if (task.isSuccessful()) {
                        String userId = mAuth.getCurrentUser().getUid();

                        // START LOCATION TRACKING SERVICE
                        startLocationService();

                        // Rest of your login code...
                        getSharedPreferences("login", MODE_PRIVATE)
                                .edit()
                                .putString("userId", userId)
                                .putString("email", email)
                                .putString("password", password)
                                .putString("phone", phone)
                                .putString("role", role)
                                .putString("driverType", driverType)
                                .apply();

                        registerFCMToken(userId);
                        storeUserIdForFCM(userId);

                        startActivity(new Intent(this, DashboardActivity.class));
                        finish();
                    } else {
                        // Error handling...
                    }
                });
    }

    private void startLocationService() {
        try {
            Intent intent = new Intent(this, LocationTrackingService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            Log.d(TAG, "✅ LocationTrackingService started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start LocationTrackingService: " + e.getMessage());
        }
    }

    // ===== Register FCM Token =====
    private void registerFCMToken(String userId) {
        Log.d(TAG, "Calling FCMService.onUserLogin for user: " + userId);
        try {
            FCMService.onUserLogin(this, userId);
            Log.d(TAG, "FCMService.onUserLogin called successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error calling FCMService.onUserLogin: " + e.getMessage(), e);
        }
    }

    // ===== Store user ID for FCM =====
    private void storeUserIdForFCM(String userId) {
        getSharedPreferences("OLStarPrefs", MODE_PRIVATE)
                .edit()
                .putString("user_id", userId)
                .apply();
        Log.d(TAG, "User ID stored in OLStarPrefs: " + userId);
    }

    private boolean isUserPertinent(String role) {
        return role != null && role.equalsIgnoreCase("driver");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, but wait for login to start tracking
                Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Location permission required for tracking", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop tracking when app closes
        stopLocationTracking();
    }
}