package com.example.ndpclient;

public class RouteStop {
    private int seq;
    private String type;
    private double lat;
    private double lon;
    private String packageId;
    private String address;
    private double legKm;
    private double cumKm;
    private double cumWeight;
    private double cumVolume;
    private double cumProfit;

    // Creates a route stop without address data.
    public RouteStop(int seq, String type, double lat, double lon, String packageId,
                     double legKm, double cumKm, double cumWeight, double cumVolume, double cumProfit) {
        this(seq, type, lat, lon, packageId, null, legKm, cumKm, cumWeight, cumVolume, cumProfit);
    }

    // Creates a route stop with full route and address data.
    public RouteStop(int seq, String type, double lat, double lon, String packageId, String address,
                     double legKm, double cumKm, double cumWeight, double cumVolume, double cumProfit) {
        this.seq = seq;
        this.type = type;
        this.lat = lat;
        this.lon = lon;
        this.packageId = packageId;
        this.address = normalize(address);
        this.legKm = legKm;
        this.cumKm = cumKm;
        this.cumWeight = cumWeight;
        this.cumVolume = cumVolume;
        this.cumProfit = cumProfit;
    }

    // Normalizes optional address values.
    private String normalize(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        return s;
    }

    // Returns the route sequence number.
    public int getSeq() { return seq; }

    // Returns the route stop type.
    public String getType() { return type; }

    // Returns the stop latitude.
    public double getLat() { return lat; }

    // Returns the stop longitude.
    public double getLon() { return lon; }

    // Returns the related package identifier.
    public String getPackageId() { return packageId; }

    // Returns the stop address when available.
    public String getAddress() { return address; }

    // Returns the distance from the previous stop.
    public double getLegKm() { return legKm; }

    // Returns the cumulative route distance.
    public double getCumKm() { return cumKm; }

    // Returns the cumulative route weight.
    public double getCumWeight() { return cumWeight; }

    // Returns the cumulative route volume.
    public double getCumVolume() { return cumVolume; }

    // Returns the cumulative route profit.
    public double getCumProfit() { return cumProfit; }

    // Checks whether this stop represents a package delivery.
    public boolean isDelivery() {
        return "delivery".equalsIgnoreCase(type) && packageId != null && !packageId.trim().isEmpty();
    }
}