package com.zyacodes.olstar.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.zyacodes.olstar.R;
import com.zyacodes.olstar.models.RequestModel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RequestsAdapter extends RecyclerView.Adapter<RequestsAdapter.RequestViewHolder> {

    private Context context;
    private List<RequestModel> requestList;

    public RequestsAdapter(Context context, List<RequestModel> requestList) {
        this.context = context;
        this.requestList = requestList;
    }

    @NonNull
    @Override
    public RequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_request, parent, false);
        return new RequestViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RequestViewHolder holder, int position) {
        RequestModel request = requestList.get(position);

        // Request type
        holder.tvRequestType.setText("Gas Payment");

        // Amount
        holder.tvAmount.setText("₱" + request.getAmount());

        // Timestamp
        holder.tvDate.setText(formatDate(request.getTimestamp()));

        // Status mapping
        String status = request.getStatus();
        if (status == null) status = "pending";

        switch (status.toLowerCase()) {
            case "paid":
                holder.tvRequestStatus.setText("Paid");
                holder.tvRequestStatus.setTextColor(0xFF2E7D32); // green
                break;
            case "denied":
                holder.tvRequestStatus.setText("Denied");
                holder.tvRequestStatus.setTextColor(0xFFC62828); // red
                break;
            case "pending":
            default:
                holder.tvRequestStatus.setText("Pending");
                holder.tvRequestStatus.setTextColor(0xFFFFA000); // orange
                break;
        }

        // Admin reply image
        if (request.getImageReply() != null &&
                !request.getImageReply().isEmpty()) {

            holder.imgAdminReply.setVisibility(View.VISIBLE);

            Glide.with(context)
                    .load(request.getImageReply())
                    .placeholder(R.drawable.ic_image_placeholder) // optional
                    .into(holder.imgAdminReply);
        } else {
            holder.imgAdminReply.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return requestList.size();
    }

    private String formatDate(long timestamp) {
        return new SimpleDateFormat(
                "dd MMM yyyy HH:mm",
                Locale.getDefault()
        ).format(new Date(timestamp));
    }

    static class RequestViewHolder extends RecyclerView.ViewHolder {

        TextView tvRequestType, tvRequestStatus, tvAmount, tvDate;
        ImageView imgAdminReply;

        public RequestViewHolder(@NonNull View itemView) {
            super(itemView);

            tvRequestType = itemView.findViewById(R.id.tvRequestType);
            tvRequestStatus = itemView.findViewById(R.id.tvRequestStatus);
            tvAmount = itemView.findViewById(R.id.tvRequestAmount);
            tvDate = itemView.findViewById(R.id.tvRequestTimestamp);
            imgAdminReply = itemView.findViewById(R.id.ivAdminReply);
        }
    }
}
