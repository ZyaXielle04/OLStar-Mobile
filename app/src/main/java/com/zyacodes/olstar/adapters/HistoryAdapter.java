package com.zyacodes.olstar.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.zyacodes.olstar.R;
import com.zyacodes.olstar.models.TripModel;

import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private Context context;
    private List<TripModel> historyList;

    public HistoryAdapter(Context context, List<TripModel> historyList) {
        this.context = context;
        this.historyList = historyList;
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

        holder.tvTripId.setText("Trip ID: " + trip.getTripId());
        holder.tvFlightNo.setText("Flight Number: " + trip.getFlightNumber());
        holder.tvPickup.setText("Pickup: " + trip.getPickup());
        holder.tvDropoff.setText("Drop-off: " + trip.getDropOff());
        holder.tvDate.setText("Date: " + trip.getDate());
        holder.tvTime.setText("Time: " + trip.getTime());
        holder.tvDriverRate.setText("Driver Rate: ₱" + trip.getDriverRate());
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {

        TextView tvTripId, tvFlightNo, tvPickup, tvDropoff,
                tvDate, tvTime, tvDriverRate;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);

            tvTripId = itemView.findViewById(R.id.tvTripId);
            tvFlightNo = itemView.findViewById(R.id.tvFlightNo);
            tvPickup = itemView.findViewById(R.id.tvPickup);
            tvDropoff = itemView.findViewById(R.id.tvDropoff);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvDriverRate = itemView.findViewById(R.id.tvDriverRate);
        }
    }
}
