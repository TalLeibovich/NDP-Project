package com.example.ndpclient;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class CourierHomeActivity extends AppCompatActivity {

    private TextView tvCourierStatus;

    private Button btnCourierRunOptimize;
    private Button btnCourierStartOrContinue;
    private Button btnCourierOpenMap;
    private Button btnCourierOptimizationDefaults;
    private Button btnCourierCompleteRoute;
    private Button btnCourierLogout;

    private SessionManager sessionManager;
    private OptimizeResultStore optimizeResultStore;
    private RouteStopStatusStore statusStore;
    private DeliveryProgressStore progressStore;

    private boolean completeInFlight = false;

    // Initializes the courier home screen and connects all navigation actions.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_courier_home);

        sessionManager = new SessionManager(this);
        optimizeResultStore = new OptimizeResultStore(this);
        statusStore = new RouteStopStatusStore(this);
        progressStore = new DeliveryProgressStore(this);

        tvCourierStatus = findViewById(R.id.tvCourierStatus);

        btnCourierRunOptimize = findViewById(R.id.btnCourierRunOptimize);
        btnCourierStartOrContinue = findViewById(R.id.btnCourierStartOrContinue);
        btnCourierOpenMap = findViewById(R.id.btnCourierOpenMap);
        btnCourierOptimizationDefaults = findViewById(R.id.btnCourierOptimizationDefaults);
        btnCourierCompleteRoute = findViewById(R.id.btnCourierCompleteRoute);
        btnCourierLogout = findViewById(R.id.btnCourierLogout);

        btnCourierRunOptimize.setOnClickListener(v ->
                startActivity(new Intent(this, OptimizeConfigActivity.class)));

        btnCourierStartOrContinue.setOnClickListener(v -> {
            String companyId = sessionManager.getCompanyId();
            String courierId = sessionManager.getCourierId();
            if (!optimizeResultStore.hasAssigned(companyId, courierId)) {
                Toast.makeText(this, "No assigned route. Run optimization and press Assign to Courier.", Toast.LENGTH_LONG).show();
                return;
            }
            startActivity(new Intent(this, DeliveryModeActivity.class));
        });

        btnCourierOpenMap.setOnClickListener(v -> {
            String companyId = sessionManager.getCompanyId();
            String courierId = sessionManager.getCourierId();
            if (!optimizeResultStore.hasAssigned(companyId, courierId)) {
                Toast.makeText(this, "No assigned route to show on map.", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, RouteMapActivity.class));
        });

        btnCourierOptimizationDefaults.setOnClickListener(v ->
                startActivity(new Intent(CourierHomeActivity.this, CourierDefaultsActivity.class))
        );

        btnCourierCompleteRoute.setOnClickListener(v -> showCompleteDialog());

        btnCourierLogout.setOnClickListener(v -> {
            sessionManager.clearCourier();

            Intent intent = new Intent(CourierHomeActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    // Refreshes courier details and action states when returning to the screen.
    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    // Updates the visible courier session details and button availability.
    private void refreshUi() {
        String companyName = sessionManager.getCompanyName();
        String companyId = sessionManager.getCompanyId();
        String courierName = sessionManager.getCourierName();
        String courierId = sessionManager.getCourierId();

        tvCourierStatus.setText(
                "Company: " + (companyName == null ? "-" : companyName) +
                        "\nCompany ID: " + (companyId == null ? "-" : companyId) +
                        "\nCourier: " + (courierName == null ? "-" : courierName) +
                        "\nCourier ID: " + (courierId == null ? "-" : courierId)
        );

        boolean hasAssigned = optimizeResultStore.hasAssigned(companyId, courierId);
        boolean hasSession = sessionManager.hasCompany() && sessionManager.hasCourier();

        btnCourierStartOrContinue.setEnabled(hasAssigned);
        btnCourierOpenMap.setEnabled(hasAssigned);
        btnCourierCompleteRoute.setEnabled(hasAssignedRunId());
        btnCourierOptimizationDefaults.setEnabled(hasSession);
    }

    // Checks whether the assigned route contains a valid server run identifier.
    private boolean hasAssignedRunId() {
        String companyId = sessionManager.getCompanyId();
        String courierId = sessionManager.getCourierId();
        String assigned = optimizeResultStore.getAssigned(companyId, courierId);

        if (assigned == null || assigned.trim().isEmpty()) return false;

        try {
            JSONObject o = new JSONObject(assigned);
            String runId = o.optString("run_id", "");
            return runId != null && !runId.trim().isEmpty() && !"null".equalsIgnoreCase(runId);
        } catch (Exception e) {
            return false;
        }
    }

    // Extracts the run identifier from the assigned route data.
    private String getAssignedRunId() {
        String companyId = sessionManager.getCompanyId();
        String courierId = sessionManager.getCourierId();
        String assigned = optimizeResultStore.getAssigned(companyId, courierId);

        try {
            JSONObject o = new JSONObject(assigned);
            return o.optString("run_id", null);
        } catch (Exception e) {
            return null;
        }
    }

    // Shows a confirmation dialog before completing the active route.
    private void showCompleteDialog() {
        if (completeInFlight) return;

        String runId = getAssignedRunId();
        if (runId == null || runId.trim().isEmpty() || "null".equalsIgnoreCase(runId)) {
            Toast.makeText(this, "No assigned route to complete.", Toast.LENGTH_SHORT).show();
            btnCourierCompleteRoute.setEnabled(false);
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Complete Route")
                .setMessage("This will complete the run and release locked packages. Continue?")
                .setPositiveButton("Yes", (d, which) -> completeRoute(runId))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Completes the active route and clears local route state.
    private void completeRoute(String runId) {
        completeInFlight = true;
        btnCourierCompleteRoute.setEnabled(false);
        btnCourierCompleteRoute.setText("Completing...");

        ApiClient.post("/runs/" + runId + "/complete", "{}", new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String json) {
                runOnUiThread(() -> {
                    String companyId = sessionManager.getCompanyId();
                    String courierId = sessionManager.getCourierId();

                    statusStore.clearCompany(companyId);
                    progressStore.resetCompany(companyId);
                    optimizeResultStore.clearAssigned(companyId, courierId);
                    optimizeResultStore.clearDraft(companyId, courierId);

                    completeInFlight = false;
                    btnCourierCompleteRoute.setText(getString(R.string.complete_route_release));
                    Toast.makeText(CourierHomeActivity.this, "Route completed. Packages released.", Toast.LENGTH_LONG).show();

                    refreshUi();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    completeInFlight = false;
                    btnCourierCompleteRoute.setEnabled(true);
                    btnCourierCompleteRoute.setText(getString(R.string.complete_route_release));
                    Toast.makeText(CourierHomeActivity.this,
                            "Complete failed: " + (e == null ? "" : e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}