package com.example.ndpclient;

import android.content.Context;
import android.content.SharedPreferences;

public class DeliveryProgressStore {

    private static final String PREFS_NAME = "delivery_progress_prefs";

    private final SharedPreferences prefs;

    // Initializes local storage for delivery progress.
    public DeliveryProgressStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // Builds the storage key for the current route position of a company.
    private String keyForCompany(String companyId) {
        String c = (companyId == null || companyId.trim().isEmpty()) ? "no_company" : companyId.trim();
        return c + "::current_seq";
    }

    // Returns the current delivery stop sequence, defaulting to the route start.
    public int getCurrentSeq(String companyId) {
        return prefs.getInt(keyForCompany(companyId), 0);
    }

    // Saves the current delivery stop sequence.
    public void setCurrentSeq(String companyId, int seq) {
        prefs.edit().putInt(keyForCompany(companyId), seq).apply();
    }

    // Clears the saved delivery progress for a company.
    public void resetCompany(String companyId) {
        prefs.edit().remove(keyForCompany(companyId)).apply();
    }
}