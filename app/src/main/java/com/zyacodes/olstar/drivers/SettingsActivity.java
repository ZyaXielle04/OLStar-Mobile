package com.zyacodes.olstar.drivers;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.zyacodes.olstar.GasPaymentDialog;
import com.zyacodes.olstar.MainActivity;
import com.zyacodes.olstar.R;
import com.zyacodes.olstar.controllers.GlobalFabController;

public class SettingsActivity extends AppCompatActivity {

    private LinearLayout navDashboard, navTrips, navRequests, navSettings, navHistory;
    private Button btnLogout; // <-- Logout button

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Handle system bar insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        setupBottomNavigation();
        setupLogoutButton(); // <-- Initialize logout button
    }

    private void initViews() {
        navDashboard = findViewById(R.id.navDashboard);
        navTrips = findViewById(R.id.navTrips);
        navRequests = findViewById(R.id.navRequests);
        navSettings = findViewById(R.id.navSettings);
        navHistory = findViewById(R.id.navHistory);

        btnLogout = findViewById(R.id.btnLogout); // <-- bind button
    }

    private void setupBottomNavigation() {
        navDashboard.setOnClickListener(v -> {
            Intent intent = new Intent(this, DashboardActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });

        navTrips.setOnClickListener(v -> {
            Intent intent = new Intent(this, TripsActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });

        navHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, HistoryActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });

        navRequests.setOnClickListener(v -> {
            Intent intent = new Intent(this, RequestsActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });

        // Settings → already on Settings, so no action
        navSettings.setOnClickListener(v -> {
            // Do nothing
        });
    }

    // ---------------- Logout Button ----------------
    private void setupLogoutButton() {
        btnLogout.setOnClickListener(v -> {

            // 1. Firebase sign out
            FirebaseAuth.getInstance().signOut();

            // 2. Clear saved login credentials
            getSharedPreferences("login", MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply();

            // 3. Go to Login screen and clear back stack
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}
