package com.example.ndpclient;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    private static final String PREFS_NAME = "ndp_prefs";
    private static final String KEY_ROLE = "role";
    private static final String KEY_COMPANY_ID = "company_id";
    private static final String KEY_COMPANY_NAME = "company_name";
    private static final String KEY_COURIER_ID = "courier_id";
    private static final String KEY_COURIER_NAME = "courier_name";

    private final SharedPreferences prefs;

    // --- Courier auto-resume (Stage 8 UX) ---
    private static final String KEY_AUTO_RESUME_DELIVERY = "auto_resume_delivery";

    public void setAutoResumeDelivery(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_RESUME_DELIVERY, enabled).apply();
    }

    public boolean isAutoResumeDelivery() {
        return prefs.getBoolean(KEY_AUTO_RESUME_DELIVERY, false);
    }

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveRole(String role) {
        prefs.edit().putString(KEY_ROLE, role).apply();
    }

    public String getRole() {
        return prefs.getString(KEY_ROLE, null);
    }

    public boolean hasRole() {
        return getRole() != null;
    }

    public void saveCompany(String companyId, String companyName) {
        prefs.edit()
                .putString(KEY_COMPANY_ID, companyId)
                .putString(KEY_COMPANY_NAME, companyName)
                .apply();
    }

    public String getCompanyId() {
        return prefs.getString(KEY_COMPANY_ID, null);
    }

    public String getCompanyName() {
        return prefs.getString(KEY_COMPANY_NAME, null);
    }

    public boolean hasCompany() {
        return getCompanyId() != null;
    }

    public void saveCourier(String courierId, String courierName) {
        prefs.edit()
                .putString(KEY_COURIER_ID, courierId)
                .putString(KEY_COURIER_NAME, courierName)
                .apply();
    }

    public String getCourierId() {
        return prefs.getString(KEY_COURIER_ID, null);
    }

    public String getCourierName() {
        return prefs.getString(KEY_COURIER_NAME, null);
    }

    public boolean hasCourier() {
        return getCourierId() != null;
    }

    public void clearCourier() {
        prefs.edit()
                .remove(KEY_COURIER_ID)
                .remove(KEY_COURIER_NAME)
                .apply();
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }

    // --- Last Courier per Company (Stage 8 UX) ---
    private String keyLastCourierId(String companyId) {
        String c = (companyId == null) ? "no_company" : companyId.trim();
        return "last_courier_id::" + c;
    }

    private String keyLastCourierName(String companyId) {
        String c = (companyId == null) ? "no_company" : companyId.trim();
        return "last_courier_name::" + c;
    }

    public void saveLastCourierForCompany(String companyId, String courierId, String courierName) {
        prefs.edit()
                .putString(keyLastCourierId(companyId), courierId)
                .putString(keyLastCourierName(companyId), courierName)
                .apply();
    }

    public String getLastCourierIdForCompany(String companyId) {
        return prefs.getString(keyLastCourierId(companyId), null);
    }

    public String getLastCourierNameForCompany(String companyId) {
        return prefs.getString(keyLastCourierName(companyId), null);
    }
}