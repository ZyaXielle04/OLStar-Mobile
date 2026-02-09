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
    private String contactNumber;
    private String driverName;
    private String driverPhone;
    private String transportUnit;
    private String unitType;
    private String plateNumber;
    private String color;

    // ---------------- State fields ----------------
    private boolean expanded = false;
    private int slideProgress = 0;

    public TripModel() {}

    public TripModel(String tripId, String pickup, String dropOff, String status,
                     String date, String time, String flightNumber, String clientName,
                     String tripType, String driverRate, String contactNumber,
                     String driverName, String driverPhone, String transportUnit,
                     String unitType, String plateNumber, String color) {
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
        this.contactNumber = contactNumber;
        this.driverName = driverName;
        this.driverPhone = driverPhone;
        this.transportUnit = transportUnit;
        this.unitType = unitType;
        this.plateNumber = plateNumber;
        this.color = color;
    }

    public String getTripId() { return tripId; }
    public String getPickup() { return pickup; }
    public String getDropOff() { return dropOff; }
    public String getStatus() { return status; }
    public String getDate() { return date; }
    public String getTime() { return time; }
    public String getFlightNumber() { return flightNumber; }
    public String getClientName() { return clientName; }
    public String getDriverRate() { return driverRate; }
    public String getTripType() { return tripType; }
    public String getContactNumber() { return contactNumber; }
    public String getDriverName() { return driverName; }
    public String getDriverPhone() { return driverPhone; }
    public String getTransportUnit() { return transportUnit; }
    public String getUnitType() { return unitType; }
    public String getPlateNumber() { return plateNumber; }
    public String getColor() { return color; }

    public boolean isExpanded() { return expanded; }
    public void setExpanded(boolean expanded) { this.expanded = expanded; }

    public int getSlideProgress() { return slideProgress; }
    public void setSlideProgress(int slideProgress) { this.slideProgress = slideProgress; }
    public void setStatus(String status) { this.status = status; }
}
