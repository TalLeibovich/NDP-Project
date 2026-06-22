package com.example.ndpclient;

public class RouteStop {
    private int seq;
    private String type;      // start / delivery / end (אם יהיה בעתיד)
    private double lat;
    private double lon;
    private String packageId; // null ב-start
    private double legKm;
    private double cumKm;
    private double cumWeight;
    private double cumVolume;
    private double cumProfit;

    public RouteStop(int seq, String type, double lat, double lon, String packageId,
                     double legKm, double cumKm, double cumWeight, double cumVolume, double cumProfit) {
        this.seq = seq;
        this.type = type;
        this.lat = lat;
        this.lon = lon;
        this.packageId = packageId;
        this.legKm = legKm;
        this.cumKm = cumKm;
        this.cumWeight = cumWeight;
        this.cumVolume = cumVolume;
        this.cumProfit = cumProfit;
    }

    public int getSeq() { return seq; }
    public String getType() { return type; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public String getPackageId() { return packageId; }
    public double getLegKm() { return legKm; }
    public double getCumKm() { return cumKm; }
    public double getCumWeight() { return cumWeight; }
    public double getCumVolume() { return cumVolume; }
    public double getCumProfit() { return cumProfit; }

    public boolean isDelivery() {
        return "delivery".equalsIgnoreCase(type) && packageId != null && !packageId.trim().isEmpty();
    }
}