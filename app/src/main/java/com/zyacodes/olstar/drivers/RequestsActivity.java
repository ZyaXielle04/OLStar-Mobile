package com.zyacodes.olstar.drivers;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import com.zyacodes.olstar.GasPaymentDialog;
import com.zyacodes.olstar.R;
import com.zyacodes.olstar.adapters.RequestsAdapter;
import com.zyacodes.olstar.controllers.GlobalFabController;
import com.zyacodes.olstar.models.RequestModel;

import java.util.ArrayList;
import java.util.List;

public class RequestsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private RequestsAdapter adapter;
    private List<RequestModel> requestList;
    private LinearLayout navDashboard, navTrips, navRequests, navSettings, navHistory;

    private DatabaseReference requestsRef;
    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_requests);

        GlobalFabController.attach(this, v -> {
            GasPaymentDialog.show(this);
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        uid = FirebaseAuth.getInstance().getUid();

        requestsRef = FirebaseDatabase
                .getInstance("https://olstar-5e642-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("requests");

        initRecyclerView();
        loadRequests();
        setupBottomNavigation();
    }

    private void initRecyclerView() {
        recyclerView = findViewById(R.id.recyclerRequests);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        requestList = new ArrayList<>();
        adapter = new RequestsAdapter(this, requestList);
        recyclerView.setAdapter(adapter);
    }

    private void loadRequests() {
        requestsRef.orderByChild("requestedBy")
                .equalTo(uid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        requestList.clear();

                        for (DataSnapshot snap : snapshot.getChildren()) {
                            RequestModel model =
                                    snap.getValue(RequestModel.class);
                            if (model != null) {
                                requestList.add(model);
                            }
                        }

                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        error.toException().printStackTrace();
                    }
                });
    }
    private void setupBottomNavigation() {

        navDashboard = findViewById(R.id.navDashboard);
        navTrips = findViewById(R.id.navTrips);
        navRequests = findViewById(R.id.navRequests);
        navSettings = findViewById(R.id.navSettings);
        navHistory = findViewById(R.id.navHistory);

        navDashboard.setOnClickListener(v -> {
            Intent intent = new Intent(this, DashboardActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });

        navTrips.setOnClickListener(v -> {
            Intent intent = new Intent(this, TripsActivity.class);
            startActivity(intent);
            // Fade animation
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });

        navHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, HistoryActivity.class);
            startActivity(intent);
            // Fade animation
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });

        navRequests.setOnClickListener(v -> {
        });

        navSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });
    }
}
