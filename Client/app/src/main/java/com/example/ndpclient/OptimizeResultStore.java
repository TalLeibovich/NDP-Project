package com.example.ndpclient;

import android.content.Context;
import android.content.SharedPreferences;

public class OptimizeResultStore {

    private static final String PREFS_NAME = "optimize_result_store_v2";
    private final SharedPreferences prefs;

    public OptimizeResultStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private String safe(String s) {
        if (s == null) return "null";
        s = s.trim();
        return s.isEmpty() ? "empty" : s;
    }

    private String keyDraft(String companyId, String courierId) {
        return "draft::" + safe(companyId) + "::" + safe(courierId);
    }

    private String keyAssigned(String companyId, String courierId) {
        return "assigned::" + safe(companyId) + "::" + safe(courierId);
    }

    // ---------- Draft ----------
    public void saveDraft(String companyId, String courierId, String json) {
        prefs.edit().putString(keyDraft(companyId, courierId), json).apply();
    }

    public String getDraft(String companyId, String courierId) {
        return prefs.getString(keyDraft(companyId, courierId), null);
    }

    public boolean hasDraft(String companyId, String courierId) {
        String v = getDraft(companyId, courierId);
        return v != null && !v.trim().isEmpty();
    }

    public void clearDraft(String companyId, String courierId) {
        prefs.edit().remove(keyDraft(companyId, courierId)).apply();
    }

    // ---------- Assigned ----------
    public void saveAssigned(String companyId, String courierId, String json) {
        prefs.edit().putString(keyAssigned(companyId, courierId), json).apply();
    }

    public String getAssigned(String companyId, String courierId) {
        return prefs.getString(keyAssigned(companyId, courierId), null);
    }

    public boolean hasAssigned(String companyId, String courierId) {
        String v = getAssigned(companyId, courierId);
        return v != null && !v.trim().isEmpty();
    }

    // ✅ NEW: to match your usage in CourierHomeActivity / others
    public void clearAssigned(String companyId, String courierId) {
        prefs.edit().remove(keyAssigned(companyId, courierId)).apply();
    }

    // ---------- Helpers ----------
    public boolean hasResult(String companyId, String courierId) {
        return hasAssigned(companyId, courierId) || hasDraft(companyId, courierId);
    }

    /** Prefer assigned, fallback to draft */
    public String getBestResult(String companyId, String courierId) {
        String a = getAssigned(companyId, courierId);
        if (a != null && !a.trim().isEmpty()) return a;
        return getDraft(companyId, courierId);
    }

    public void clearAllForCourier(String companyId, String courierId) {
        prefs.edit()
                .remove(keyDraft(companyId, courierId))
                .remove(keyAssigned(companyId, courierId))
                .apply();
    }
}