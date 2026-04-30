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
    private String company;
    private String pax;

    // ---------------- RFID Fields ----------------
    private double rfidBalance;
    private long rfidLastUpdated;

    // ---------------- State fields ----------------
    private boolean expanded = false;
    private int slideProgress = 0;
    private int queueNumber = 0;
    private int tripNumber = 0;
    private int totalTripsToday = 0;

    public TripModel() {}

    public TripModel(String tripId, String pickup, String dropOff, String status,
                     String date, String time, String flightNumber, String clientName,
                     String tripType, String driverRate, String contactNumber,
                     String driverName, String driverPhone, String transportUnit,
                     String unitType, String plateNumber, String color,
                     double rfidBalance, long rfidLastUpdated, String company, String pax) {
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
        this.rfidBalance = rfidBalance;
        this.rfidLastUpdated = rfidLastUpdated;
        this.company = company;
        this.pax = pax;
    }

    // ---------------- Getters ----------------
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

    // ---------------- RFID Getters/Setters ----------------
    public double getRfidBalance() { return rfidBalance; }
    public void setRfidBalance(double rfidBalance) { this.rfidBalance = rfidBalance; }

    public long getRfidLastUpdated() { return rfidLastUpdated; }
    public void setRfidLastUpdated(long rfidLastUpdated) { this.rfidLastUpdated = rfidLastUpdated; }

    // ---------------- State Getters/Setters ----------------
    public boolean isExpanded() { return expanded; }
    public void setExpanded(boolean expanded) { this.expanded = expanded; }

    public int getSlideProgress() { return slideProgress; }
    public void setSlideProgress(int slideProgress) { this.slideProgress = slideProgress; }

    public int getQueueNumber() { return queueNumber; }
    public void setQueueNumber(int queueNumber) { this.queueNumber = queueNumber; }

    public int getTripNumber() { return tripNumber; }
    public void setTripNumber(int tripNumber) { this.tripNumber = tripNumber; }

    public int getTotalTripsToday() { return totalTripsToday; }
    public void setTotalTripsToday(int totalTripsToday) { this.totalTripsToday = totalTripsToday; }

    // ---------------- Status Helper Methods ----------------
    public void setStatus(String status) { this.status = status; }

    public boolean isCompleted() {
        return "Completed".equalsIgnoreCase(status);
    }

    public boolean isCancelled() {
        return "Cancelled".equalsIgnoreCase(status);
    }

    public boolean isPending() {
        return "Pending".equalsIgnoreCase(status);
    }

    public boolean isConfirmed() {
        return "Confirmed".equalsIgnoreCase(status);
    }

    public boolean isArrived() {
        return "Arrived".equalsIgnoreCase(status);
    }

    public boolean isOnRoute() {
        return "On Route".equalsIgnoreCase(status);
    }

    public boolean isActive() {
        return !isCompleted() && !isCancelled();
    }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getPax() { return pax; }
    public void setPax(String pax) { this.pax = pax; }

    // ---------------- Display Helper ----------------
    public String getDisplayTripNumber() {
        if (tripNumber > 0) {
            return "Trip #" + tripNumber;
        }
        return "Trip ID: " + tripId;
    }

    public boolean isNoShow() {
        return "No Show".equalsIgnoreCase(status);
    }

    public String getFullTripNumber() {
        if (tripNumber > 0 && totalTripsToday > 0) {
            return "Trip " + tripNumber + " of " + totalTripsToday;
        } else if (tripNumber > 0) {
            return "Trip #" + tripNumber;
        }
        return "Trip ID: " + tripId;
    }

    public void setNoShow(boolean noShow) {
        // We don't actually change the status, just track it in memory for UI
        // This is just to update the UI appearance
    }
}