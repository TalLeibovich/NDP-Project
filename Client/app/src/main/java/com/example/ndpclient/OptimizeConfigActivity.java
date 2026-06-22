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

    private EditText etStartLat;
    private EditText etStartLon;
    private EditText etMaxDistance;
    private EditText etMaxWeight;
    private EditText etMaxVolume;
    private EditText etMaxStops;
    private EditText etServiceDate;

    private SessionManager sessionManager;
    private OptimizeResultStore resultStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_optimize_config);

        sessionManager = new SessionManager(this);
        resultStore = new OptimizeResultStore(this);

        btnBackOptimize = findViewById(R.id.btnBackOptimize);
        btnRunOptimize = findViewById(R.id.btnRunOptimize);

        tvOptimizeCompany = findViewById(R.id.tvOptimizeCompany);
        tvOptimizeCourier = findViewById(R.id.tvOptimizeCourier);
        tvOptimizeStatus = findViewById(R.id.tvOptimizeStatus);

        etStartLat = findViewById(R.id.etStartLat);
        etStartLon = findViewById(R.id.etStartLon);
        etMaxDistance = findViewById(R.id.etMaxDistance);
        etMaxWeight = findViewById(R.id.etMaxWeight);
        etMaxVolume = findViewById(R.id.etMaxVolume);
        etMaxStops = findViewById(R.id.etMaxStops);
        etServiceDate = findViewById(R.id.etServiceDate);

        String companyName = sessionManager.getCompanyName();
        String courierName = sessionManager.getCourierName();

        tvOptimizeCompany.setText("Company: " + (companyName == null ? "-" : companyName));
        tvOptimizeCourier.setText("Courier: " + (courierName == null ? "-" : courierName));

        // defaults
        etStartLat.setText("32.0853");
        etStartLon.setText("34.7818");
        etMaxDistance.setText("50");
        etMaxWeight.setText("100");
        etMaxVolume.setText("100");
        etMaxStops.setText("20");
        etServiceDate.setText("2026-01-18");

        btnBackOptimize.setOnClickListener(v -> {
            // Back to correct home by role
            if ("courier".equalsIgnoreCase(sessionManager.getRole())) {
                startActivity(new Intent(this, CourierHomeActivity.class));
            } else {
                startActivity(new Intent(this, MainActivity.class));
            }
            finish();
        });

        btnRunOptimize.setOnClickListener(v -> beforeRunOptimization());
    }

    /** checks if this courier already has draft/assigned */
    private void beforeRunOptimization() {
        String companyId = sessionManager.getCompanyId();
        String courierId = sessionManager.getCourierId();

        if (companyId == null || courierId == null) {
            tvOptimizeStatus.setText(R.string.optimize_missing_session);
            return;
        }

        if (!resultStore.hasResult(companyId, courierId)) {
            runOptimization(); // no open optimization locally
            return;
        }

        // open optimization exists for this courier
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

    private void deleteAndRelease(String companyId, String courierId) {
        // Try extract run_id from best result (assigned preferred)
        String json = resultStore.getBestResult(companyId, courierId);
        String runId = null;

        try {
            if (json != null) {
                JSONObject obj = new JSONObject(json);
                runId = obj.optString("run_id", null);
                if (runId != null && runId.trim().isEmpty()) runId = null;
            }
        } catch (Exception ignored) { }

        // Clear local stored result first (so we don't re-open it)
        resultStore.clearAllForCourier(companyId, courierId);

        // Also reset local delivery UI pointers for this company
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

    private void runOptimization() {
        String companyId = sessionManager.getCompanyId();
        String courierId = sessionManager.getCourierId();

        if (companyId == null || courierId == null) {
            tvOptimizeStatus.setText(R.string.optimize_missing_session);
            return;
        }

        String startLatText = etStartLat.getText().toString().trim();
        String startLonText = etStartLon.getText().toString().trim();
        String maxDistanceText = etMaxDistance.getText().toString().trim();
        String maxWeightText = etMaxWeight.getText().toString().trim();
        String maxVolumeText = etMaxVolume.getText().toString().trim();
        String maxStopsText = etMaxStops.getText().toString().trim();
        String serviceDate = etServiceDate.getText().toString().trim();

        if (startLatText.isEmpty() || startLonText.isEmpty()
                || maxDistanceText.isEmpty() || maxWeightText.isEmpty()
                || maxVolumeText.isEmpty() || maxStopsText.isEmpty()
                || serviceDate.isEmpty()) {
            tvOptimizeStatus.setText(R.string.optimize_fill_all_fields);
            return;
        }

        try {
            double startLat = Double.parseDouble(startLatText);
            double startLon = Double.parseDouble(startLonText);
            double maxDistance = Double.parseDouble(maxDistanceText);
            double maxWeight = Double.parseDouble(maxWeightText);
            double maxVolume = Double.parseDouble(maxVolumeText);
            int maxStops = Integer.parseInt(maxStopsText);

            JSONObject start = new JSONObject();
            start.put("lat", startLat);
            start.put("lon", startLon);

            JSONObject constraints = new JSONObject();
            constraints.put("max_distance_km", maxDistance);
            constraints.put("max_weight", maxWeight);
            constraints.put("max_volume", maxVolume);
            constraints.put("max_stops", maxStops);

            JSONObject body = new JSONObject();
            body.put("company_id", companyId);
            body.put("courier_id", courierId);
            body.put("start", start);
            body.put("constraints", constraints);
            body.put("service_date", serviceDate);

            tvOptimizeStatus.setText(R.string.optimize_running);
            btnRunOptimize.setEnabled(false);

            ApiClient.post("/optimize", body.toString(), new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(String json) {
                    runOnUiThread(() -> {
                        btnRunOptimize.setEnabled(true);

                        try {
                            JSONObject response = new JSONObject(json);
                            RouteOrderParser.parseOrderedStops(response, "OptimizeConfig");
                        } catch (Exception ignored) {}

                        // Save as DRAFT for this company+courier
                        resultStore.saveDraft(companyId, courierId, json);

                        // Open Route Ready
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
            tvOptimizeStatus.setText(R.string.invalid_numeric_values);
        }
    }
}