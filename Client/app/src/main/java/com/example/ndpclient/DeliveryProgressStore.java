package com.example.ndpclient;

import android.content.Context;
import android.content.SharedPreferences;

public class DeliveryProgressStore {

    private static final String PREFS_NAME = "delivery_progress_prefs";

    private final SharedPreferences prefs;

    public DeliveryProgressStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private String keyForCompany(String companyId) {
        String c = (companyId == null || companyId.trim().isEmpty()) ? "no_company" : companyId.trim();
        return c + "::current_seq";
    }

    public int getCurrentSeq(String companyId) {
        return prefs.getInt(keyForCompany(companyId), 0); // 0 = start ברירת מחדל
    }

    public void setCurrentSeq(String companyId, int seq) {
        prefs.edit().putInt(keyForCompany(companyId), seq).apply();
    }

    public void resetCompany(String companyId) {
        prefs.edit().remove(keyForCompany(companyId)).apply();
    }
}