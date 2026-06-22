package com.example.ndpclient;

import android.content.Context;
import android.content.SharedPreferences;

public class OptimizeResultStore {

    private static final String PREFS_NAME = "optimize_result_store_v2";
    private final SharedPreferences prefs;

    // Initializes local storage for optimization results.
    public OptimizeResultStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // Normalizes identifiers before using them in storage keys.
    private String safe(String s) {
        if (s == null) return "null";
        s = s.trim();
        return s.isEmpty() ? "empty" : s;
    }

    // Builds the storage key for a draft optimization result.
    private String keyDraft(String companyId, String courierId) {
        return "draft::" + safe(companyId) + "::" + safe(courierId);
    }

    // Builds the storage key for an assigned optimization result.
    private String keyAssigned(String companyId, String courierId) {
        return "assigned::" + safe(companyId) + "::" + safe(courierId);
    }

    // Saves a draft optimization result for a courier.
    public void saveDraft(String companyId, String courierId, String json) {
        prefs.edit().putString(keyDraft(companyId, courierId), json).apply();
    }

    // Returns the saved draft optimization result.
    public String getDraft(String companyId, String courierId) {
        return prefs.getString(keyDraft(companyId, courierId), null);
    }

    // Checks whether a draft optimization result exists.
    public boolean hasDraft(String companyId, String courierId) {
        String v = getDraft(companyId, courierId);
        return v != null && !v.trim().isEmpty();
    }

    // Clears the saved draft optimization result.
    public void clearDraft(String companyId, String courierId) {
        prefs.edit().remove(keyDraft(companyId, courierId)).apply();
    }

    // Saves an assigned optimization result for a courier.
    public void saveAssigned(String companyId, String courierId, String json) {
        prefs.edit().putString(keyAssigned(companyId, courierId), json).apply();
    }

    // Returns the assigned optimization result.
    public String getAssigned(String companyId, String courierId) {
        return prefs.getString(keyAssigned(companyId, courierId), null);
    }

    // Checks whether an assigned optimization result exists.
    public boolean hasAssigned(String companyId, String courierId) {
        String v = getAssigned(companyId, courierId);
        return v != null && !v.trim().isEmpty();
    }

    // Clears the assigned optimization result.
    public void clearAssigned(String companyId, String courierId) {
        prefs.edit().remove(keyAssigned(companyId, courierId)).apply();
    }

    // Checks whether any optimization result exists for a courier.
    public boolean hasResult(String companyId, String courierId) {
        return hasAssigned(companyId, courierId) || hasDraft(companyId, courierId);
    }

    // Returns the assigned result when available, otherwise returns the draft.
    public String getBestResult(String companyId, String courierId) {
        String a = getAssigned(companyId, courierId);
        if (a != null && !a.trim().isEmpty()) return a;
        return getDraft(companyId, courierId);
    }

    // Clears all optimization results for a courier.
    public void clearAllForCourier(String companyId, String courierId) {
        prefs.edit()
                .remove(keyDraft(companyId, courierId))
                .remove(keyAssigned(companyId, courierId))
                .apply();
    }
}