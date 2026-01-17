package com.zyacodes.olstar.models;

public class TripModel {

    private String tripId;
    private String pickup;
    private String dropOff;
    private String status;
    private String date;
    private String time;

    public TripModel() {
        // Required for Firebase
    }

    public TripModel(String tripId, String pickup, String dropOff, String status, String date, String time) {
        this.tripId = tripId;
        this.pickup = pickup;
        this.dropOff = dropOff;
        this.status = status;
        this.date = date;
        this.time = time;
    }

    public String getTripId() {
        return tripId;
    }

    public String getPickup() {
        return pickup;
    }

    public String getDropOff() {
        return dropOff;
    }

    public String getStatus() {
        return status;
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }
}
