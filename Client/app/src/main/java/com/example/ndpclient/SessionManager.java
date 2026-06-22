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
    private static final String KEY_AUTO_RESUME_DELIVERY = "auto_resume_delivery";

    private final SharedPreferences prefs;

    // Initializes local session storage.
    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // Saves whether courier delivery mode should auto-resume.
    public void setAutoResumeDelivery(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_RESUME_DELIVERY, enabled).apply();
    }

    // Returns whether courier delivery mode should auto-resume.
    public boolean isAutoResumeDelivery() {
        return prefs.getBoolean(KEY_AUTO_RESUME_DELIVERY, false);
    }

    // Saves the selected user role.
    public void saveRole(String role) {
        prefs.edit().putString(KEY_ROLE, role).apply();
    }

    // Returns the selected user role.
    public String getRole() {
        return prefs.getString(KEY_ROLE, null);
    }

    // Checks whether a role is selected.
    public boolean hasRole() {
        return getRole() != null;
    }

    // Saves the selected company.
    public void saveCompany(String companyId, String companyName) {
        prefs.edit()
                .putString(KEY_COMPANY_ID, companyId)
                .putString(KEY_COMPANY_NAME, companyName)
                .apply();
    }

    // Returns the selected company identifier.
    public String getCompanyId() {
        return prefs.getString(KEY_COMPANY_ID, null);
    }

    // Returns the selected company name.
    public String getCompanyName() {
        return prefs.getString(KEY_COMPANY_NAME, null);
    }

    // Checks whether a company is selected.
    public boolean hasCompany() {
        return getCompanyId() != null;
    }

    // Saves the selected courier.
    public void saveCourier(String courierId, String courierName) {
        prefs.edit()
                .putString(KEY_COURIER_ID, courierId)
                .putString(KEY_COURIER_NAME, courierName)
                .apply();
    }

    // Returns the selected courier identifier.
    public String getCourierId() {
        return prefs.getString(KEY_COURIER_ID, null);
    }

    // Returns the selected courier name.
    public String getCourierName() {
        return prefs.getString(KEY_COURIER_NAME, null);
    }

    // Checks whether a courier is selected.
    public boolean hasCourier() {
        return getCourierId() != null;
    }

    // Clears the selected courier from the session.
    public void clearCourier() {
        prefs.edit()
                .remove(KEY_COURIER_ID)
                .remove(KEY_COURIER_NAME)
                .apply();
    }

    // Clears all saved session data.
    public void clearSession() {
        prefs.edit().clear().apply();
    }

    // Builds the storage key for the last courier identifier of a company.
    private String keyLastCourierId(String companyId) {
        String c = (companyId == null) ? "no_company" : companyId.trim();
        return "last_courier_id::" + c;
    }

    // Builds the storage key for the last courier name of a company.
    private String keyLastCourierName(String companyId) {
        String c = (companyId == null) ? "no_company" : companyId.trim();
        return "last_courier_name::" + c;
    }

    // Saves the last selected courier for a company.
    public void saveLastCourierForCompany(String companyId, String courierId, String courierName) {
        prefs.edit()
                .putString(keyLastCourierId(companyId), courierId)
                .putString(keyLastCourierName(companyId), courierName)
                .apply();
    }

    // Returns the last selected courier identifier for a company.
    public String getLastCourierIdForCompany(String companyId) {
        return prefs.getString(keyLastCourierId(companyId), null);
    }

    // Returns the last selected courier name for a company.
    public String getLastCourierNameForCompany(String companyId) {
        return prefs.getString(keyLastCourierName(companyId), null);
    }
}