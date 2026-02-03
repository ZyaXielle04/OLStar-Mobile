package com.zyacodes.olstar.drivers;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.android.gms.location.*;
import com.google.android.libraries.navigation.*;
import com.google.firebase.database.*;
import com.zyacodes.olstar.GasPaymentDialog;
import com.zyacodes.olstar.R;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;

import com.zyacodes.olstar.controllers.GlobalFabController;

public class TripActiveActivity extends AppCompatActivity {

    private static final int LOCATION_REQUEST_CODE = 1000;
    private static final int CAMERA_REQUEST_CODE = 2000;

    private NavigationView navigationView;
    private Navigator navigator;
    private String pendingNavAddress = null; // <-- pending destination

    private TextView tvDestination, tvDirection, tvDistance;
    private SeekBar slideAction;

    private String tripId, pickupAddress, dropOffAddress;
    private double destinationLat, destinationLng;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private DatabaseReference statusRef;

    private boolean photoTaken = false;
    private Uri photoUri;
    private String currentStatus = "";
    private String currentPhotoType = "";
    private boolean dialogShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_active);

        GlobalFabController.attach(this, v -> {
            GasPaymentDialog.show(this);
        });

        initCloudinary();

        navigationView = findViewById(R.id.navigation_view);
        navigationView.onCreate(savedInstanceState);

        tvDestination = findViewById(R.id.tvDestination);
        tvDirection = findViewById(R.id.tvDirection);
        tvDistance = findViewById(R.id.tvDistance);
        slideAction = findViewById(R.id.slideArrivedPickup);
        slideAction.setVisibility(View.GONE);

        tripId = getIntent().getStringExtra("tripId");
        pickupAddress = getIntent().getStringExtra("pickup");
        dropOffAddress = getIntent().getStringExtra("dropOff");

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        statusRef = FirebaseDatabase.getInstance(
                        "https://olstar-5e642-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("schedules")
                .child(tripId)
                .child("status");

        setupSeekbarListener();

        // Initialize navigator as early as possible
        checkLocationPermission();

        // Start listening to status changes
        listenToStatus();
    }

    // ---------------- CLOUDINARY ----------------
    private void initCloudinary() {
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("cloud_name", "dekdyp7bb");
            config.put("api_key", "214836573954892");
            MediaManager.init(this, config);
        } catch (IllegalStateException ignored) {}
    }

    // ---------------- STATUS LISTENER ----------------
    private void listenToStatus() {
        DatabaseReference photoRef = FirebaseDatabase.getInstance(
                        "https://olstar-5e642-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("schedules")
                .child(tripId)
                .child("PhotoUrl");

        statusRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                currentStatus = snapshot.getValue(String.class);

                runOnUiThread(() -> {
                    String navAddress = null;

                    switch (currentStatus) {
                        case "Confirmed":
                            tvDestination.setText("Pickup: " + pickupAddress);
                            navAddress = pickupAddress;
                            slideAction.setVisibility(View.GONE);
                            break;

                        case "Arrived":
                            tvDestination.setText("Drop-off: " + dropOffAddress);
                            navAddress = dropOffAddress;
                            slideAction.setVisibility(View.VISIBLE);
                            break;

                        case "On Route":
                            tvDestination.setText("Drop-off: " + dropOffAddress);
                            navAddress = dropOffAddress;
                            slideAction.setVisibility(View.GONE);
                            slideAction.setEnabled(false);
                            break;

                        case "Completed":
                            tvDestination.setText("Trip Completed");
                            slideAction.setVisibility(View.GONE);
                            break;

                        default:
                            tvDestination.setText("Pickup: " + pickupAddress);
                            navAddress = pickupAddress;
                            slideAction.setVisibility(View.GONE);
                    }

                    // ---------------- NAVIGATION ----------------
                    if (navAddress != null) {
                        if (navigator != null) {
                            navigateToAddress(navAddress);
                        } else {
                            pendingNavAddress = navAddress; // <-- store for later
                        }
                    }

                    // Handle camera & seekbar
                    if ("Arrived".equals(currentStatus)) {
                        currentPhotoType = "arrivalPhotoUrl";
                        checkPhotoAndSetupSeekbar(photoRef, currentPhotoType);
                    } else if ("Completed".equals(currentStatus)) {
                        currentPhotoType = "dropOffPhotoUrl";
                        checkPhotoAndSetupSeekbar(photoRef, currentPhotoType);

                        showTripCompletedDialog();
                    } else if ("Confirmed".equals(currentStatus)) {
                        slideAction.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void showTripCompletedDialog() {
        if (dialogShown || isFinishing() || isDestroyed()) return;
        dialogShown = true;

        runOnUiThread(() -> {
            AlertDialog dialog = new AlertDialog.Builder(TripActiveActivity.this)
                    .setTitle("Trip Completed")
                    .setMessage("This trip is completed. We will redirect you to your dashboard in a few seconds.")
                    .setCancelable(false)
                    .create();
            dialog.show();

            slideAction.postDelayed(() -> {
                dialog.dismiss();
                Intent i = new Intent(TripActiveActivity.this, TripsActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                finish();
            }, 3000);
        });
    }

    private void checkPhotoAndSetupSeekbar(DatabaseReference photoRef, String photoType) {
        photoRef.child(photoType).get().addOnCompleteListener(task -> {
            runOnUiThread(() -> {
                slideAction.setVisibility(View.VISIBLE);
                slideAction.setEnabled(true);
                slideAction.setProgress(3);

                if (task.isSuccessful()) {
                    Object url = task.getResult().getValue();
                    if (url == null || url.toString().isEmpty()) {
                        enforceCameraCapture();
                        photoTaken = false;
                    } else {
                        photoTaken = true;
                    }
                } else {
                    enforceCameraCapture();
                    photoTaken = false;
                }
            });
        });
    }

    // ---------------- SEEKBAR ----------------
    private void setupSeekbarListener() {
        slideAction.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {}
            @Override public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                if (s.getProgress() >= 95) {
                    if ("Confirmed".equals(currentStatus)) {
                        statusRef.setValue("Arrived").addOnSuccessListener(aVoid -> resetSeekbar());
                    } else if ("Arrived".equals(currentStatus)) {
                        statusRef.setValue("On Route").addOnSuccessListener(aVoid -> resetSeekbar());
                    } else if ("On Route".equals(currentStatus)) {
                        currentPhotoType = "dropOffPhotoUrl";
                        slideAction.setEnabled(false);
                        enforceCameraCapture();
                        return;
                    } else {
                        resetSeekbar();
                    }
                } else {
                    resetSeekbar();
                }
            }

            private void resetSeekbar() {
                runOnUiThread(() -> {
                    slideAction.setProgress(3);
                    slideAction.setEnabled(true);
                });
            }
        });
    }

    // ---------------- NAVIGATION ----------------
    private void initNavigation() {
        NavigationApi.getNavigator(this, new NavigationApi.NavigatorListener() {
            @Override
            public void onNavigatorReady(@NonNull Navigator nav) {
                navigator = nav;
                navigator.setAudioGuidance(Navigator.AudioGuidance.VOICE_ALERTS_AND_GUIDANCE);
                navigator.setTaskRemovedBehavior(Navigator.TaskRemovedBehavior.QUIT_SERVICE);

                // Navigate pending destination if exists
                if (pendingNavAddress != null) {
                    navigateToAddress(pendingNavAddress);
                    pendingNavAddress = null;
                }
            }

            @Override public void onError(int errorCode) {}
        });
    }

    private void navigateToAddress(String address) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> list = geocoder.getFromLocationName(address, 1);
                if (list == null || list.isEmpty()) return;

                Address a = list.get(0);
                destinationLat = a.getLatitude();
                destinationLng = a.getLongitude();

                if (navigator != null) {
                    navigator.setDestination(
                            Waypoint.builder()
                                    .setLatLng(destinationLat, destinationLng)
                                    .build());
                    startDistanceUpdates();
                }
            } catch (Exception ignored) {}
        });
    }

    // ---------------- DISTANCE ----------------
    private void startDistanceUpdates() {
        LocationRequest req = LocationRequest.create()
                .setInterval(2000)
                .setFastestInterval(1000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult res) {
                Location loc = res.getLastLocation();
                if (loc == null) return;

                float[] d = new float[1];
                Location.distanceBetween(
                        loc.getLatitude(), loc.getLongitude(),
                        destinationLat, destinationLng, d);

                tvDistance.setText((int) d[0] + " m remaining");

                if (d[0] <= 200) {
                    slideAction.setVisibility(View.VISIBLE);
                    slideAction.setEnabled(true);
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper());
        }
    }

    // ---------------- CAMERA ----------------
    private void enforceCameraCapture() {
        if (isFinishing() || isDestroyed()) return;

        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;

            new AlertDialog.Builder(TripActiveActivity.this)
                    .setCancelable(false)
                    .setTitle("Verification Required")
                    .setMessage("You must take a photo at this location to continue.")
                    .setPositiveButton("Open Camera",
                            (d, w) -> checkCameraPermission())
                    .show();
        });
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_REQUEST_CODE
            );
        } else {
            openCamera();
        }
    }

    private void openCamera() {
        try {
            File f = createImageFile();
            photoUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    f
            );

            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            cameraLauncher.launch(i);

        } catch (Exception e) {
            checkCameraPermission();
        }
    }

    private File createImageFile() throws IOException {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile("PHOTO_" + ts, ".jpg", dir);
    }

    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    r -> {
                        if (r.getResultCode() == RESULT_OK) {
                            uploadPhoto(currentPhotoType);
                        } else {
                            enforceCameraCapture();
                        }
                    });

    // ---------------- CLOUDINARY UPLOAD ----------------
    private void uploadPhoto(String photoType) {
        MediaManager.get()
                .upload(photoUri)
                .unsigned("OLStar")
                .callback(new UploadCallback() {
                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String url = resultData.get("secure_url").toString();

                        DatabaseReference tripRef = FirebaseDatabase.getInstance(
                                        "https://olstar-5e642-default-rtdb.asia-southeast1.firebasedatabase.app/")
                                .getReference("schedules")
                                .child(tripId);

                        tripRef.child("PhotoUrl")
                                .child(photoType)
                                .setValue(url)
                                .addOnSuccessListener(aVoid -> {

                                    photoTaken = true;

                                    if ("dropOffPhotoUrl".equals(photoType)) {
                                        tripRef.child("status").setValue("Completed");
                                    }

                                    runOnUiThread(() -> {
                                        slideAction.setProgress(3);
                                        slideAction.setEnabled(true);
                                        slideAction.setVisibility(View.GONE);
                                    });
                                });
                    }

                    @Override
                    public void onError(String r, ErrorInfo e) {
                        enforceCameraCapture();
                    }

                    @Override public void onStart(String r) {}
                    @Override public void onProgress(String r, long b, long t) {}
                    @Override public void onReschedule(String r, ErrorInfo e) {}
                })
                .dispatch();
    }

    // ---------------- PERMISSIONS ----------------
    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_REQUEST_CODE
            );
        } else {
            initNavigation();
        }
    }

    // ---------------- LIFECYCLE ----------------
    @Override protected void onStart() { super.onStart(); navigationView.onStart(); }
    @Override protected void onResume() { super.onResume(); navigationView.onResume(); }
    @Override protected void onPause() { super.onPause(); navigationView.onPause(); }
    @Override protected void onStop() { super.onStop(); navigationView.onStop(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (navigator != null) navigator.cleanup();
        if (locationCallback != null)
            fusedLocationClient.removeLocationUpdates(locationCallback);
        navigationView.onDestroy();
    }
}
