package com.zyacodes.olstar.adapters;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.provider.AlarmClock;
import android.view.Gravity;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import java.util.Map;
import java.util.HashMap;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;
import androidx.recyclerview.widget.RecyclerView;

import com.zyacodes.olstar.R;
import com.zyacodes.olstar.drivers.TripActiveActivity;
import com.zyacodes.olstar.models.TripModel;
import android.provider.AlarmClock;


import java.net.URLEncoder;
import java.util.Calendar;
import java.util.List;

public class TripAdapter extends RecyclerView.Adapter<TripAdapter.ViewHolder> {

    private final List<Object> items; // Can be String (header) or TripModel
    private final Context context;

    // Callback for photo taking
    private final OnPhotoClickListener photoClickListener;

    public interface OnPhotoClickListener {
        void onTakePhoto(TripModel trip, String photoType);
    }

    public interface OnClientNoShowListener {
        void onClientNoShow(TripModel trip);
    }

    private final OnClientNoShowListener noShowListener;

    public TripAdapter(Context context, List<Object> items, OnPhotoClickListener photoListener, OnClientNoShowListener noShowListener) {
        this.context = context;
        this.items = items;
        this.photoClickListener = photoListener;
        this.noShowListener = noShowListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_trip, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Object item = items.get(position);

        if (item instanceof String) {
            // This is a header item (e.g., "TODAY'S TRIPS - Wednesday, February 12, 2025")
            String headerText = (String) item;
            bindHeader(h, headerText, position);
        } else if (item instanceof TripModel) {
            // This is a trip item
            TripModel trip = (TripModel) item;
            bindTrip(h, trip, position);
        }
    }

    private void bindHeader(ViewHolder h, String headerText, int position) {
        // Show header layout, hide trip layout
        h.dateHeaderLayout.setVisibility(View.VISIBLE);
        h.mainCard.setVisibility(View.GONE);
        h.hiddenLayout.setVisibility(View.GONE);
        h.statusProgressContainer.setVisibility(View.GONE);

        // Set header text
        h.tvHeaderTitle.setText(headerText);

        // Show/hide top divider based on position
        if (position == 0) {
            h.headerDividerTop.setVisibility(View.GONE);
        } else {
            h.headerDividerTop.setVisibility(View.VISIBLE);
        }
    }

    private void bindTrip(ViewHolder h, TripModel trip, int position) {
        // Show trip layout, hide header layout
        h.dateHeaderLayout.setVisibility(View.GONE);
        h.mainCard.setVisibility(View.VISIBLE);
        h.hiddenLayout.setVisibility(View.VISIBLE);
        h.statusProgressContainer.setVisibility(View.VISIBLE);

        String status = trip.getStatus();

        String tripType = trip.getTripType();
        if (tripType != null && !tripType.isEmpty()) {
            h.tvTripType.setText("Trip Type: " + tripType);
            h.tvTripType.setVisibility(View.VISIBLE);
        } else {
            h.tvTripType.setVisibility(View.GONE); // Hide if no trip type
        }

        h.tvFlightNo.setText(trip.getFlightNumber());
        h.tvClientName.setText("Client Name: " + trip.getClientName());
        h.tvContactNumber.setText("Contact No.: " + trip.getContactNumber());
        h.tvPickup.setText("Pickup: " + trip.getPickup());
        h.tvDropoff.setText("Drop-off: " + trip.getDropOff());
        h.tvDate.setText("Date: " + trip.getDate());
        h.tvTime.setText("Time: " + trip.getTime());
        h.tvStatus.setText("Status: " + status);
        h.statusProgressContainer.removeAllViews();

        // Display the PERMANENT trip number prominently using the badge
        int tripNumber = trip.getTripNumber();
        if (tripNumber > 0) {
            // Show the queue badge with the trip number
            if (h.tvQueueBadge != null) {
                h.tvQueueBadge.setText(String.valueOf(tripNumber));
                h.tvQueueBadge.setVisibility(View.VISIBLE);

                // Change badge color based on status
                if (trip.isCompleted()) {
                    h.tvQueueBadge.setBackgroundResource(R.drawable.circle_gray_background);
                } else if (trip.isCancelled() || trip.isNoShow()) {
                    h.tvQueueBadge.setBackgroundResource(R.drawable.circle_red_background);
                } else {
                    h.tvQueueBadge.setBackgroundResource(R.drawable.circle_blue_background);
                }
            }

            // Update the trip ID text with trip number
            h.tvTripId.setText("Trip #" + tripNumber + " • ID: " + trip.getTripId());
        } else {
            // Hide badge if no trip number
            if (h.tvQueueBadge != null) {
                h.tvQueueBadge.setVisibility(View.GONE);
            }
            h.tvTripId.setText("Trip ID: " + trip.getTripId());
        }

        // Visual indicator for completed/cancelled/no show trips
        if (trip.isCompleted() || trip.isCancelled() || trip.isNoShow()) {
            h.mainCard.setAlpha(0.6f);
        } else {
            h.mainCard.setAlpha(1.0f);
        }

        // Safer version with null checks
        String transportUnit = trip.getTransportUnit() != null ? trip.getTransportUnit() : "N/A";
        String plateNumber = trip.getPlateNumber() != null ? trip.getPlateNumber() : "N/A";
        String unitType = trip.getUnitType() != null ? trip.getUnitType() : "N/A";

        String vehicleInfo = String.format("Vehicle: %s | %s (%s)",
                transportUnit, plateNumber, unitType);
        h.tvVehicle.setText(vehicleInfo);

        // ---------------- RFID BALANCE DISPLAY ----------------
        double rfidBalance = trip.getRfidBalance();
        long rfidLastUpdated = trip.getRfidLastUpdated();

        // Set RFID Balance with color coding
        String balanceText = String.format(Locale.US, "RFID Balance: ₱%,.2f", rfidBalance);
        h.tvRFID.setText(balanceText);

        // Color code the balance based on amount
        if (rfidBalance < 200) {
            h.tvRFID.setTextColor(Color.parseColor("#D32F2F")); // Red for low balance
        } else if (rfidBalance < 500) {
            h.tvRFID.setTextColor(Color.parseColor("#F57C00")); // Orange for warning
        } else {
            h.tvRFID.setTextColor(Color.parseColor("#2E7D32")); // Green for good balance
        }

        // Set RFID Last Updated
        if (rfidLastUpdated > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.US);
            String dateStr = sdf.format(new Date(rfidLastUpdated));
            h.tvRFIDlastUpdated.setText("RFID Last updated: " + dateStr);
            h.tvRFIDlastUpdated.setTextColor(Color.parseColor("#666666"));
        } else {
            h.tvRFIDlastUpdated.setText("RFID Last updated: Never");
            h.tvRFIDlastUpdated.setTextColor(Color.parseColor("#999999"));
        }

        // Status Progress Steps
        String[] statusKeys = {"Pending", "Confirmed", "Arrived", "On Route", "Completed"};
        boolean cancelled = "Cancelled".equalsIgnoreCase(trip.getStatus());
        boolean noShow = "No Show".equalsIgnoreCase(trip.getStatus());
        int currentIndex = -1;
        for (int i = 0; i < statusKeys.length; i++) {
            if (statusKeys[i].equalsIgnoreCase(trip.getStatus())) {
                currentIndex = i;
                break;
            }
        }

        // Create a horizontal container for the steps
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Map for labels
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("Pending", "The Driver is preparing to dispatch.");
        statusMap.put("Confirmed", "Driver has departed");
        statusMap.put("Arrived", "Driver has arrived");
        statusMap.put("On Route", "Service Start");
        statusMap.put("Completed", "Service finished");
        statusMap.put("Cancelled", "Booking Cancelled");
        statusMap.put("No Show", "Client No Show");

        for (int i = 0; i < statusKeys.length; i++) {
            LinearLayout step = new LinearLayout(context);
            step.setOrientation(LinearLayout.VERTICAL);
            step.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            step.setGravity(Gravity.CENTER_HORIZONTAL);

            // Circle
            TextView circle = new TextView(context);
            circle.setText(String.valueOf(i + 1));
            circle.setGravity(Gravity.CENTER);
            circle.setWidth(60);
            circle.setHeight(60);
            circle.setBackgroundResource(R.drawable.circle_background);

            if (cancelled || noShow) {
                // Cancelled/No Show - all steps RED
                circle.setBackgroundColor(Color.parseColor("#F44336")); // Keep red for cancelled
                circle.setTextColor(Color.WHITE);

            } else if (i <= currentIndex) {
                // Completed or current step
                circle.setBackgroundColor(Color.parseColor("#427AA1"));
                circle.setTextColor(Color.BLACK); // Black text for better contrast on light blue

            } else {
                // Future steps - use a lighter gray or keep as is
                circle.setBackgroundColor(Color.parseColor("#E0E0E0"));
                circle.setTextColor(Color.BLACK);
            }

            // Label
            TextView label = new TextView(context);
            label.setGravity(Gravity.CENTER);
            label.setTextSize(10);
            label.setTextColor(Color.BLACK);
            if (cancelled) {
                label.setText(i == 2 ? "Booking Cancelled" : "");
            } else if (noShow) {
                label.setText(i == 2 ? "Client No Show" : "");
            } else {
                label.setText(statusMap.get(statusKeys[i]));
            }

            step.addView(circle);
            step.addView(label);

            // Line (except last)
            if (i < statusKeys.length - 1) {
                View line = new View(context);
                LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                        0, 5, 1f);
                line.setLayoutParams(lineParams);
                if (cancelled || noShow) {
                    line.setBackgroundColor(Color.RED);
                } else if (i < currentIndex) {
                    line.setBackgroundColor(Color.BLUE);
                } else {
                    line.setBackgroundColor(Color.LTGRAY);
                }
                step.addView(line);
            }

            container.addView(step);
        }

        // Add to container in item_trip.xml
        h.statusProgressContainer.addView(container);

        // Toggle hidden layout
        h.hiddenLayout.setVisibility(trip.isExpanded() ? View.VISIBLE : View.GONE);
        h.mainCard.setOnClickListener(v -> {
            trip.setExpanded(!trip.isExpanded());
            h.hiddenLayout.setVisibility(trip.isExpanded() ? View.VISIBLE : View.GONE);
        });

        // Slide to confirm logic
        if ("Completed".equalsIgnoreCase(status) || "Cancelled".equalsIgnoreCase(status) || "No Show".equalsIgnoreCase(status)) {
            h.slideConfirm.setVisibility(View.GONE);
        } else {
            h.slideConfirm.setVisibility(View.VISIBLE);
            h.slideConfirm.setProgress(0);
            h.slideConfirm.setEnabled(true);

            h.slideConfirm.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    if (seekBar.getProgress() >= 95) {
                        seekBar.setProgress(0);

                        String photoType;
                        switch (trip.getStatus()) {
                            case "Confirmed": photoType = "confirmedPhotoUrl"; break;
                            case "Arrived": photoType = "arrivedPhotoUrl"; break;
                            case "On Route": photoType = "OnRoutePhotoUrl"; break;
                            default: photoType = "pendingPhotoUrl"; break;
                        }

                        photoClickListener.onTakePhoto(trip, photoType);
                    }
                }
            });
        }

        // ---------------- Button Functionality ----------------

        // FlightAware button
        Button btnFlightAware = h.itemView.findViewById(R.id.btnFlightAware);
        if (btnFlightAware != null) {
            btnFlightAware.setOnClickListener(v -> {
                String flightNumber = trip.getFlightNumber();
                if (flightNumber != null && !flightNumber.isEmpty()) {
                    flightNumber = flightNumber.replace(" ", "");
                    String url = "https://flightaware.com/live/flight/" + flightNumber;
                    try {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW);
                        browserIntent.setData(android.net.Uri.parse(url));
                        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        v.getContext().startActivity(browserIntent);
                    } catch (Exception e) {
                        Toast.makeText(v.getContext(), "Cannot open FlightAware. No browser found.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(v.getContext(), "Flight number not available", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Copy Client Name
        h.copyClientName.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Client Name", trip.getClientName());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(v.getContext(), "Client Name copied", Toast.LENGTH_SHORT).show();
        });

        // Copy Contact Number
        h.copyContactNumber.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Contact Number", trip.getContactNumber());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(v.getContext(), "Contact Number copied", Toast.LENGTH_SHORT).show();
        });

        // Send Itinerary
        h.sendItenary.setOnClickListener(v -> {
            String message = "Hi Good day Sir/Madam " + trip.getClientName() +
                    ", I am " + trip.getDriverName() + " of Klook X OL-Star Transport. " +
                    "I am the assigned driver for your airport transfer tomorrow. Here is complete information and details.\n\n" +
                    "🚗 DRIVER INFORMATION\n" +
                    "Name: " + trip.getDriverName() + "\n" +
                    "Mobile: " + trip.getDriverPhone() + "\n" +
                    "Vehicle: " + trip.getTransportUnit() + "\n" +
                    "Plate No: " + trip.getPlateNumber() + "\n" +
                    "Color: " + trip.getColor() + "\n\n" +
                    "Here is the itinerary of your Airport Transfer:\n\n" +
                    "✈️ FLIGHT DETAILS\n" +
                    "📅 Date: " + trip.getDate() + "\n" +
                    "⏰ Pickup Time: " + trip.getTime() + "\n" +
                    "📍 PICKUP AREA\n" + trip.getPickup() + "\n" +
                    "📍 DROP-OFF LOCATION\n" + trip.getDropOff() + "\n\n" +
                    "ℹ️ ADDITIONAL INFO\n" +
                    "You have a free one (1) hour waiting period. After that, PHP 150 per succeeding hour.";

            ClipboardManager clipboard = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Itinerary", message);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(v.getContext(), "Itinerary copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        // Driver Arrived at Pickup
        h.arrivedPickup.setOnClickListener(v -> {
            String message = "Hi Good day Sir/Madam " + trip.getClientName() +
                    ", I am " + trip.getDriverName() + " I am here at " + trip.getPickup() +
                    " waiting for your arrival.\n\n" +
                    "🚗 DRIVER INFORMATION\n" +
                    "Name: " + trip.getDriverName() + "\n" +
                    "Mobile: " + trip.getDriverPhone() + "\n" +
                    "Vehicle: " + trip.getTransportUnit() + "\n" +
                    "Plate No: " + trip.getPlateNumber() + "\n" +
                    "Color: " + trip.getColor() + "\n\n" +
                    "Please take note that we cannot stay here longer than 5 minutes due to strict security. " +
                    "After 5 minutes, I have to exit and wait for you in the parking area. Please message me once you are in " + trip.getPickup() + ".\n\n" +
                    "Take note that You have a free one (1) hour waiting period. After that, PHP 150 per succeeding hour.";

            ClipboardManager clipboard = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Arrived", message);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(v.getContext(), "Arrived message copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        // Request Client Update
        h.driverUpdate.setOnClickListener(v -> {
            String message = "Good Day, I am " + trip.getDriverName() + ". Can I ask for an update? " +
                    "Once you arrive at " + trip.getPickup() + ", please message me because I am staying in the parking area. " +
                    "We cannot wait in Arrival Area due to strict security.";

            ClipboardManager clipboard = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("DriverUpdate", message);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(v.getContext(), "Driver Update copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        // Review and Ratings
        h.reviewAndRatings.setOnClickListener(v -> {
            String message = "Good Day Ma'am/Sir " + trip.getClientName() +
                    ", I am " + trip.getDriverName() + ", your assigned driver. " +
                    "I hope you are satisfied with my driving performance. " +
                    "May I request a little time for you to give us a Review and Ratings. " +
                    "Your suggestion will help us improve my performance. Thank you and have a great day.";

            ClipboardManager clipboard = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("ReviewAndRatings", message);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(v.getContext(), "Review request copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        // Overtime Message
        h.overtimeMessage.setOnClickListener(v -> {
            String message = "Good Day, we regret to inform that you have consumed the 1 hour free waiting time. " +
                    "You consumed additional XX hour(s) and you have to pay PHP YYY.";

            ClipboardManager clipboard = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Overtime", message);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(v.getContext(), "Overtime message copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        // Back to Active Trip (Google Maps)
        h.backToActiveTrip.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), TripActiveActivity.class);
            intent.putExtra("tripId", trip.getTripId());
            intent.putExtra("pickup", trip.getPickup());
            intent.putExtra("dropOff", trip.getDropOff());
            v.getContext().startActivity(intent);
        });

        // Set Alarm for Trip
        h.btnSetAlarm.setOnClickListener(v -> {
            try {
                String time = trip.getTime().trim(); // e.g., "6:00PM"
                String[] timeParts = time.split(":");

                int hour = Integer.parseInt(timeParts[0]);
                int minute = Integer.parseInt(timeParts[1].replaceAll("[^0-9]", ""));

                // Convert to 24-hour
                if (time.toLowerCase().contains("pm") && hour < 12) hour += 12;
                else if (time.toLowerCase().contains("am") && hour == 12) hour = 0;

                // Use Calendar to adjust time
                Calendar alarmTime = Calendar.getInstance();
                alarmTime.set(Calendar.HOUR_OF_DAY, hour);
                alarmTime.set(Calendar.MINUTE, minute);
                alarmTime.set(Calendar.SECOND, 0);

                // Adjust based on trip status
                switch (trip.getStatus().toLowerCase()) {
                    case "departure":
                        alarmTime.add(Calendar.HOUR_OF_DAY, -1);
                        alarmTime.add(Calendar.MINUTE, -50);
                        alarmTime.add(Calendar.MINUTE, -40);
                        break;
                    case "arrival":
                        alarmTime.add(Calendar.MINUTE, -20);
                        alarmTime.add(Calendar.MINUTE, -15);
                        alarmTime.add(Calendar.MINUTE, -10);
                        alarmTime.add(Calendar.MINUTE, -5);
                        break;
                    case "special trip":
                        alarmTime.add(Calendar.HOUR_OF_DAY, -1);
                        alarmTime.add(Calendar.MINUTE, -50);
                        alarmTime.add(Calendar.MINUTE, -40);
                        break;
                }

                // Create alarm intent
                Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
                intent.putExtra(AlarmClock.EXTRA_HOUR, alarmTime.get(Calendar.HOUR_OF_DAY));
                intent.putExtra(AlarmClock.EXTRA_MINUTES, alarmTime.get(Calendar.MINUTE));
                intent.putExtra(AlarmClock.EXTRA_MESSAGE, "Trip Reminder: " + trip.getTripId());
                intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);

                if (intent.resolveActivity(v.getContext().getPackageManager()) != null) {
                    v.getContext().startActivity(intent);
                } else {
                    Toast.makeText(v.getContext(), "No alarm app found", Toast.LENGTH_SHORT).show();
                }

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(v.getContext(), "Failed to set alarm", Toast.LENGTH_SHORT).show();
            }
        });

        // ================ CLIENT NO SHOW BUTTON ================
        h.btnClientNoShow.setOnClickListener(v -> {
            // Only allow if trip is not already completed, cancelled, or no show
            if (trip.isCompleted() || trip.isCancelled() || trip.isNoShow()) {
                Toast.makeText(v.getContext(),
                        "Cannot mark as no show - trip is already " + trip.getStatus(),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // Show AlertDialog
            new androidx.appcompat.app.AlertDialog.Builder(v.getContext())
                    .setTitle("⚠️ Client No Show")
                    .setMessage("Are you sure you want to mark this trip as 'Client No Show'?\n\n" +
                            "🚫 Trip #" + trip.getTripNumber() + "\n" +
                            "👤 Client: " + trip.getClientName() + "\n" +
                            "⏰ Time: " + trip.getTime() + "\n" +
                            "📍 Pickup: " + trip.getPickup() + "\n\n" +
                            "This action cannot be undone.")
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton("Yes, No Show", (dialog, which) -> {
                        if (noShowListener != null) {
                            noShowListener.onClientNoShow(trip);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        // ================ END CLIENT NO SHOW BUTTON ================
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ---------------- VIEW HOLDER ----------------
    static class ViewHolder extends RecyclerView.ViewHolder {
        // Header views
        LinearLayout dateHeaderLayout;
        TextView tvHeaderTitle;
        View headerDividerTop;

        // Trip views
        public LinearLayout statusProgressContainer;
        LinearLayout mainCard, hiddenLayout;
        TextView tvTripId, tvFlightNo, tvClientName, tvDate, tvTime, tvContactNumber, tvPickup, tvDropoff, tvStatus, tvVehicle, tvQueueBadge, tvTripType;
        TextView tvRFID, tvRFIDlastUpdated; // Added RFID fields
        SeekBar slideConfirm;
        AppCompatButton btnFlightAware, copyClientName, copyContactNumber, sendItenary,
                arrivedPickup, driverUpdate, reviewAndRatings, overtimeMessage,
                backToActiveTrip, btnSetAlarm, btnClientNoShow;

        ViewHolder(@NonNull View itemView) {
            super(itemView);

            // Initialize header views
            dateHeaderLayout = itemView.findViewById(R.id.dateHeaderLayout);
            tvHeaderTitle = itemView.findViewById(R.id.tvHeaderTitle);
            headerDividerTop = itemView.findViewById(R.id.headerDividerTop);

            // Initialize trip views
            mainCard = itemView.findViewById(R.id.mainCard);
            hiddenLayout = itemView.findViewById(R.id.hiddenLayout);
            tvTripId = itemView.findViewById(R.id.tvTripId);
            tvFlightNo = itemView.findViewById(R.id.tvFlightNo);
            tvClientName = itemView.findViewById(R.id.tvClientName);
            tvContactNumber = itemView.findViewById(R.id.tvContactNumber);
            tvPickup = itemView.findViewById(R.id.tvPickup);
            tvDropoff = itemView.findViewById(R.id.tvDropoff);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvTime = itemView.findViewById(R.id.tvTime);
            slideConfirm = itemView.findViewById(R.id.slideConfirm);
            tvVehicle = itemView.findViewById(R.id.tvVehicle);
            tvQueueBadge = itemView.findViewById(R.id.tvQueueBadge);
            tvTripType = itemView.findViewById(R.id.tvTripType);

            // Initialize RFID fields
            tvRFID = itemView.findViewById(R.id.tvRFID);
            tvRFIDlastUpdated = itemView.findViewById(R.id.tvRFIDlastUpdated);

            // Corrected line
            statusProgressContainer = itemView.findViewById(R.id.statusContainer);

            // Buttons
            btnFlightAware = itemView.findViewById(R.id.btnFlightAware);
            copyClientName = itemView.findViewById(R.id.copyClientName);
            copyContactNumber = itemView.findViewById(R.id.copyContactNumber);
            sendItenary = itemView.findViewById(R.id.sendItenary);
            arrivedPickup = itemView.findViewById(R.id.arrivedPickup);
            driverUpdate = itemView.findViewById(R.id.driverUpdate);
            reviewAndRatings = itemView.findViewById(R.id.reviewAndRatings);
            overtimeMessage = itemView.findViewById(R.id.overtimeMessage);
            backToActiveTrip = itemView.findViewById(R.id.backToActiveTrip);
            btnSetAlarm = itemView.findViewById(R.id.btnSetAlarm);
            btnClientNoShow = itemView.findViewById(R.id.btnClientNoShow);
        }
    }
}