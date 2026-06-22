package com.example.ndpclient;

import android.content.Context;
import android.content.SharedPreferences;

public class DefaultsStore {

    private static final String PREFS_NAME = "optimization_defaults_store";

    private final SharedPreferences prefs;

    // Initializes the local storage used for optimization defaults.
    public DefaultsStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // Normalizes saved values and applies a fallback when needed.
    private String clean(String s, String fallback) {
        if (s == null) return fallback;
        String t = s.trim();
        return t.isEmpty() ? fallback : t;
    }

    // Builds a unique key for company-level defaults.
    private String companyKey(String companyId, String suffix) {
        String c = clean(companyId, "no_company");
        return "company::" + c + "::" + suffix;
    }

    // Builds a unique key for courier-level defaults within a company.
    private String courierKey(String companyId, String courierId, String suffix) {
        String c = clean(companyId, "no_company");
        String k = clean(courierId, "no_courier");
        return "courier::" + c + "::" + k + "::" + suffix;
    }

    // Saves the default start address for a company.
    public void saveCompanyDefaultAddress(String companyId, String address) {
        prefs.edit()
                .putString(companyKey(companyId, "default_address"), clean(address, ""))
                .apply();
    }

    // Returns the default start address saved for a company.
    public String getCompanyDefaultAddress(String companyId) {
        return prefs.getString(companyKey(companyId, "default_address"), "");
    }

    // Saves courier optimization defaults while preserving the existing end address.
    public void saveCourierDefaults(
            String companyId,
            String courierId,
            String maxDistanceKm,
            String maxWeight,
            String maxVolume,
            String maxStops
    ) {
        saveCourierDefaults(companyId, courierId, maxDistanceKm, maxWeight, maxVolume, maxStops,
                getDefaultEndAddress(companyId, courierId));
    }

    // Saves all courier optimization defaults, including the optional end address.
    public void saveCourierDefaults(
            String companyId,
            String courierId,
            String maxDistanceKm,
            String maxWeight,
            String maxVolume,
            String maxStops,
            String defaultEndAddress
    ) {
        prefs.edit()
                .putString(courierKey(companyId, courierId, "max_distance_km"), clean(maxDistanceKm, "50"))
                .putString(courierKey(companyId, courierId, "max_weight"), clean(maxWeight, "100"))
                .putString(courierKey(companyId, courierId, "max_volume"), clean(maxVolume, "100"))
                .putString(courierKey(companyId, courierId, "max_stops"), clean(maxStops, "20"))
                .putString(courierKey(companyId, courierId, "default_end_address"), clean(defaultEndAddress, ""))
                .apply();
    }

    // Returns the courier default maximum route distance.
    public String getMaxDistanceKm(String companyId, String courierId) {
        return prefs.getString(courierKey(companyId, courierId, "max_distance_km"), "50");
    }

    // Returns the courier default maximum package weight.
    public String getMaxWeight(String companyId, String courierId) {
        return prefs.getString(courierKey(companyId, courierId, "max_weight"), "100");
    }

    // Returns the courier default maximum package volume.
    public String getMaxVolume(String companyId, String courierId) {
        return prefs.getString(courierKey(companyId, courierId, "max_volume"), "100");
    }

    // Returns the courier default maximum number of stops.
    public String getMaxStops(String companyId, String courierId) {
        return prefs.getString(courierKey(companyId, courierId, "max_stops"), "20");
    }

    // Returns the optional default end address for a courier.
    public String getDefaultEndAddress(String companyId, String courierId) {
        return prefs.getString(courierKey(companyId, courierId, "default_end_address"), "");
    }

    // Saves the optional default end address for a courier.
    public void saveDefaultEndAddress(String companyId, String courierId, String address) {
        prefs.edit()
                .putString(courierKey(companyId, courierId, "default_end_address"), clean(address, ""))
                .apply();
    }
}