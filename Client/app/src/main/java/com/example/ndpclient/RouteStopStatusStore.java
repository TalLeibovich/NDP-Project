package com.example.ndpclient;

import android.content.Context;
import android.content.SharedPreferences;

public class RouteStopStatusStore {

    public enum Status {
        PENDING,
        CURRENT,
        DELIVERED,
        FAILED
    }

    private static final String PREFS_NAME = "route_stop_status_prefs";
    private final SharedPreferences prefs;

    public RouteStopStatusStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private String keyFor(String companyId, String packageId) {
        String c = (companyId == null || companyId.trim().isEmpty()) ? "no_company" : companyId.trim();
        String p = (packageId == null || packageId.trim().isEmpty()) ? "unknown_pkg" : packageId.trim();
        return c + "::" + p;
    }

    public Status getStatus(String companyId, String packageId) {
        String key = keyFor(companyId, packageId);
        String val = prefs.getString(key, Status.PENDING.name());

        // תאימות לאחור אם נשמר "NOT_STARTED" מהגרסה הקודמת
        if ("NOT_STARTED".equals(val)) return Status.PENDING;

        try {
            return Status.valueOf(val);
        } catch (Exception e) {
            return Status.PENDING;
        }
    }

    public void setStatus(String companyId, String packageId, Status status) {
        String key = keyFor(companyId, packageId);
        prefs.edit().putString(key, status.name()).apply();
    }

    public void clearCompany(String companyId) {
        String c = (companyId == null || companyId.trim().isEmpty()) ? "no_company" : companyId.trim();
        String prefix = c + "::";

        java.util.Map<String, ?> all = prefs.getAll();
        SharedPreferences.Editor editor = prefs.edit();

        for (String key : all.keySet()) {
            if (key != null && key.startsWith(prefix)) {
                editor.remove(key);
            }
        }

        editor.apply();
    }
}