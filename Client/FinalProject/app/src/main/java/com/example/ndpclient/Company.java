package com.example.ndpclient;

public class Company {
    private String companyId;
    private String name;
    private double defaultStartLat;
    private double defaultStartLon;

    // Creates a company model with its default starting location.
    public Company(String companyId, String name, double defaultStartLat, double defaultStartLon) {
        this.companyId = companyId;
        this.name = name;
        this.defaultStartLat = defaultStartLat;
        this.defaultStartLon = defaultStartLon;
    }

    // Returns the company identifier.
    public String getCompanyId() {
        return companyId;
    }

    // Returns the company display name.
    public String getName() {
        return name;
    }

    // Returns the default starting latitude.
    public double getDefaultStartLat() {
        return defaultStartLat;
    }

    // Returns the default starting longitude.
    public double getDefaultStartLon() {
        return defaultStartLon;
    }

    // Returns the display format used in selection lists.
    @Override
    public String toString() {
        return name + " (" + companyId + ")";
    }
}