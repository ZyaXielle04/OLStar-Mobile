package com.zyacodes.olstar.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.zyacodes.olstar.MainActivity;
import com.zyacodes.olstar.R;
import android.content.Intent;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONObject;

public class FCMService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID = "trip_alerts";
    private static final String CHANNEL_ID_DND = "trip_alerts_critical"; // Separate channel for critical alerts

    // Replace with your actual server URL when deployed
    private static final String SERVER_URL = "https://olstar.onrender.com/api/driver/register-token";

    private static OkHttpClient httpClient; // Make it static to share across instances
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "🚀 FCMService STARTED");

        // Initialize HTTP client
        initHttpClient();

        // SAFELY create or update channels
        createAlarmChannels();
    }

    private void initHttpClient() {
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();
            Log.d(TAG, "✅ HTTP Client initialized");
        }
    }

    private void acquireWakeLock() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                if (wakeLock == null) {
                    wakeLock = powerManager.newWakeLock(
                            PowerManager.PARTIAL_WAKE_LOCK |
                                    PowerManager.ACQUIRE_CAUSES_WAKEUP |
                                    PowerManager.SCREEN_DIM_WAKE_LOCK,
                            "OLStar:FCMLock"
                    );
                }
                if (!wakeLock.isHeld()) {
                    wakeLock.acquire(10000); // Hold for 10 seconds max
                    Log.d(TAG, "✅ Wake lock acquired");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to acquire wake lock: " + e.getMessage());
        }
    }

    private void turnScreenOn() {
        try {
            // Method 1: Using PowerManager to wake up screen
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                if (wakeLock == null) {
                    wakeLock = powerManager.newWakeLock(
                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                                    PowerManager.ACQUIRE_CAUSES_WAKEUP,
                            "OLStar:ScreenLock"
                    );
                }
                if (!wakeLock.isHeld()) {
                    wakeLock.acquire(5000);
                }
                Log.d(TAG, "💡 Screen turned on via PowerManager");
            }

            // Method 2: Using FLAG_TURN_SCREEN_ON in the intent (alternative)
            // This is handled in the notification builder with setFullScreenIntent

        } catch (Exception e) {
            Log.e(TAG, "Failed to turn screen on: " + e.getMessage());
        }
    }

    private void createAlarmChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // Get alarm sound
                Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (alarmSound == null) {
                    alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                }

                Log.d(TAG, "🔊 USING SOUND URI: " + alarmSound);

                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build();

                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

                // ===== CHANNEL 1: Normal Alarm Channel =====
                NotificationChannel existingChannel = manager.getNotificationChannel(CHANNEL_ID);

                if (existingChannel == null) {
                    // Create new channel if it doesn't exist
                    NotificationChannel channel = new NotificationChannel(
                            CHANNEL_ID,
                            "TRIP ALERTS",
                            NotificationManager.IMPORTANCE_HIGH
                    );

                    channel.setSound(alarmSound, audioAttributes);
                    channel.enableVibration(true);
                    channel.setVibrationPattern(new long[]{0, 1000, 1000, 1000, 1000});
                    channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
                    channel.setBypassDnd(true); // Try to bypass DND
                    channel.enableLights(true);
                    channel.setLightColor(0xFFFF0000); // Red light

                    manager.createNotificationChannel(channel);
                    Log.d(TAG, "✅ NEW ALARM CHANNEL CREATED: " + CHANNEL_ID);
                } else {
                    // Update existing channel
                    existingChannel.setSound(alarmSound, audioAttributes);
                    existingChannel.setVibrationPattern(new long[]{0, 1000, 1000, 1000, 1000});
                    existingChannel.setBypassDnd(true);
                    manager.createNotificationChannel(existingChannel);
                    Log.d(TAG, "✅ EXISTING CHANNEL UPDATED: " + CHANNEL_ID);
                }

                // ===== CHANNEL 2: Critical Alert Channel (Android 14+) =====
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14
                    NotificationChannel criticalChannel = manager.getNotificationChannel(CHANNEL_ID_DND);

                    if (criticalChannel == null) {
                        criticalChannel = new NotificationChannel(
                                CHANNEL_ID_DND,
                                "CRITICAL TRIP ALERTS",
                                NotificationManager.IMPORTANCE_HIGH
                        );
                        criticalChannel.setSound(alarmSound, audioAttributes);
                        criticalChannel.enableVibration(true);
                        criticalChannel.setVibrationPattern(new long[]{0, 1000, 1000, 1000, 1000});
                        criticalChannel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
                        criticalChannel.setBypassDnd(true);
                        criticalChannel.setAllowBubbles(true);
                        criticalChannel.setShowBadge(true);

                        // Set as critical alert (Android 14+)
                        criticalChannel.setImportance(NotificationManager.IMPORTANCE_HIGH);

                        manager.createNotificationChannel(criticalChannel);
                        Log.d(TAG, "✅ CRITICAL CHANNEL CREATED: " + CHANNEL_ID_DND);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ Channel creation failed: " + e.getMessage());
            }
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Log.d(TAG, "📩 MESSAGE RECEIVED!");

        // Acquire wake lock to wake up device
        acquireWakeLock();

        String title = "🚨 TRIP ALERT";
        String body = "You have a new trip request!";
        Map<String, String> data = remoteMessage.getData();

        // Extract from notification if available
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle() != null ?
                    remoteMessage.getNotification().getTitle() : title;
            body = remoteMessage.getNotification().getBody() != null ?
                    remoteMessage.getNotification().getBody() : body;
        }

        // Extract from data if available
        if (data.containsKey("title")) title = data.get("title");
        if (data.containsKey("body")) body = data.get("body");

        // TRIGGER VIBRATION IMMEDIATELY (bypassing DND)
        vibratePhone(true); // Force vibration

        // Turn screen on
        turnScreenOn();

        // SHOW NOTIFICATION WITH ALARM
        showAlarmNotification(title, body, data);
    }

    private void vibratePhone(boolean force) {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                // Create vibration pattern that continues even in DND
                long[] pattern = new long[]{0, 1000, 500, 1000, 500, 1000, 500, 1000};

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    VibrationEffect effect = VibrationEffect.createWaveform(pattern, 0); // 0 = repeat

                    // For Android 8+, we can use vibration with audio attributes
                    if (force) {
                        // This attempts to bypass DND for vibration
                        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .build();
                        vibrator.vibrate(effect, audioAttributes);
                    } else {
                        vibrator.vibrate(effect);
                    }
                } else {
                    vibrator.vibrate(pattern, 0);
                }
                Log.d(TAG, "📳 VIBRATION TRIGGERED (force=" + force + ")");
            }
        } catch (Exception e) {
            Log.e(TAG, "Vibration error: " + e.getMessage());
        }
    }

    private void showAlarmNotification(String title, String body, Map<String, String> data) {
        try {
            // Create intent with FLAG_ACTIVITY_NEW_TASK to show even when locked
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.setAction(Long.toString(System.currentTimeMillis()));

            // Safely add FLAG_TURN_SCREEN_ON if available
            try {
                // Use reflection to check if the flag exists (safer approach)
                int flagTurnScreenOn = Intent.class.getField("FLAG_ACTIVITY_TURN_SCREEN_ON").getInt(null);
                if (Build.VERSION.SDK_INT >= 27) {
                    intent.addFlags(flagTurnScreenOn);
                    Log.d(TAG, "Added FLAG_ACTIVITY_TURN_SCREEN_ON");
                }
            } catch (Exception e) {
                // Flag doesn't exist in this SDK version, use wake lock instead
                Log.d(TAG, "FLAG_ACTIVITY_TURN_SCREEN_ON not available, using wake lock");
            }

            // Add data to intent
            if (data != null) {
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    intent.putExtra(entry.getKey(), entry.getValue());
                    Log.d(TAG, "📊 Data: " + entry.getKey() + " = " + entry.getValue());
                }
            }

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    (int) System.currentTimeMillis(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Get alarm sound
            Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmSound == null) {
                alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }

            // Fallback to system default if still null
            if (alarmSound == null) {
                alarmSound = Settings.System.DEFAULT_ALARM_ALERT_URI;
            }

            Log.d(TAG, "🔊 NOTIFICATION USING SOUND: " + alarmSound);

            // Determine which channel to use
            String channelId = CHANNEL_ID;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                channelId = CHANNEL_ID_DND; // Use critical channel for Android 14+
            }

            // BUILD NOTIFICATION WITH ALL POSSIBLE FLAGS
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setVibrate(new long[]{0, 1000, 1000, 1000})
                    .setSound(alarmSound)
                    .setFullScreenIntent(pendingIntent, true)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .setTimeoutAfter(60000); // Auto-dismiss after 60 seconds

            // Additional flags for Android
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder.setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            }

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            // Use unique ID for each notification
            int notificationId = (int) (System.currentTimeMillis() % 10000);
            manager.notify(notificationId, builder.build());

            Log.d(TAG, "✅ ALARM NOTIFICATION SHOWN! ID: " + notificationId + " on channel: " + channelId);

            // CONTINUOUS VIBRATION WAVES
            for (int i = 0; i < 5; i++) {
                int delay = 1000 + (i * 2000);
                new android.os.Handler().postDelayed(() -> vibratePhone(true), delay);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to show notification: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Release wake lock if held
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "Wake lock released");
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "🔑 New token: " + token);

        // Initialize HTTP client if needed
        initHttpClient();

        // Store token
        getSharedPreferences("fcm_prefs", MODE_PRIVATE)
                .edit()
                .putString("fcm_token", token)
                .apply();

        // Send token to server if user is logged in
        String userId = getSharedPreferences("OLStarPrefs", MODE_PRIVATE)
                .getString("user_id", null);

        if (userId != null) {
            sendTokenToBackend(userId, token);
        } else {
            // Store token to send after login
            getSharedPreferences("OLStarPrefs", MODE_PRIVATE)
                    .edit()
                    .putString("pending_fcm_token", token)
                    .apply();
            Log.d(TAG, "📦 User not logged in - storing token for later");
        }
    }

    /**
     * Call this method from your LoginActivity after successful login
     */
    public static void onUserLogin(Context context, String userId) {
        Log.d(TAG, "👤 User logged in: " + userId);

        // Save user ID
        context.getSharedPreferences("OLStarPrefs", Context.MODE_PRIVATE)
                .edit()
                .putString("user_id", userId)
                .apply();

        // Initialize HTTP client if needed
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();
            Log.d(TAG, "✅ HTTP Client initialized in static method");
        }

        // Check if there's a pending token to send
        String pendingToken = context.getSharedPreferences("OLStarPrefs", Context.MODE_PRIVATE)
                .getString("pending_fcm_token", null);

        if (pendingToken != null) {
            Log.d(TAG, "📦 Found pending token, sending to backend");
            sendTokenToBackendStatic(context, userId, pendingToken);
        } else {
            Log.d(TAG, "No pending token found");

            // Also try to get the latest token from fcm_prefs
            String currentToken = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
                    .getString("fcm_token", null);

            if (currentToken != null) {
                Log.d(TAG, "📦 Found current token in fcm_prefs, sending to backend");
                sendTokenToBackendStatic(context, userId, currentToken);
            }
        }
    }

    private static void sendTokenToBackendStatic(Context context, String userId, String token) {
        // Double-check HTTP client is initialized
        if (httpClient == null) {
            Log.e(TAG, "❌ HTTP Client is null, initializing...");
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();

            if (httpClient == null) {
                Log.e(TAG, "❌ Failed to initialize HTTP Client");
                return;
            }
        }

        final OkHttpClient client = httpClient; // Capture for use in thread

        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("user_id", userId);
                json.put("fcm_token", token);

                JSONObject deviceInfo = new JSONObject();
                deviceInfo.put("platform", "android");
                deviceInfo.put("model", Build.MODEL);
                deviceInfo.put("manufacturer", Build.MANUFACTURER);
                deviceInfo.put("os_version", Build.VERSION.RELEASE);
                deviceInfo.put("sdk_version", Build.VERSION.SDK_INT);
                deviceInfo.put("device", Build.DEVICE);
                deviceInfo.put("product", Build.PRODUCT);
                json.put("device_info", deviceInfo);

                RequestBody body = RequestBody.create(
                        json.toString(),
                        MediaType.parse("application/json; charset=utf-8")
                );

                Request request = new Request.Builder()
                        .url(SERVER_URL)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "application/json")
                        .build();

                Log.d(TAG, "📤 Sending token to server: " + SERVER_URL);

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        Log.d(TAG, "✅ Token registered successfully. Response: " + responseBody);

                        // Clear pending token using the provided Context
                        context.getSharedPreferences("OLStarPrefs", Context.MODE_PRIVATE)
                                .edit()
                                .remove("pending_fcm_token")
                                .apply();

                        // Mark that token was sent
                        context.getSharedPreferences("OLStarPrefs", Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean("fcm_token_sent", true)
                                .putLong("fcm_token_sent_time", System.currentTimeMillis())
                                .apply();
                    } else {
                        String errorBody = response.body() != null ? response.body().string() : "No error body";
                        Log.e(TAG, "❌ Token registration failed: " + response.code() + " - " + errorBody);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Error sending token to server: " + e.getMessage(), e);
            }
        }).start();
    }

    private void sendTokenToBackend(String userId, String token) {
        // Instance method that uses the service's Context
        sendTokenToBackendStatic(this, userId, token);
    }

    /**
     * Test method to trigger a test notification
     */
    public void sendTestNotification() {
        showAlarmNotification("🔔 TEST ALARM", "This is a test alarm notification", null);
    }
}