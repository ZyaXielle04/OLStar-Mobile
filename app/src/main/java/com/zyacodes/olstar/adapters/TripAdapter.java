package com.zyacodes.olstar.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.zyacodes.olstar.R;
import com.zyacodes.olstar.models.TripModel;

import java.util.List;

public class TripAdapter extends RecyclerView.Adapter<TripAdapter.ViewHolder> {

    private final List<TripModel> trips;

    public TripAdapter(List<TripModel> trips) {
        this.trips = trips;
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
        TripModel trip = trips.get(position);

        h.tvTripId.setText("Trip ID: " + trip.getTripId());
        h.tvPickup.setText("Pickup: " + trip.getPickup());
        h.tvDropoff.setText("Drop-off: " + trip.getDropOff());
        h.tvDate.setText("Date: " + trip.getDate());
        h.tvTime.setText("Time: " + trip.getTime());
        h.tvStatus.setText("Status: " + trip.getStatus());

        // Status color
        if ("Completed".equalsIgnoreCase(trip.getStatus())) {
            h.tvStatus.setTextColor(Color.parseColor("#2E7D32")); // green
        } else if ("Pending".equalsIgnoreCase(trip.getStatus())) {
            h.tvStatus.setTextColor(Color.parseColor("#F9A825")); // yellow
        } else {
            h.tvStatus.setTextColor(Color.GRAY);
        }
    }

    @Override
    public int getItemCount() {
        return trips.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTripId, tvPickup, tvDropoff, tvDate, tvTime, tvStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTripId = itemView.findViewById(R.id.tvTripId);
            tvPickup = itemView.findViewById(R.id.tvPickup);
            tvDropoff = itemView.findViewById(R.id.tvDropoff);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvStatus = itemView.findViewById(R.id.tvStatus);
        }
    }
}
