package com.zyacodes.olstar.drivers;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.database.*;
import com.zyacodes.olstar.R;
import com.zyacodes.olstar.adapters.TripAdapter;
import com.zyacodes.olstar.models.TripModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class TripsActivity extends AppCompatActivity {

    private RecyclerView rvTrips;
    private TripAdapter adapter;
    private List<Object> combinedList;
    private TextView tvEmpty;
    private DatabaseReference schedulesRef;
    private String driverPhone;

    private final ZoneId PH_ZONE = ZoneId.of("Asia/Manila");

    private Uri currentPhotoUri;
    private String currentPhotoType;
    private TripModel currentTripForPhoto;
    private ActivityResultLauncher<Intent> cameraLauncher;

    private LinearLayout navDashboard, navTrips, navRequests, navSettings, navHistory;
    private static final int REQUEST_CAMERA_PERMISSION = 101;
    private TextView tvDate;

    private FusedLocationProviderClient fusedLocationClient;
    private String currentCoordinates = "Unknown";

    // RFID data cache - plateNumber -> RFIDCardData
    private Map<String, RFIDCardData> rfidCache = new HashMap<>();

    // Helper class for RFID data
    private static class RFIDCardData {
        double balance;
        long lastUpdated;

        RFIDCardData(double balance, long lastUpdated) {
            this.balance = balance;
            this.lastUpdated = lastUpdated;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trips);

        rvTrips = findViewById(R.id.rvTrips);
        rvTrips.setLayoutManager(new LinearLayoutManager(this));

        tvEmpty = findViewById(R.id.tvEmpty);
        tvDate = findViewById(R.id.tvDate);

        combinedList = new ArrayList<>();
        adapter = new TripAdapter(this, combinedList, this::onTakePhoto, this::onClientNoShow);
        rvTrips.setAdapter(adapter);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        initViews();
        setupBottomNavigation();
        loadUserFromPrefs();
        setupFirebase();
        loadRFIDDataAndTrips(); // Changed: Load RFID data first
        initCloudinary();
        initCameraLauncher();
        displayCurrentDate();
    }

    private void onClientNoShow(TripModel trip) {
        Toast.makeText(this, "Processing no show for Trip #" + trip.getTripNumber() + "...", Toast.LENGTH_SHORT).show();

        schedulesRef.child(trip.getTripId())
                .child("clientNoShow")
                .setValue(true)
                .addOnSuccessListener(aVoid -> {
                    trip.setNoShow(true);
                    adapter.notifyDataSetChanged();

                    Toast.makeText(TripsActivity.this,
                            "✅ Trip #" + trip.getTripNumber() + " marked as Client No Show",
                            Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(TripsActivity.this,
                            "Failed to mark no show: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void displayCurrentDate() {
        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.US);
        tvDate.setText(LocalDate.now(PH_ZONE).format(formatter));
    }

    private void initViews() {
        navDashboard = findViewById(R.id.navDashboard);
        navTrips = findViewById(R.id.navTrips);
        navRequests = findViewById(R.id.navRequests);
        navSettings = findViewById(R.id.navSettings);
        navHistory = findViewById(R.id.navHistory);
    }

    private void setupBottomNavigation() {
        navDashboard.setOnClickListener(v -> {
            startActivity(new Intent(this, DashboardActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });

        navHistory.setOnClickListener(v -> {
            startActivity(new Intent(this, HistoryActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });

        navRequests.setOnClickListener(v -> {
            startActivity(new Intent(this, RequestsActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });

        navSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });
    }

    private void loadUserFromPrefs() {
        driverPhone = getSharedPreferences("login", MODE_PRIVATE)
                .getString("phone", null);
        if (driverPhone == null) {
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void setupFirebase() {
        schedulesRef = FirebaseDatabase.getInstance(
                "https://olstar-5e642-default-rtdb.asia-southeast1.firebasedatabase.app/"
        ).getReference("schedules");
    }

    /**
     * Load RFID data first, then load trips
     */
    private void loadRFIDDataAndTrips() {
        DatabaseReference rfidRef = FirebaseDatabase.getInstance(
                "https://olstar-5e642-default-rtdb.asia-southeast1.firebasedatabase.app/"
        ).getReference("rfidCards");

        rfidRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot rfidSnapshot) {
                rfidCache.clear();

                for (DataSnapshot cardSnapshot : rfidSnapshot.getChildren()) {
                    String plateNumber = cardSnapshot.child("plateNumber").getValue(String.class);
                    if (plateNumber != null && !plateNumber.isEmpty()) {
                        Double balance = cardSnapshot.child("balance").getValue(Double.class);
                        Long lastUpdated = cardSnapshot.child("lastUpdated").getValue(Long.class);

                        rfidCache.put(plateNumber, new RFIDCardData(
                                balance != null ? balance : 0.0,
                                lastUpdated != null ? lastUpdated : 0L
                        ));
                    }
                }

                // Now load trips with the RFID cache populated
                loadTodayTrips();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TripsActivity.this, "Failed to load RFID data", Toast.LENGTH_SHORT).show();
                // Still try to load trips even if RFID fails
                loadTodayTrips();
            }
        });
    }

    /**
     * TODAY + TOMORROW with HEADERS
     * Sorted: Yesterday Uncompleted, Today a→b, Tomorrow a→b
     */
    private void loadTodayTrips() {
        LocalDate today = LocalDate.now(PH_ZONE);
        LocalDate tomorrow = today.plusDays(1);
        LocalDate yesterday = today.minusDays(1);

        DateTimeFormatter timeFormatter =
                DateTimeFormatter.ofPattern("h:mma", Locale.US);
        DateTimeFormatter dateDisplayFormatter =
                DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US);

        schedulesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<TripModel> allTodayTrips = new ArrayList<>();
                List<TripModel> allTomorrowTrips = new ArrayList<>();
                List<TripModel> allYesterdayTrips = new ArrayList<>();

                List<TripModel> activeTodayTrips = new ArrayList<>();
                List<TripModel> activeTomorrowTrips = new ArrayList<>();
                List<TripModel> activeYesterdayTrips = new ArrayList<>();

                for (DataSnapshot sched : snapshot.getChildren()) {
                    String phone = sched.child("current").child("cellPhone").getValue(String.class);
                    if (phone == null || !phone.equals(driverPhone)) continue;

                    String dateStr = sched.child("date").getValue(String.class);
                    String status = sched.child("status").getValue(String.class);
                    if (dateStr == null) continue;

                    LocalDate tripDate;
                    try {
                        tripDate = LocalDate.parse(dateStr);
                    } catch (Exception e) {
                        continue;
                    }

                    // Get plate number to look up RFID data
                    String plateNumber = sched.child("plateNumber").getValue(String.class);

                    // Get RFID data from cache
                    RFIDCardData rfidData = rfidCache.get(plateNumber);
                    double rfidBalance = rfidData != null ? rfidData.balance : 0.0;
                    long rfidLastUpdated = rfidData != null ? rfidData.lastUpdated : 0L;
                    String company = sched.child("company").getValue(String.class);
                    String pax = sched.child("pax").getValue(String.class);

                    TripModel trip = new TripModel(
                            sched.getKey(),
                            sched.child("pickup").getValue(String.class),
                            sched.child("dropOff").getValue(String.class),
                            status,
                            dateStr,
                            sched.child("time").getValue(String.class),
                            sched.child("flightNumber").getValue(String.class),
                            sched.child("clientName").getValue(String.class),
                            sched.child("tripType").getValue(String.class),
                            sched.child("driverRate").getValue(String.class),
                            sched.child("contactNumber").getValue(String.class),
                            sched.child("current").child("driverName").getValue(String.class),
                            driverPhone,
                            sched.child("transportUnit").getValue(String.class),
                            sched.child("unitType").getValue(String.class),
                            plateNumber,
                            sched.child("color").getValue(String.class),
                            rfidBalance,
                            rfidLastUpdated,
                            company,
                            pax
                    );

                    boolean isIncomplete = status != null &&
                            !"Completed".equalsIgnoreCase(status) &&
                            !"Cancelled".equalsIgnoreCase(status) &&
                            !"No Show".equalsIgnoreCase(status);

                    // Categorize trips by date
                    if (tripDate.equals(yesterday)) {
                        allYesterdayTrips.add(trip);
                        if (isIncomplete) {
                            activeYesterdayTrips.add(trip);
                        }
                    } else if (tripDate.equals(today)) {
                        allTodayTrips.add(trip);
                        if (isIncomplete) {
                            activeTodayTrips.add(trip);
                        }
                    } else if (tripDate.equals(tomorrow)) {
                        allTomorrowTrips.add(trip);
                        if (isIncomplete) {
                            activeTomorrowTrips.add(trip);
                        }
                    }
                }

                // Sort all trips by time to assign permanent trip numbers
                sortTripsByTime(allYesterdayTrips, timeFormatter);
                sortTripsByTime(allTodayTrips, timeFormatter);
                sortTripsByTime(allTomorrowTrips, timeFormatter);

                // Sort active trips by time for display
                sortTripsByTime(activeYesterdayTrips, timeFormatter);
                sortTripsByTime(activeTodayTrips, timeFormatter);
                sortTripsByTime(activeTomorrowTrips, timeFormatter);

                // Assign PERMANENT trip numbers based on time order
                int tripNumber = 1;
                for (TripModel trip : allYesterdayTrips) {
                    trip.setTripNumber(tripNumber++);
                }

                tripNumber = 1;
                for (TripModel trip : allTodayTrips) {
                    trip.setTripNumber(tripNumber++);
                }

                tripNumber = 1;
                for (TripModel trip : allTomorrowTrips) {
                    trip.setTripNumber(tripNumber++);
                }

                // Build combined list with headers
                combinedList.clear();

                // 1. Add yesterday's incomplete trips with header
                if (!activeYesterdayTrips.isEmpty()) {
                    combinedList.add("YESTERDAY'S TRIPS (Ongoing) - " + yesterday.format(dateDisplayFormatter));
                    combinedList.addAll(activeYesterdayTrips);
                }

                // 2. Add today's incomplete trips with header
                if (!activeTodayTrips.isEmpty()) {
                    combinedList.add("TODAY'S TRIPS - " + today.format(dateDisplayFormatter));
                    combinedList.addAll(activeTodayTrips);
                }

                // 3. Add tomorrow's incomplete trips with header
                if (!activeTomorrowTrips.isEmpty()) {
                    combinedList.add("TOMORROW'S TRIPS - " + tomorrow.format(dateDisplayFormatter));
                    combinedList.addAll(activeTomorrowTrips);
                }

                adapter.notifyDataSetChanged();

                tvEmpty.setVisibility(combinedList.isEmpty() ? TextView.VISIBLE : TextView.GONE);
                rvTrips.setVisibility(combinedList.isEmpty() ? RecyclerView.GONE : RecyclerView.VISIBLE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TripsActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void sortTripsByTime(List<TripModel> trips, DateTimeFormatter timeFormatter) {
        Collections.sort(trips, (t1, t2) -> {
            try {
                String time1 = t1.getTime().replace("12Noon", "12:00PM").toUpperCase().trim();
                String time2 = t2.getTime().replace("12Noon", "12:00PM").toUpperCase().trim();

                LocalTime lt1 = LocalTime.parse(time1, timeFormatter);
                LocalTime lt2 = LocalTime.parse(time2, timeFormatter);

                return lt1.compareTo(lt2);
            } catch (Exception e) {
                return 0;
            }
        });
    }

    // ---------------- CAMERA / CLOUDINARY ----------------
    private void initCloudinary() {
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("cloud_name", "dekdyp7bb");
            config.put("api_key", "214836573954892");
            MediaManager.init(this, config);
        } catch (IllegalStateException ignored) {}
    }

    private void initCameraLauncher() {
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && currentTripForPhoto != null) {
                        if (currentPhotoUri != null) {
                            fetchCoordinatesAndAnnotate();
                        }
                    } else {
                        Toast.makeText(this, "Photo is required!", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    // ------------------ ANNOTATE IMAGE WITH DETAILS ------------------
    private void fetchCoordinatesAndAnnotate() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            String coords = location.getLatitude() + ", " + location.getLongitude();
                            String address = getAddressFromCoordinates(location.getLatitude(), location.getLongitude());
                            currentCoordinates = coords + " (" + address + ")";
                        } else {
                            currentCoordinates = "Unknown Location";
                        }
                        annotateAndSavePhoto();
                    })
                    .addOnFailureListener(e -> {
                        currentCoordinates = "Unknown Location";
                        annotateAndSavePhoto();
                    });
        } else {
            currentCoordinates = "Unknown Location";
            annotateAndSavePhoto();
        }
    }

    private void annotateAndSavePhoto() {
        String dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());

        Bitmap annotatedBitmap = burnTextOnImage(currentPhotoUri,
                currentTripForPhoto.getDriverName(),
                currentTripForPhoto.getClientName(),
                dateTime,
                currentCoordinates);

        if (annotatedBitmap != null) {
            try {
                File annotatedFile = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                        "OLStar/ANNOTATED_" + System.currentTimeMillis() + ".jpg");
                if (!annotatedFile.getParentFile().exists()) annotatedFile.getParentFile().mkdirs();

                FileOutputStream out = new FileOutputStream(annotatedFile);
                annotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
                out.flush();
                out.close();

                saveImageToGallery(annotatedFile);
                currentPhotoUri = Uri.fromFile(annotatedFile);
                uploadPhotoToCloudinary(currentTripForPhoto, currentPhotoType, currentPhotoUri);

            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to save annotated image", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Failed to annotate image", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap burnTextOnImage(Uri photoUri, String driverName, String clientName, String dateTime, String coordinates) {
        try {
            Bitmap original = MediaStore.Images.Media.getBitmap(getContentResolver(), photoUri);
            Bitmap mutableBitmap = original.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(mutableBitmap);

            Paint paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setTextSize(125);
            paint.setAntiAlias(true);
            paint.setShadowLayer(25f, 10f, 10f, Color.WHITE);

            int padding = 40;
            float x, y;

            List<String> lines = Arrays.asList(
                    "Driver: " + driverName,
                    "Client: " + clientName,
                    "Date: " + dateTime,
                    "Coordinates: " + coordinates
            );

            y = mutableBitmap.getHeight() - padding;

            for (int i = lines.size() - 1; i >= 0; i--) {
                String line = lines.get(i);
                List<String> wrapped = wrapText(line, paint, mutableBitmap.getWidth() - 2 * padding);

                for (int j = wrapped.size() - 1; j >= 0; j--) {
                    String wrapLine = wrapped.get(j);
                    float textWidth = paint.measureText(wrapLine);
                    x = mutableBitmap.getWidth() - textWidth - padding;
                    canvas.drawText(wrapLine, x, y, paint);
                    y -= paint.getTextSize() + 30;
                }
            }

            return mutableBitmap;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private List<String> wrapText(String text, Paint paint, float maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();

        for (String word : words) {
            if (paint.measureText(line + " " + word) > maxWidth) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                if (line.length() > 0) line.append(" ");
                line.append(word);
            }
        }

        if (line.length() > 0) lines.add(line.toString());

        return lines;
    }

    private String getAddressFromCoordinates(double latitude, double longitude) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder sb = new StringBuilder();
                if (address.getThoroughfare() != null) sb.append(address.getThoroughfare()).append(", ");
                if (address.getLocality() != null) sb.append(address.getLocality()).append(", ");
                if (address.getAdminArea() != null) sb.append(address.getAdminArea()).append(", ");
                if (address.getCountryName() != null) sb.append(address.getCountryName());
                return sb.toString();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "Unknown Location";
    }

    // ---------------- CAMERA / PHOTO LOGIC ----------------
    public void onTakePhoto(TripModel trip, String photoType) {
        currentTripForPhoto = trip;
        currentPhotoType = photoType;

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            openCamera(trip, photoType);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (currentTripForPhoto != null && currentPhotoType != null) {
                    openCamera(currentTripForPhoto, currentPhotoType);
                }
            } else {
                Toast.makeText(this, "Camera permission is required to take a photo.", Toast.LENGTH_SHORT).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void openCamera(TripModel trip, String photoType) {
        try {
            File photoFile = createImageFile();
            currentPhotoUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".provider",
                    photoFile);

            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            cameraLauncher.launch(i);

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Cannot open camera", Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() throws IOException {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "OLStar");
        if (!dir.exists()) dir.mkdirs();
        return File.createTempFile("PHOTO_" + ts, ".jpg", dir);
    }

    private void saveImageToGallery(File file) {
        try {
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri contentUri = Uri.fromFile(file);
            mediaScanIntent.setData(contentUri);
            sendBroadcast(mediaScanIntent);
            Toast.makeText(this, "Saved to gallery", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void uploadPhotoToCloudinary(TripModel trip, String photoType, Uri uri) {
        MediaManager.get()
                .upload(uri)
                .unsigned("OLStar")
                .callback(new UploadCallback() {
                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String url = resultData.get("secure_url").toString();

                        DatabaseReference photoRef = schedulesRef
                                .child(trip.getTripId())
                                .child("PhotoUrl")
                                .child(photoType);

                        photoRef.setValue(url).addOnSuccessListener(aVoid -> {
                            String newStatus = getNextStatus(trip.getStatus());
                            DatabaseReference statusRef = schedulesRef
                                    .child(trip.getTripId())
                                    .child("status");

                            statusRef.setValue(newStatus).addOnSuccessListener(v -> {
                                trip.setStatus(newStatus);
                                for (int i = 0; i < combinedList.size(); i++) {
                                    Object item = combinedList.get(i);
                                    if (item instanceof TripModel) {
                                        TripModel t = (TripModel) item;
                                        if (t.getTripId().equals(trip.getTripId())) {
                                            adapter.notifyItemChanged(i);
                                            break;
                                        }
                                    }
                                }

                                Toast.makeText(TripsActivity.this,
                                        "Photo uploaded & status updated to " + newStatus,
                                        Toast.LENGTH_SHORT).show();
                            });
                        });
                    }

                    @Override public void onError(String requestId, ErrorInfo error) {
                        Toast.makeText(TripsActivity.this, "Upload failed!", Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onStart(String requestId) {}
                    @Override public void onProgress(String requestId, long bytes, long totalBytes) {}
                    @Override public void onReschedule(String requestId, ErrorInfo error) {}
                })
                .dispatch();
    }

    private String getNextStatus(String currentStatus) {
        switch (currentStatus) {
            case "Pending": return "Confirmed";
            case "Confirmed": return "Arrived";
            case "Arrived": return "On Route";
            case "On Route": return "Completed";
            default: return currentStatus;
        }
    }
}