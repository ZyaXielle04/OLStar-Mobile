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
import com.zyacodes.olstar.services.LocationTrackingService;

import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST = 1000;

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

        loginBtn.setOnClickListener(v -> loginWithPhoneAndPassword());

        setupBiometricLogin();

        // Request location permission & start service
        requestLocationPermissionAndStartService();
    }

    // ---------------- LOCATION SERVICE ----------------
    private void requestLocationPermissionAndStartService() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
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
        Intent intent = new Intent(this, LocationTrackingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationService();
        } else {
            Toast.makeText(
                    this,
                    "Location permission is required for GPS tracking",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // ---------------- BIOMETRIC ----------------
    private void setupBiometricLogin() {
        Executor executor = ContextCompat.getMainExecutor(this);

        biometricPrompt = new BiometricPrompt(
                MainActivity.this,
                executor,
                new BiometricPrompt.AuthenticationCallback() {

                    @Override
                    public void onAuthenticationError(
                            int errorCode,
                            @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        Toast.makeText(
                                MainActivity.this,
                                "Authentication error: " + errString,
                                Toast.LENGTH_SHORT
                        ).show();
                    }

                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);

                        Toast.makeText(
                                MainActivity.this,
                                "Fingerprint recognized!",
                                Toast.LENGTH_SHORT
                        ).show();

                        SharedPreferences prefs =
                                getSharedPreferences("login", MODE_PRIVATE);

                        String email = prefs.getString("email", null);
                        String password = prefs.getString("password", null);
                        String phone = prefs.getString("phone", null);
                        String role = prefs.getString("role", null);

                        if (email != null && password != null
                                && phone != null && role != null) {

                            loginWithEmail(email, password, phone, role);

                        } else {
                            Toast.makeText(
                                    MainActivity.this,
                                    "No saved credentials for fingerprint login.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Toast.makeText(
                                MainActivity.this,
                                "Fingerprint not recognized.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Login with Fingerprint")
                .setSubtitle("Use your fingerprint to login")
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

        usersRef.orderByChild("phone")
                .equalTo(phone)
                .addListenerForSingleValueEvent(new ValueEventListener() {

                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {

                        if (!snapshot.exists()) {
                            progressDialog.dismiss();
                            Toast.makeText(
                                    MainActivity.this,
                                    "Phone number not registered",
                                    Toast.LENGTH_SHORT
                            ).show();
                            return;
                        }

                        String email = null;
                        String role = null;

                        for (DataSnapshot userSnap : snapshot.getChildren()) {
                            email = userSnap.child("email").getValue(String.class);
                            role = userSnap.child("role").getValue(String.class);
                            break;
                        }

                        if (email == null || email.isEmpty()) {
                            progressDialog.dismiss();
                            Toast.makeText(
                                    MainActivity.this,
                                    "Email not found for this phone",
                                    Toast.LENGTH_SHORT
                            ).show();
                            return;
                        }

                        if (role == null || !role.equalsIgnoreCase("driver")) {
                            progressDialog.dismiss();
                            Toast.makeText(
                                    MainActivity.this,
                                    "Access denied. Driver accounts only.",
                                    Toast.LENGTH_LONG
                            ).show();
                            return;
                        }

                        loginWithEmail(email, password, phone, role);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressDialog.dismiss();
                        Toast.makeText(
                                MainActivity.this,
                                "Database error: " + error.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                        Log.e("MainActivity", "Firebase DB error", error.toException());
                    }
                });
    }

    private void loginWithEmail(String email,
                                String password,
                                String phone,
                                String role) {

        progressDialog.show();

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();

                    if (task.isSuccessful()) {
                        String uid = mAuth.getCurrentUser().getUid();

                        // ✅ SAVE ALL USER DATA
                        getSharedPreferences("login", MODE_PRIVATE)
                                .edit()
                                .putString("uid", uid)
                                .putString("email", email)
                                .putString("password", password)
                                .putString("phone", phone)
                                .putString("role", role)
                                .apply();

                        Toast.makeText(
                                MainActivity.this,
                                "Login successful",
                                Toast.LENGTH_SHORT
                        ).show();

                        Intent intent = new Intent(
                                MainActivity.this,
                                DashboardActivity.class
                        );
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();

                    } else {
                        Toast.makeText(
                                MainActivity.this,
                                "Login failed: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }
}
