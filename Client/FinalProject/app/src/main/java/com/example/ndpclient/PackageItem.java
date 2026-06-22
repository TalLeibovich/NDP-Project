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
    private String address;
    private String formattedAddress;

    // Creates a package item without address metadata.
    public PackageItem(String id, String companyId, double lat, double lon,
                       double weight, double volume, double profit,
                       String deadline, int delivered) {
        this(id, companyId, lat, lon, weight, volume, profit, deadline, delivered, null, null);
    }

    // Creates a package item with full delivery and address data.
    public PackageItem(String id, String companyId, double lat, double lon,
                       double weight, double volume, double profit,
                       String deadline, int delivered,
                       String address, String formattedAddress) {
        this.id = id;
        this.companyId = companyId;
        this.lat = lat;
        this.lon = lon;
        this.weight = weight;
        this.volume = volume;
        this.profit = profit;
        this.deadline = deadline;
        this.delivered = delivered;
        this.address = clean(address);
        this.formattedAddress = clean(formattedAddress);
    }

    // Normalizes optional address values.
    private String clean(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty() || "null".equalsIgnoreCase(t)) return null;
        return t;
    }

    // Returns the package identifier.
    public String getId() { return id; }

    // Returns the company identifier.
    public String getCompanyId() { return companyId; }

    // Returns the package latitude.
    public double getLat() { return lat; }

    // Returns the package longitude.
    public double getLon() { return lon; }

    // Returns the package weight.
    public double getWeight() { return weight; }

    // Returns the package volume.
    public double getVolume() { return volume; }

    // Returns the package profit.
    public double getProfit() { return profit; }

    // Returns the package deadline.
    public String getDeadline() { return deadline; }

    // Returns the package delivery status flag.
    public int getDelivered() { return delivered; }

    // Returns the original address when available.
    public String getAddress() { return address; }

    // Returns the formatted address when available.
    public String getFormattedAddress() { return formattedAddress; }

    // Returns the best available address for display.
    public String getBestAddress() {
        if (formattedAddress != null && !formattedAddress.trim().isEmpty()) return formattedAddress;
        if (address != null && !address.trim().isEmpty()) return address;
        return null;
    }

    // Returns the package display text used in simple lists.
    @Override
    public String toString() {
        String bestAddress = getBestAddress();
        String addressPart = (bestAddress == null)
                ? "Address: not available"
                : "Address: " + bestAddress;

        return id +
                " | " + addressPart +
                " | deadline: " + deadline +
                " | weight: " + weight +
                " | volume: " + volume +
                " | profit: " + profit;
    }
}