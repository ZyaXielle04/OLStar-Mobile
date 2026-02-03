package com.zyacodes.olstar.models;

public class TripModel {

    private String tripId;
    private String pickup;
    private String dropOff;
    private String status;
    private String date;
    private String time;
    private String flightNumber;
    private String clientName;
    private String tripType;
    private String driverRate;

    public TripModel() {
        // Required for Firebase
    }

    public TripModel(String tripId, String pickup, String dropOff, String status,
                     String date, String time, String flightNumber, String clientName, String tripType, String driverRate) {
        this.tripId = tripId;
        this.pickup = pickup;
        this.dropOff = dropOff;
        this.status = status;
        this.date = date;
        this.time = time;
        this.flightNumber = flightNumber;
        this.clientName = clientName;
        this.tripType = tripType;
        this.driverRate = driverRate;
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

    public String getFlightNumber() {
        return flightNumber;
    }
    public String getClientName() { return clientName; }

    public String getDriverRate() { return driverRate; }
    public String getTripType() { return tripType; }
}
