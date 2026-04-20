package com.zyacodes.olstar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class GasPaymentDialog {

    private static final int REQ_MILEAGE = 101;
    private static final int REQ_RECEIPT = 102;
    private static final int REQ_QR = 103;

    private static final String UNSIGNED_PRESET = "OLStar";

    private static String currentPhotoPath;

    private static String mileageUrl, receiptUrl, qrUrl;

    public static void show(Activity activity) {
        initCloudinary(activity);

        View view = LayoutInflater.from(activity)
                .inflate(R.layout.dialog_gas_payment, null);

        EditText etAmount = view.findViewById(R.id.etAmount);
        Button btnMileage = view.findViewById(R.id.btnMileage);
        Button btnReceipt = view.findViewById(R.id.btnReceipt);
        Button btnQr = view.findViewById(R.id.btnQr);

        btnMileage.setOnClickListener(v -> openCamera(activity, REQ_MILEAGE));
        btnReceipt.setOnClickListener(v -> openCamera(activity, REQ_RECEIPT));
        btnQr.setOnClickListener(v -> openCamera(activity, REQ_QR));

        new AlertDialog.Builder(activity)
                .setTitle("Gas Payment Request")
                .setView(view)
                .setCancelable(true)
                .setPositiveButton("Submit", (dialog, which) -> {
                    String amount = etAmount.getText().toString().trim();

                    if (amount.isEmpty()) {
                        toast(activity, "Amount is required");
                        return;
                    }

                    if (mileageUrl == null || receiptUrl == null || qrUrl == null) {
                        toast(activity, "All photos must be uploaded first");
                        return;
                    }

                    submitToRTDB(activity, amount);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void initCloudinary(Activity activity) {
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("cloud_name", "dekdyp7bb");
            config.put("api_key", "214836573954892");
            MediaManager.init(activity, config);
        } catch (IllegalStateException ignored) {}
    }

    private static void openCamera(Activity activity, int requestCode) {
        if (activity.checkSelfPermission(android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activity.requestPermissions(
                        new String[]{android.Manifest.permission.CAMERA},
                        requestCode
                );
            }
            return;
        }

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(activity.getPackageManager()) == null) {
            toast(activity, "No camera app found");
            return;
        }

        File photoFile;
        try {
            // Create OLStar subdirectory in Pictures
            File storageDir = new File(
                    activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "OLStar"
            );

            // Create directory if it doesn't exist
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }

            // Create the image file in the OLStar subdirectory
            photoFile = File.createTempFile(
                    "IMG_" + System.currentTimeMillis(),
                    ".jpg",
                    storageDir  // <- Now creates file in Pictures/OLStar/
            );

            // Make sure the file is writable
            photoFile.setWritable(true, false);

        } catch (IOException e) {
            e.printStackTrace();
            toast(activity, "Failed to create image file: " + e.getMessage());
            return;
        }

        currentPhotoPath = photoFile.getAbsolutePath();
        System.out.println("Photo path: " + currentPhotoPath);

        Uri photoURI = FileProvider.getUriForFile(
                activity,
                activity.getPackageName() + ".provider",  // Make sure this matches manifest
                photoFile
        );

        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        // Grant URI permission to camera app
        activity.grantUriPermission(
                "com.android.camera",
                photoURI,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        );

        activity.startActivityForResult(intent, requestCode);
    }

    public static void handleActivityResult(int requestCode, int resultCode, Intent data, Activity activity) {
        if (resultCode != Activity.RESULT_OK) {
            toast(activity, "Photo capture cancelled or failed");
            return;
        }

        if (currentPhotoPath == null || currentPhotoPath.isEmpty()) {
            toast(activity, "No photo path saved");
            return;
        }

        File f = new File(currentPhotoPath);
        if (!f.exists()) {
            toast(activity, "Photo file missing: " + currentPhotoPath);
            return;
        }

        toast(activity, "Uploading photo...");

        // Immediately upload after capture
        upload(currentPhotoPath, url -> {
            switch (requestCode) {
                case REQ_MILEAGE:
                    mileageUrl = url;
                    toast(activity, "Mileage photo uploaded");
                    break;
                case REQ_RECEIPT:
                    receiptUrl = url;
                    toast(activity, "Receipt photo uploaded");
                    break;
                case REQ_QR:
                    qrUrl = url;
                    toast(activity, "QR photo uploaded");
                    break;
            }
        });
    }

    private static void upload(String path, UploadCallbackCustom callback) {
        MediaManager.get().upload(path)
                .unsigned(UNSIGNED_PRESET)
                .callback(new UploadCallback() {
                    @Override public void onStart(String requestId) {}
                    @Override public void onProgress(String requestId, long bytes, long total) {}
                    @Override public void onSuccess(String requestId, Map result) {
                        callback.onUploaded(result.get("secure_url").toString());
                    }
                    @Override public void onError(String requestId, ErrorInfo error) {
                        toast(null, "Upload failed: " + error.getDescription());
                    }
                    @Override public void onReschedule(String requestId, ErrorInfo error) {}
                })
                .dispatch();
    }

    private static void submitToRTDB(Activity activity, String amount) {
        String uid = FirebaseAuth.getInstance().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance(
                        "https://olstar-5e642-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("requests")
                .push();

        Map<String, Object> data = new HashMap<>();
        data.put("requestedBy", uid);
        data.put("amount", amount);
        data.put("receiptUrl", receiptUrl);
        data.put("gcashUrl", qrUrl);
        data.put("mileageURL", mileageUrl);
        data.put("status", "pending");
        data.put("timestamp", ServerValue.TIMESTAMP);

        ref.setValue(data)
                .addOnSuccessListener(a -> toast(activity, "Request submitted"))
                .addOnFailureListener(e -> toast(activity, "Submit failed: " + e.getMessage()));
    }

    private static void toast(Activity activity, String message) {
        if (activity != null) {
            activity.runOnUiThread(() ->
                    Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            );
        } else {
            System.out.println("GasPaymentDialog: " + message);
        }
    }

    public interface UploadCallbackCustom {
        void onUploaded(String url);
    }
}