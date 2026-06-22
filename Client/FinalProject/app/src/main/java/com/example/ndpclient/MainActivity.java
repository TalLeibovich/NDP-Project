package com.example.ndpclient;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private TextView tvMainStatus;
    private Button btnManagePackages;
    private Button btnRunOptimization;
    private Button btnContinueLastRoute;
    private Button btnChangeCourier;
    private Button btnCompanyDefaultAddress;
    private Button btnCourierDefaults;
    private Button btnReleaseActiveRun;
    private Button btnLogout;

    private SessionManager sessionManager;
    private OptimizeResultStore optimizeResultStore;

    // Initializes the manager dashboard and connects all main actions.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sessionManager = new SessionManager(this);
        optimizeResultStore = new OptimizeResultStore(this);

        if (!sessionManager.hasCompany()) {
            Intent i = new Intent(this, CompanySelectionActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
            return;
        }

        tvMainStatus = findViewById(R.id.tvMainStatus);
        btnManagePackages = findViewById(R.id.btnManagePackages);
        btnRunOptimization = findViewById(R.id.btnRunOptimization);
        btnContinueLastRoute = findViewById(R.id.btnContinueLastRoute);
        btnChangeCourier = findViewById(R.id.btnChangeCourier);
        btnCompanyDefaultAddress = findViewById(R.id.btnCompanyDefaultAddress);
        btnCourierDefaults = findViewById(R.id.btnCourierDefaults);
        btnReleaseActiveRun = findViewById(R.id.btnReleaseActiveRun);
        btnLogout = findViewById(R.id.btnLogout);

        btnContinueLastRoute.setText(getString(R.string.view_courier_route));

        btnManagePackages.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, PackagesListActivity.class))
        );

        btnRunOptimization.setOnClickListener(v -> {
            if (!sessionManager.hasCourier()) {
                startActivity(new Intent(MainActivity.this, CourierSelectionActivity.class));
                return;
            }
            startActivity(new Intent(MainActivity.this, OptimizeConfigActivity.class));
        });

        btnContinueLastRoute.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, OptimizeResultActivity.class))
        );

        btnChangeCourier.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, CourierSelectionActivity.class))
        );

        btnCompanyDefaultAddress.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, CompanyDefaultAddressActivity.class))
        );

        btnCourierDefaults.setOnClickListener(v -> {
            if (!sessionManager.hasCourier()) {
                Toast.makeText(MainActivity.this, "Please select courier first.", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(MainActivity.this, CourierSelectionActivity.class));
                return;
            }
            startActivity(new Intent(MainActivity.this, CourierDefaultsActivity.class));
        });

        btnReleaseActiveRun.setOnClickListener(v -> showActiveRunsDialog());

        btnLogout.setOnClickListener(v -> {
            sessionManager.clearSession();
            Intent i = new Intent(MainActivity.this, CompanySelectionActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });

        refreshUiAndActions();
    }

    // Refreshes the dashboard when returning from related screens.
    @Override
    protected void onResume() {
        super.onResume();
        refreshUiAndActions();
    }

    // Updates session details, button states, and route visibility.
    private void refreshUiAndActions() {
        if (!sessionManager.hasCourier()) {
            String companyId = sessionManager.getCompanyId();
            String lastCourierId = sessionManager.getLastCourierIdForCompany(companyId);
            String lastCourierName = sessionManager.getLastCourierNameForCompany(companyId);

            if (lastCourierId != null && lastCourierName != null) {
                sessionManager.saveCourier(lastCourierId, lastCourierName);
                Toast.makeText(this, "Courier selected automatically: " + lastCourierName, Toast.LENGTH_SHORT).show();
            }
        }

        String companyName = sessionManager.getCompanyName();
        String companyId = sessionManager.getCompanyId();
        String courierName = sessionManager.getCourierName();
        String courierId = sessionManager.getCourierId();

        String text =
                "Company: " + (companyName == null ? "-" : companyName) +
                        "\nCompany ID: " + (companyId == null ? "-" : companyId) +
                        "\nCourier: " + (courierName == null ? "-" : courierName) +
                        "\nCourier ID: " + (courierId == null ? "-" : courierId);

        tvMainStatus.setText(text);

        boolean hasCompany = sessionManager.hasCompany();
        boolean hasCourier = sessionManager.hasCourier();

        btnRunOptimization.setEnabled(hasCourier);
        btnCompanyDefaultAddress.setEnabled(hasCompany);
        btnCourierDefaults.setEnabled(hasCompany && hasCourier);

        boolean hasLastRoute = optimizeResultStore.hasAssigned(companyId, courierId);
        if (hasLastRoute && hasCourier) {
            btnContinueLastRoute.setVisibility(View.VISIBLE);
            btnContinueLastRoute.setEnabled(true);
        } else {
            btnContinueLastRoute.setVisibility(View.GONE);
        }

        btnChangeCourier.setEnabled(true);
        btnReleaseActiveRun.setEnabled(hasCompany);
    }

    // Closes the app from the main dashboard.
    @Override
    public void onBackPressed() {
        finishAffinity();
    }

    private static class RunMeta {
        String assignedTo = "Unassigned";
        int lockedCount = 0;
        boolean mixed = false;
    }

    // Loads active runs for the selected company and opens the selection dialog.
    private void showActiveRunsDialog() {
        String companyId = sessionManager.getCompanyId();
        if (companyId == null || companyId.trim().isEmpty()) {
            Toast.makeText(this, "No company selected", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Checking active runs...", Toast.LENGTH_SHORT).show();

        try {
            String path = "/runs/active?company_id=" + URLEncoder.encode(companyId, "UTF-8");
            ApiClient.get(path, new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(String json) {
                    runOnUiThread(() -> {
                        try {
                            JSONObject obj = new JSONObject(json);
                            JSONArray arr = obj.optJSONArray("active_run_ids");

                            if (arr == null || arr.length() == 0) {
                                Toast.makeText(MainActivity.this, "No active runs for this company.", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            List<String> runIds = new ArrayList<>();
                            for (int i = 0; i < arr.length(); i++) {
                                String rid = arr.optString(i, null);
                                if (rid != null && !rid.trim().isEmpty()) runIds.add(rid.trim());
                            }

                            if (runIds.isEmpty()) {
                                Toast.makeText(MainActivity.this, "No active runs for this company.", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            fetchRunMetaAndShow(companyId, runIds);

                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "Failed to parse active runs.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "Failed to load active runs: " + (e == null ? "" : e.getMessage()),
                            Toast.LENGTH_LONG).show());
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Failed to build request.", Toast.LENGTH_SHORT).show();
        }
    }

    // Fetches package metadata for active runs and shows run ownership details.
    private void fetchRunMetaAndShow(String companyId, List<String> runIds) {
        try {
            String path = "/packages?company_id=" + URLEncoder.encode(companyId, "UTF-8") + "&delivered=0";
            ApiClient.get(path, new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(String json) {
                    runOnUiThread(() -> {
                        Map<String, RunMeta> metaMap = new HashMap<>();
                        for (String rid : runIds) metaMap.put(rid, new RunMeta());

                        try {
                            JSONArray arr = new JSONArray(json);
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject p = arr.getJSONObject(i);

                                String rid = p.optString("assigned_run_id", null);
                                if (rid == null || rid.trim().isEmpty()) continue;

                                RunMeta m = metaMap.get(rid);
                                if (m == null) continue;

                                m.lockedCount++;

                                String assignedTo = p.optString("assigned_to", null);
                                if (assignedTo == null || assignedTo.trim().isEmpty()) assignedTo = "Unassigned";

                                if ("Unassigned".equals(m.assignedTo)) {
                                    m.assignedTo = assignedTo;
                                } else if (!m.assignedTo.equals(assignedTo)) {
                                    m.mixed = true;
                                }
                            }
                        } catch (Exception ignored) {}

                        final String[] display = new String[runIds.size()];
                        final String[] whoArr = new String[runIds.size()];

                        for (int i = 0; i < runIds.size(); i++) {
                            String rid = runIds.get(i);
                            RunMeta m = metaMap.get(rid);

                            String who = (m == null) ? "Unassigned" : (m.mixed ? "Mixed" : m.assignedTo);
                            int count = (m == null) ? 0 : m.lockedCount;

                            whoArr[i] = who;

                            String shortId = rid.length() > 8 ? rid.substring(0, 8) + "..." : rid;
                            display[i] = shortId + "  | courier: " + who + "  | locked: " + count;
                        }

                        final int[] selectedIndex = new int[]{0};

                        AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Active Runs")
                                .setSingleChoiceItems(display, 0, (d, which) -> selectedIndex[0] = which)
                                .setNegativeButton("Cancel", null)
                                .setNeutralButton("View", (d, w) -> {
                                    int idx = selectedIndex[0];
                                    String who = whoArr[idx];

                                    if (who == null || who.trim().isEmpty() ||
                                            "Unassigned".equals(who) || "Mixed".equals(who)) {
                                        Toast.makeText(MainActivity.this,
                                                "Cannot view: run not assigned to a single courier.",
                                                Toast.LENGTH_LONG).show();
                                        return;
                                    }

                                    sessionManager.saveCourier(who, who);
                                    startActivity(new Intent(MainActivity.this, OptimizeResultActivity.class));
                                })
                                .setPositiveButton("Release", (d, w) -> {
                                    int idx = selectedIndex[0];
                                    String runId = runIds.get(idx);
                                    confirmReleaseRun(companyId, runId);
                                })
                                .create();

                        dialog.show();
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "Failed to load packages for run details: " + (e == null ? "" : e.getMessage()),
                            Toast.LENGTH_LONG).show());
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Failed to build request.", Toast.LENGTH_SHORT).show();
        }
    }

    // Confirms release before completing an active run.
    private void confirmReleaseRun(String companyId, String runId) {
        new AlertDialog.Builder(this)
                .setTitle("Release run?")
                .setMessage("This will complete the run and unlock any locked packages.\n\nRun ID:\n" + runId)
                .setPositiveButton("Release", (d, w) -> releaseRun(companyId, runId))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Completes a run on the server and refreshes local state.
    private void releaseRun(String companyId, String runId) {
        Toast.makeText(this, "Releasing run...", Toast.LENGTH_SHORT).show();
        ApiClient.post("/runs/" + runId + "/complete", "{}", new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String json) {
                runOnUiThread(() -> {
                    tryClearCurrentCourierRouteIfMatches(companyId, runId);
                    Toast.makeText(MainActivity.this, "Run released.", Toast.LENGTH_SHORT).show();
                    refreshUiAndActions();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Release failed: " + (e == null ? "" : e.getMessage()),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    // Clears the current courier route only when it matches the released run.
    private void tryClearCurrentCourierRouteIfMatches(String companyId, String runId) {
        String courierId = sessionManager.getCourierId();
        if (companyId == null || courierId == null) return;

        String assigned = optimizeResultStore.getAssigned(companyId, courierId);
        if (assigned == null) return;

        try {
            JSONObject obj = new JSONObject(assigned);
            String localRunId = obj.optString("run_id", null);
            if (localRunId != null && localRunId.equals(runId)) {
                optimizeResultStore.clearAllForCourier(companyId, courierId);
            }
        } catch (Exception ignored) {}
    }
}