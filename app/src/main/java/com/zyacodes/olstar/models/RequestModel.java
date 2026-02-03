package com.zyacodes.olstar.models;

public class RequestModel {

    private String amount;
    private String requestedBy;
    private String status;
    private long timestamp;
    private String imageReply;

    // Required for Firebase
    public RequestModel() {}

    public String getAmount() {
        return amount;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public String getStatus() {
        return status;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getImageReply() {
        return imageReply;
    }
}
