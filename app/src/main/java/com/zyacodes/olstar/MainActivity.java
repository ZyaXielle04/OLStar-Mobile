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

        // ---- ADD THIS CHECK ----
        if (userId == null || email == null || password == null
                || phone == null || role == null) {
            // Nothing to auto-login
            return;
        }

        if (isUserPertinent(role)) {
            loginWithEmail(email, password, phone, role);
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
        Intent intent = new Intent(this, LocationTrackingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
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

                        if (email != null && password != null
                                && phone != null && role != null
                                && isUserPertinent(role)) {
                            loginWithEmail(email, password, phone, role);
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

                        for (DataSnapshot userSnap : snapshot.getChildren()) {
                            email = userSnap.child("email").getValue(String.class);
                            role = userSnap.child("role").getValue(String.class);
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

                        loginWithEmail(email, password, phone, role);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressDialog.dismiss();
                        Log.e("MainActivity", error.getMessage());
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
                        String userId = mAuth.getCurrentUser().getUid();

                        getSharedPreferences("login", MODE_PRIVATE)
                                .edit()
                                .putString("userId", userId)
                                .putString("email", email)
                                .putString("password", password)
                                .putString("phone", phone)
                                .putString("role", role)
                                .apply();

                        startActivity(new Intent(this, DashboardActivity.class));
                        finish();
                    } else {
                        Toast.makeText(
                                this,
                                "Login failed",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }

    private boolean isUserPertinent(String role) {
        return role != null && role.equalsIgnoreCase("driver");
    }
}
