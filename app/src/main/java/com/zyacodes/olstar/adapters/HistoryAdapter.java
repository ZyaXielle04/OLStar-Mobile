package com.zyacodes.olstar.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.zyacodes.olstar.R;
import com.zyacodes.olstar.models.TripModel;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private Context context;
    private List<TripModel> historyList;
    private SimpleDateFormat displayDateFormat;
    private SimpleDateFormat inputDateFormat;
    private SimpleDateFormat displayTimeFormat;
    private SimpleDateFormat inputTimeFormat;

    public HistoryAdapter(Context context, List<TripModel> historyList) {
        this.context = context;
        this.historyList = historyList;

        // Initialize formatters with Asia/Manila timezone
        TimeZone manilaTimeZone = TimeZone.getTimeZone("Asia/Manila");

        displayDateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        displayDateFormat.setTimeZone(manilaTimeZone);

        inputDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        inputDateFormat.setTimeZone(manilaTimeZone);

        // Format for displaying time in 12-hour with AM/PM (e.g., "3:00 AM")
        displayTimeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        displayTimeFormat.setTimeZone(manilaTimeZone);

        // Format for parsing time from database (e.g., "3:00AM" or "8:55PM")
        // Using "h:mma" pattern for no space between time and AM/PM
        inputTimeFormat = new SimpleDateFormat("h:mma", Locale.getDefault());
        inputTimeFormat.setTimeZone(manilaTimeZone);
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        TripModel trip = historyList.get(position);

        // Set Trip ID
        String tripId = trip.getTripId();
        if (tripId != null && !tripId.isEmpty() && !tripId.equals("null")) {
            holder.tvTripId.setText("Trip #" + tripId);
        } else {
            holder.tvTripId.setText("Trip #N/A");
        }

        // Set Flight Number
        String flightNumber = trip.getFlightNumber();
        if (flightNumber != null && !flightNumber.isEmpty() && !flightNumber.equals("null")) {
            holder.tvFlightNo.setText("Flight: " + flightNumber);
            holder.tvFlightNo.setVisibility(View.VISIBLE);
        } else {
            holder.tvFlightNo.setVisibility(View.GONE);
        }

        // Set Client Name
        String clientName = trip.getClientName();
        if (clientName != null && !clientName.isEmpty() && !clientName.equals("null")) {
            holder.tvClientName.setText("Client: " + clientName);
            holder.tvClientName.setVisibility(View.VISIBLE);
        } else {
            holder.tvClientName.setText("Client: Guest");
            holder.tvClientName.setVisibility(View.VISIBLE);
        }

        // Set Pickup
        String pickup = trip.getPickup();
        if (pickup != null && !pickup.isEmpty() && !pickup.equals("null")) {
            holder.tvPickup.setText("Pickup: " + pickup);
            holder.tvPickup.setVisibility(View.VISIBLE);
        } else {
            holder.tvPickup.setVisibility(View.GONE);
        }

        // Set Drop-off
        String dropOff = trip.getDropOff();
        if (dropOff != null && !dropOff.isEmpty() && !dropOff.equals("null")) {
            holder.tvDropoff.setText("Drop-off: " + dropOff);
            holder.tvDropoff.setVisibility(View.VISIBLE);
        } else {
            holder.tvDropoff.setVisibility(View.GONE);
        }

        // Set Date (formatted)
        String date = trip.getDate();
        if (date != null && !date.isEmpty() && !date.equals("null")) {
            String formattedDate = formatDate(date);
            holder.tvDate.setText("Date: " + formattedDate);
            holder.tvDate.setVisibility(View.VISIBLE);
        } else {
            holder.tvDate.setVisibility(View.GONE);
        }

        // Set Time (formatted) - Now properly handles "3:00AM" and "8:55PM" format
        String time = trip.getTime();
        if (time != null && !time.isEmpty() && !time.equals("null")) {
            String formattedTime = formatTime(time);
            holder.tvTime.setText("Time: " + formattedTime);
            holder.tvTime.setVisibility(View.VISIBLE);

            // Debug log to check the conversion
            Log.d("HistoryAdapter", "Original time: " + time + " -> Formatted: " + formattedTime);
        } else {
            holder.tvTime.setVisibility(View.GONE);
        }

        // Set Driver Rate
        String driverRate = trip.getDriverRate();
        if (driverRate != null && !driverRate.isEmpty() && !driverRate.equals("null")) {
            try {
                double rate = Double.parseDouble(driverRate);
                holder.tvDriverRate.setText(String.format(Locale.getDefault(), "My Rate: ₱%.2f", rate));
            } catch (NumberFormatException e) {
                holder.tvDriverRate.setText("My Rate: ₱" + driverRate);
            }
        } else {
            holder.tvDriverRate.setText("My Rate: ₱0.00");
        }
    }

    @Override
    public int getItemCount() {
        return historyList != null ? historyList.size() : 0;
    }

    public void updateList(List<TripModel> newList) {
        this.historyList = newList;
        notifyDataSetChanged();
    }

    // Helper method to format date from yyyy-MM-dd to MMM dd, yyyy
    private String formatDate(String dateStr) {
        try {
            java.util.Date date = inputDateFormat.parse(dateStr);
            if (date != null) {
                return displayDateFormat.format(date);
            }
        } catch (ParseException e) {
            Log.e("HistoryAdapter", "Error parsing date: " + dateStr, e);
            return dateStr;
        }
        return dateStr;
    }

    // Helper method to format time from "h:mma" to "h:mm a"
    // Example: "3:00AM" -> "3:00 AM", "8:55PM" -> "8:55 PM"
    private String formatTime(String timeStr) {
        try {
            // Parse the time string from database (e.g., "3:00AM")
            java.util.Date time = inputTimeFormat.parse(timeStr);
            if (time != null) {
                // Format with a space between time and AM/PM (e.g., "3:00 AM")
                return displayTimeFormat.format(time);
            }
        } catch (ParseException e) {
            Log.e("HistoryAdapter", "Error parsing time: " + timeStr, e);

            // If parsing fails, try adding a space between time and AM/PM
            try {
                // Try to add a space: "3:00AM" -> "3:00 AM"
                String fixedTime = timeStr.replaceAll("(AM|PM)$", " $1");
                java.util.Date time = displayTimeFormat.parse(fixedTime);
                if (time != null) {
                    return displayTimeFormat.format(time);
                }
            } catch (ParseException e2) {
                Log.e("HistoryAdapter", "Error parsing time with fix: " + timeStr, e2);
                return timeStr;
            }
        }
        return timeStr;
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {

        TextView tvTripId, tvFlightNo, tvClientName, tvPickup, tvDropoff,
                tvDate, tvTime, tvDriverRate;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);

            tvTripId = itemView.findViewById(R.id.tvTripId);
            tvFlightNo = itemView.findViewById(R.id.tvFlightNo);
            tvClientName = itemView.findViewById(R.id.tvClientName);
            tvPickup = itemView.findViewById(R.id.tvPickup);
            tvDropoff = itemView.findViewById(R.id.tvDropoff);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvDriverRate = itemView.findViewById(R.id.tvDriverRate);
        }
    }
}