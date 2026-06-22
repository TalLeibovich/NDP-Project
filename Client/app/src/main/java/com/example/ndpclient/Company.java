package com.example.ndpclient;

public class Company {
    private String companyId;
    private String name;
    private double defaultStartLat;
    private double defaultStartLon;

    public Company(String companyId, String name, double defaultStartLat, double defaultStartLon) {
        this.companyId = companyId;
        this.name = name;
        this.defaultStartLat = defaultStartLat;
        this.defaultStartLon = defaultStartLon;
    }

    public String getCompanyId() {
        return companyId;
    }

    public String getName() {
        return name;
    }

    public double getDefaultStartLat() {
        return defaultStartLat;
    }

    public double getDefaultStartLon() {
        return defaultStartLon;
    }

    @Override
    public String toString() {
        return name + " (" + companyId + ")";
    }
}