package com.example.ndpclient;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class OptimizeConfigActivity extends AppCompatActivity {

    private Button btnBackOptimize;
    private Button btnRunOptimize;

    private TextView tvOptimizeCompany;
    private TextView tvOptimizeCourier;
    private TextView tvOptimizeStatus;

    private EditText etStartAddress;
    private EditText etEndAddress;
    private EditText etMaxDistance;
    private EditText etMaxWeight;
    private EditText etMaxVolume;
    private EditText etMaxStops;
    private EditText etServiceDate;

    private SessionManager sessionManager;
    private OptimizeResultStore resultStore;
    private DefaultsStore defaultsStore;

    private String pendingCompanyId;
    private String pendingCourierId;
    private String pendingStartAddress;
    private String pendingEndAddress;
    private String pendingMaxDistanceText;
    private String pendingMaxWeightText;
    private String pendingMaxVolumeText;
    private String pendingMaxStopsText;
    private String pendingServiceDate;

    private double resolvedStartLat;
    private double resolvedStartLon;
    private String resolvedStartAddress;

    // Initializes the optimization configuration screen and loads saved defaults.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_optimize_config);

        sessionManager = new SessionManager(this);
        resultStore = new OptimizeResultStore(this);
        defaultsStore = new DefaultsStore(this);

        btnBackOptimize = findViewById(R.id.btnBackOptimize);
        btnRunOptimize = findViewById(R.id.btnRunOptimize);

        tvOptimizeCompany = findViewById(R.id.tvOptimizeCompany);
        tvOptimizeCourier = findViewById(R.id.tvOptimizeCourier);
        tvOptimizeStatus = findViewById(R.id.tvOptimizeStatus);

        etStartAddress = findViewById(R.id.etStartAddress);
        etEndAddress = findViewById(R.id.etEndAddress);
        etMaxDistance = findViewById(R.id.etMaxDistance);
        etMaxWeight = findViewById(R.id.etMaxWeight);
        etMaxVolume = findViewById(R.id.etMaxVolume);
        etMaxStops = findViewById(R.id.etMaxStops);
        etServiceDate = findViewById(R.id.etServiceDate);

        String companyName = sessionManager.getCompanyName();
        String courierName = sessionManager.getCourierName();

        tvOptimizeCompany.setText("Company: " + (companyName == null ? "-" : companyName));
        tvOptimizeCourier.setText("Courier: " + (courierName == null ? "-" : courierName));

        loadDefaultsIntoFields();

        btnBackOptimize.setOnClickListener(v -> {
            if ("courier".equalsIgnoreCase(sessionManager.getRole())) {
                startActivity(new Intent(this, CourierHomeActivity.class));
            } else {
                startActivity(new Intent(this, MainActivity.class));
            }
            finish();
        });

        btnRunOptimize.setOnClickListener(v -> beforeRunOptimization());
    }

    // Loads company and courier defaults into the optimization form.
    private void loadDefaultsIntoFields() {
        String companyId = sessionManager.getCompanyId();
        String courierId = sessionManager.getCourierId();

        String companyAddress = defaultsStore.getCompanyDefaultAddress(companyId);
        if (companyAddress != null && !companyAddress.trim().isEmpty()) {
            etStartAddress.setText(companyAddress);
        } else {
            etStartAddress.setText("Bar Ilan Street 29, Raanana, Israel");
        }

        if (companyId != null && courierId != null) {
            etMaxDistance.setText(defaultsStore.getMaxDistanceKm(companyId, courierId));
            etMaxWeight.setText(defaultsStore.getMaxWeight(companyId, courierId));
            etMaxVolume.setText(defaultsStore.getMaxVolume(companyId, courierId));
            etMaxStops.setText(defaultsStore.getMaxStops(companyId, courierId));

            String defaultEndAddress = defaultsStore.getDefaultEndAddress(companyId, courierId);
            etEndAddress.setText(defaultEndAddress == null ? "" : defaultEndAddress);
        } else {
            etMaxDistance.setText("50");
            etMaxWeight.setText("100");
            etMaxVolume.setText("100");
            etMaxStops.setText("20");
            etEndAddress.setText("");
        }

        etServiceDate.setText("2026-01-18");
    }

    // Checks for an existing optimization before running a new one.
    private void beforeRunOptimization() {
        String companyId = sessionManager.getCompanyId();
        String courierId = sessionManager.getCourierId();

        if (companyId == null || courierId == null) {
            tvOptimizeStatus.setText(R.string.optimize_missing_session);
            return;
        }

        if (!resultStore.hasResult(companyId, courierId)) {
            runOptimization();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Optimization already exists")
                .setMessage("This courier already has an optimization (draft or assigned).\n\nDo you want to open it, or delete & release it?")
                .setPositiveButton("Go to it", (d, w) -> {
                    startActivity(new Intent(this, OptimizeResultActivity.class));
                })
                .setNegativeButton("Delete (Release)", (d, w) -> {
                    confirmDeleteAndRelease(companyId, courierId);
                })
                .setNeutralButton("Cancel", null)
                .show();
    }

    // Confirms deletion and release of an existing optimization result.
    private void confirmDeleteAndRelease(String companyId, String courierId) {
        boolean hasAssigned = resultStore.hasAssigned(companyId, courierId);

        String msg = hasAssigned
                ? "This route is already ASSIGNED.\nReleasing it will unlock packages and remove the assigned route.\n\nContinue?"
                : "This will delete the draft and release locked packages.\n\nContinue?";

        new AlertDialog.Builder(this)
                .setTitle("Confirm release")
                .setMessage(msg)
                .setPositiveButton("Yes", (d, w) -> deleteAndRelease(companyId, courierId))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Clears the local optimization result and releases the server run when available.
    private void deleteAndRelease(String companyId, String courierId) {
        String json = resultStore.getBestResult(companyId, courierId);
        String runId = null;

        try {
            if (json != null) {
                JSONObject obj = new JSONObject(json);
                runId = obj.optString("run_id", null);
                if (runId != null && runId.trim().isEmpty()) runId = null;
                if ("null".equalsIgnoreCase(String.valueOf(runId))) runId = null;
            }
        } catch (Exception ignored) { }

        resultStore.clearAllForCourier(companyId, courierId);

        new RouteStopStatusStore(this).clearCompany(companyId);
        new DeliveryProgressStore(this).resetCompany(companyId);

        if (runId == null) {
            tvOptimizeStatus.setText("Released locally (no run_id). You can optimize again.");
            return;
        }

        final String finalRunId = runId;
        tvOptimizeStatus.setText("Releasing run...");
        btnRunOptimize.setEnabled(false);

        ApiClient.post("/runs/" + finalRunId + "/complete", "{}", new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String json) {
                runOnUiThread(() -> {
                    btnRunOptimize.setEnabled(true);
                    tvOptimizeStatus.setText("Run released. Packages are available.");
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    btnRunOptimize.setEnabled(true);
                    String msg = (e == null || e.getMessage() == null) ? "Release failed" : e.getMessage();
                    tvOptimizeStatus.setText("Release failed: " + msg);
                });
            }
        });
    }

    // Validates the optimization form and starts the geocoding flow.
    private void runOptimization() {
        pendingCompanyId = sessionManager.getCompanyId();
        pendingCourierId = sessionManager.getCourierId();

        if (pendingCompanyId == null || pendingCourierId == null) {
            tvOptimizeStatus.setText(R.string.optimize_missing_session);
            return;
        }

        pendingStartAddress = etStartAddress.getText().toString().trim();
        pendingEndAddress = etEndAddress.getText().toString().trim();

        pendingMaxDistanceText = etMaxDistance.getText().toString().trim();
        pendingMaxWeightText = etMaxWeight.getText().toString().trim();
        pendingMaxVolumeText = etMaxVolume.getText().toString().trim();
        pendingMaxStopsText = etMaxStops.getText().toString().trim();
        pendingServiceDate = etServiceDate.getText().toString().trim();

        if (pendingStartAddress.isEmpty()
                || pendingMaxDistanceText.isEmpty()
                || pendingMaxWeightText.isEmpty()
                || pendingMaxVolumeText.isEmpty()
                || pendingMaxStopsText.isEmpty()
                || pendingServiceDate.isEmpty()) {
            tvOptimizeStatus.setText(R.string.optimize_fill_all_fields);
            return;
        }

        try {
            Double.parseDouble(pendingMaxDistanceText);
            Double.parseDouble(pendingMaxWeightText);
            Double.parseDouble(pendingMaxVolumeText);
            Integer.parseInt(pendingMaxStopsText);
        } catch (Exception e) {
            tvOptimizeStatus.setText(R.string.invalid_numeric_values);
            return;
        }

        tvOptimizeStatus.setText("Resolving start address...");
        btnRunOptimize.setEnabled(false);

        geocodeStartAddress();
    }

    // Resolves the start address before sending the optimization request.
    private void geocodeStartAddress() {
        try {
            JSONObject geoBody = new JSONObject();
            geoBody.put("address", pendingStartAddress);

            ApiClient.post("/geo/geocode", geoBody.toString(), new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(String geoJson) {
                    runOnUiThread(() -> {
                        try {
                            JSONObject geoObj = new JSONObject(geoJson);

                            resolvedStartLat = geoObj.optDouble("lat", Double.NaN);
                            resolvedStartLon = geoObj.optDouble("lon", Double.NaN);

                            if (Double.isNaN(resolvedStartLat) || Double.isNaN(resolvedStartLon)) {
                                btnRunOptimize.setEnabled(true);
                                tvOptimizeStatus.setText("Start address was not resolved.");
                                return;
                            }

                            resolvedStartAddress = readAddressFromGeoResult(geoObj, pendingStartAddress);

                            if (pendingEndAddress == null || pendingEndAddress.trim().isEmpty()) {
                                runOptimizationWithCoordinates(false, 0.0, 0.0, null);
                            } else {
                                tvOptimizeStatus.setText("Resolving end address...");
                                geocodeEndAddress();
                            }

                        } catch (Exception e) {
                            btnRunOptimize.setEnabled(true);
                            tvOptimizeStatus.setText("Failed to parse start geocode result.");
                        }
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> {
                        btnRunOptimize.setEnabled(true);
                        String msg = (e == null || e.getMessage() == null) ? "" : e.getMessage();
                        tvOptimizeStatus.setText("Start geocode failed: " + msg);
                    });
                }
            });

        } catch (Exception e) {
            btnRunOptimize.setEnabled(true);
            tvOptimizeStatus.setText("Failed to build start geocode request.");
        }
    }

    // Resolves the optional end address before running optimization.
    private void geocodeEndAddress() {
        try {
            JSONObject geoBody = new JSONObject();
            geoBody.put("address", pendingEndAddress);

            ApiClient.post("/geo/geocode", geoBody.toString(), new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(String geoJson) {
                    runOnUiThread(() -> {
                        try {
                            JSONObject geoObj = new JSONObject(geoJson);

                            double endLat = geoObj.optDouble("lat", Double.NaN);
                            double endLon = geoObj.optDouble("lon", Double.NaN);

                            if (Double.isNaN(endLat) || Double.isNaN(endLon)) {
                                btnRunOptimize.setEnabled(true);
                                tvOptimizeStatus.setText("End address was not resolved.");
                                return;
                            }

                            String resolvedEndAddress = readAddressFromGeoResult(geoObj, pendingEndAddress);

                            runOptimizationWithCoordinates(true, endLat, endLon, resolvedEndAddress);

                        } catch (Exception e) {
                            btnRunOptimize.setEnabled(true);
                            tvOptimizeStatus.setText("Failed to parse end geocode result.");
                        }
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> {
                        btnRunOptimize.setEnabled(true);
                        String msg = (e == null || e.getMessage() == null) ? "" : e.getMessage();
                        tvOptimizeStatus.setText("End geocode failed: " + msg);
                    });
                }
            });

        } catch (Exception e) {
            btnRunOptimize.setEnabled(true);
            tvOptimizeStatus.setText("Failed to build end geocode request.");
        }
    }

    // Sends the final optimization request using resolved coordinates.
    private void runOptimizationWithCoordinates(
            boolean hasEnd,
            double endLat,
            double endLon,
            String endAddress
    ) {
        try {
            double maxDistance = Double.parseDouble(pendingMaxDistanceText);
            double maxWeight = Double.parseDouble(pendingMaxWeightText);
            double maxVolume = Double.parseDouble(pendingMaxVolumeText);
            int maxStops = Integer.parseInt(pendingMaxStopsText);

            JSONObject start = new JSONObject();
            start.put("lat", resolvedStartLat);
            start.put("lon", resolvedStartLon);
            start.put("address", resolvedStartAddress);
            start.put("formatted_address", resolvedStartAddress);

            JSONObject constraints = new JSONObject();
            constraints.put("max_distance_km", maxDistance);
            constraints.put("max_weight", maxWeight);
            constraints.put("max_volume", maxVolume);
            constraints.put("max_stops", maxStops);

            JSONObject body = new JSONObject();
            body.put("company_id", pendingCompanyId);
            body.put("courier_id", pendingCourierId);
            body.put("start", start);

            if (hasEnd) {
                JSONObject end = new JSONObject();
                end.put("lat", endLat);
                end.put("lon", endLon);
                end.put("address", endAddress);
                end.put("formatted_address", endAddress);
                body.put("end", end);
            }

            body.put("constraints", constraints);
            body.put("service_date", pendingServiceDate);

            tvOptimizeStatus.setText(R.string.optimize_running);

            ApiClient.post("/optimize", body.toString(), new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(String json) {
                    runOnUiThread(() -> {
                        btnRunOptimize.setEnabled(true);

                        if (isEmptyOptimization(json)) {
                            tvOptimizeStatus.setText(R.string.optimize_no_selection);
                            return;
                        }

                        resultStore.saveDraft(pendingCompanyId, pendingCourierId, json);

                        startActivity(new Intent(OptimizeConfigActivity.this, OptimizeResultActivity.class));
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> {
                        btnRunOptimize.setEnabled(true);
                        String msg = (e == null || e.getMessage() == null || e.getMessage().trim().isEmpty())
                                ? getString(R.string.optimize_failed)
                                : e.getMessage();
                        tvOptimizeStatus.setText(msg);
                    });
                }
            });

        } catch (Exception e) {
            btnRunOptimize.setEnabled(true);
            tvOptimizeStatus.setText(R.string.invalid_numeric_values);
        }
    }

    // Extracts a readable address from the geocoding response.
    private String readAddressFromGeoResult(JSONObject geoObj, String fallback) {
        String formatted = geoObj.optString("formatted_address", null);
        if (isValidString(formatted)) return formatted;

        String address = geoObj.optString("address", null);
        if (isValidString(address)) return address;

        String input = geoObj.optString("input_address", null);
        if (isValidString(input)) return input;

        return fallback;
    }

    // Checks whether the optimizer returned no selected packages or no route.
    private boolean isEmptyOptimization(String json) {
        try {
            JSONObject obj = new JSONObject(json);

            if (obj.has("selected_package_ids")) {
                return obj.optJSONArray("selected_package_ids") == null
                        || obj.optJSONArray("selected_package_ids").length() == 0;
            }

            if (obj.has("route_stops")) {
                return obj.optJSONArray("route_stops") == null
                        || obj.optJSONArray("route_stops").length() <= 1;
            }

            return false;

        } catch (Exception e) {
            return false;
        }
    }

    // Validates that a string contains meaningful content.
    private boolean isValidString(String s) {
        return s != null && !s.trim().isEmpty() && !"null".equalsIgnoreCase(s.trim());
    }
}