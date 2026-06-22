package com.example.ndpclient;

public class PackageItem {
    private String id;
    private String companyId;
    private double lat;
    private double lon;
    private double weight;
    private double volume;
    private double profit;
    private String deadline;
    private int delivered;

    public PackageItem(String id, String companyId, double lat, double lon,
                       double weight, double volume, double profit,
                       String deadline, int delivered) {
        this.id = id;
        this.companyId = companyId;
        this.lat = lat;
        this.lon = lon;
        this.weight = weight;
        this.volume = volume;
        this.profit = profit;
        this.deadline = deadline;
        this.delivered = delivered;
    }

    public String getId() {
        return id;
    }

    public String getCompanyId() {
        return companyId;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public double getWeight() {
        return weight;
    }

    public double getVolume() {
        return volume;
    }

    public double getProfit() {
        return profit;
    }

    public String getDeadline() {
        return deadline;
    }

    public int getDelivered() {
        return delivered;
    }

    @Override
    public String toString() {
        return id +
                " | deadline: " + deadline +
                " | w: " + weight +
                " | v: " + volume +
                " | p: " + profit +
                " | (" + lat + ", " + lon + ")";
    }
}