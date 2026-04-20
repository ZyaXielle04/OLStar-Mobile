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

import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST = 1000;
    private static final String TAG = "MainActivity";

    private TextInputEditText phoneInput, passwordInput;
    private Button loginBtn;

    private FirebaseAuth mAuth;
    private DatabaseReference usersRef;
    private ProgressDialog progressDialog;

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
        requestLocationPermissionAndStartService();

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

    // ---------------- LOCATION SERVICE ----------------
    private void requestLocationPermissionAndStartService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST
            );
        } else {
            startLocationService();
        }
    }

    private void startLocationService() {
        try {
            Intent intent = new Intent(this, LocationTrackingService.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "Starting foreground service");
                startForegroundService(intent);
            } else {
                Log.d(TAG, "Starting normal service");
                startService(intent);
            }

            Log.d(TAG, "LocationTrackingService start command sent");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start LocationTrackingService: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to start location service", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------- BIOMETRIC ----------------
    private void setupBiometricLogin() {
        Executor executor = ContextCompat.getMainExecutor(this);

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

    private void loginWithEmail(String email,
                                String password,
                                String phone,
                                String role,
                                String driverType) {

        progressDialog.show();

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();

                    if (task.isSuccessful()) {
                        String userId = mAuth.getCurrentUser().getUid();
                        Log.d(TAG, "Login successful for user: " + userId);

                        // Save login credentials including driverType
                        getSharedPreferences("login", MODE_PRIVATE)
                                .edit()
                                .putString("userId", userId)
                                .putString("email", email)
                                .putString("password", password)
                                .putString("phone", phone)
                                .putString("role", role)
                                .putString("driverType", driverType)
                                .apply();

                        // ===== IMPORTANT: Register FCM Token =====
                        registerFCMToken(userId);

                        // ===== Store user ID for FCM service =====
                        storeUserIdForFCM(userId);

                        startActivity(new Intent(this, DashboardActivity.class));
                        finish();
                    } else {
                        String errorMessage = task.getException() != null ?
                                task.getException().getMessage() : "Unknown error";
                        Toast.makeText(
                                this,
                                "Login failed: " + errorMessage,
                                Toast.LENGTH_LONG
                        ).show();
                        Log.e(TAG, "Login failed", task.getException());
                    }
                });
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
                startLocationService();
            } else {
                Toast.makeText(this, "Location permission required for tracking", Toast.LENGTH_LONG).show();
            }
        }
    }
}