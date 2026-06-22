package com.example.ndpclient;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class OptimizeResultActivity extends AppCompatActivity {

    private Button btnBackOptimizeResult;
    private Button btnResetLocalStatuses;
    private Button btnStartDeliveryMode;
    private Button btnOpenRouteMap;
    private Button btnAssignToCourier;

    private TextView tvOptimizeSummary;
    private ListView listViewRouteStops;

    private OptimizeResultStore resultStore;
    private RouteStopStatusStore statusStore;
    private DeliveryProgressStore progressStore;
    private SessionManager sessionManager;

    private List<RouteStop> lastStops = new ArrayList<>();
    private String lastRunId = null;

    private boolean assignInFlight = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_optimize_result);

        sessionManager = new SessionManager(this);
        resultStore = new OptimizeResultStore(this);
        statusStore = new RouteStopStatusStore(this);
        progressStore = new DeliveryProgressStore(this);

        btnBackOptimizeResult = findViewById(R.id.btnBackOptimizeResult);
        btnResetLocalStatuses = findViewById(R.id.btnResetLocalStatuses);
        btnStartDeliveryMode = findViewById(R.id.btnStartDeliveryMode);
        btnOpenRouteMap = findViewById(R.id.btnOpenRouteMap);
        btnAssignToCourier = findViewById(R.id.btnAssignToCourier);

        tvOptimizeSummary = findViewById(R.id.tvOptimizeSummary);
        listViewRouteStops = findViewById(R.id.listViewRouteStops);

        btnBackOptimizeResult.setOnClickListener(v -> finish());

        btnResetLocalStatuses.setOnClickListener(v -> {
            String companyId = sessionManager.getCompanyId();
            statusStore.clearCompany(companyId);
            progressStore.resetCompany(companyId);
            loadAndRender();
        });

        // ❗ Delivery Mode ייפתח רק אם יש Assigned (לא Draft)
        btnStartDeliveryMode.setOnClickListener(v -> {
            String companyId = sessionManager.getCompanyId();
            String courierId = sessionManager.getCourierId();
            if (!resultStore.hasAssigned(companyId, courierId)) {
                Toast.makeText(this, "Please assign the route first (Assign to Courier).", Toast.LENGTH_LONG).show();
                return;
            }
            startActivity(new Intent(this, DeliveryModeActivity.class));
        });

        btnOpenRouteMap.setOnClickListener(v -> {
            String companyId = sessionManager.getCompanyId();
            String courierId = sessionManager.getCourierId();
            if (!resultStore.hasAssigned(companyId, courierId)) {
                Toast.makeText(this, "Please assign the route first (Assign to Courier).", Toast.LENGTH_LONG).show();
                return;
            }
            startActivity(new Intent(this, RouteMapActivity.class));
        });

        btnAssignToCourier.setOnClickListener(v -> activateRunLock());

        loadAndRender();
    }

    private void activateRunLock() {
        if (assignInFlight) return;

        String companyId = sessionManager.getCompanyId();
        String courierId = sessionManager.getCourierId();

        // אנחנו נועלים רק על Draft (תוצאה של Optimize)
        String draftJson = resultStore.getDraft(companyId, courierId);
        if (draftJson == null || draftJson.trim().isEmpty()) {
            Toast.makeText(this, "No optimization draft found. Run optimization first.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (lastRunId == null || lastRunId.trim().isEmpty() || "null".equalsIgnoreCase(lastRunId)) {
            Toast.makeText(this, "No run_id to assign", Toast.LENGTH_SHORT).show();
            return;
        }

        assignInFlight = true;
        btnAssignToCourier.setEnabled(false);
        btnAssignToCourier.setText("Assigning...");

        ApiClient.post("/runs/" + lastRunId + "/activate", "{}", new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String json) {
                runOnUiThread(() -> {
                    // ✅ אחרי assign: draft -> assigned
                    resultStore.saveAssigned(companyId, courierId, draftJson);
                    resultStore.clearDraft(companyId, courierId);

                    assignInFlight = false;
                    btnAssignToCourier.setText("Assigned");
                    Toast.makeText(OptimizeResultActivity.this, "Packages locked & route assigned to courier.", Toast.LENGTH_LONG).show();

                    loadAndRender();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    assignInFlight = false;
                    btnAssignToCourier.setEnabled(true);
                    btnAssignToCourier.setText(getString(R.string.assign_to_courier));

                    String msg = (e == null) ? "" : String.valueOf(e.getMessage());
                    if (msg.contains("HTTP 409")) {
                        Toast.makeText(OptimizeResultActivity.this,
                                "Assign failed: packages already locked by another courier/run",
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(OptimizeResultActivity.this,
                                "Assign failed: " + msg,
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void loadAndRender() {
        String companyId = sessionManager.getCompanyId();
        String courierId = sessionManager.getCourierId();

        // מציגים Draft אם קיים, אחרת Assigned (לצפייה)
        String json = resultStore.getDraft(companyId, courierId);
        boolean showingDraft = true;

        if (json == null || json.trim().isEmpty()) {
            json = resultStore.getAssigned(companyId, courierId);
            showingDraft = false;
        }

        if (json == null || json.trim().isEmpty()) {
            tvOptimizeSummary.setText(getString(R.string.no_optimize_result));
            listViewRouteStops.setAdapter(null);

            btnStartDeliveryMode.setEnabled(false);
            btnOpenRouteMap.setEnabled(false);
            btnResetLocalStatuses.setEnabled(false);
            btnAssignToCourier.setEnabled(false);
            return;
        }

        try {
            JSONObject obj = new JSONObject(json);

            lastRunId = obj.optString("run_id", null);
            JSONArray routeStopsArr = obj.optJSONArray("route_stops");
            JSONObject totals = obj.optJSONObject("totals");
            JSONArray selectedPackages = obj.optJSONArray("selected_package_ids");

            int selectedCount = (selectedPackages == null) ? 0 : selectedPackages.length();
            int totalStops = (routeStopsArr == null) ? 0 : routeStopsArr.length();

            double distance = 0.0, weight = 0.0, volume = 0.0, profit = 0.0;
            if (totals != null) {
                distance = totals.optDouble("total_distance_km", 0.0);
                weight = totals.optDouble("total_weight", 0.0);
                volume = totals.optDouble("total_volume", 0.0);
                profit = totals.optDouble("total_profit", 0.0);
            }

            String summary =
                    "Optimization completed" +
                            "\nRun ID: " + (lastRunId == null ? "-" : lastRunId) +
                            "\nSelected packages: " + selectedCount +
                            "\nTotal stops: " + totalStops +
                            "\nTotal distance: " + distance +
                            "\nTotal weight: " + weight +
                            "\nTotal volume: " + volume +
                            "\nTotal profit: " + profit +
                            (showingDraft ? "\nStatus: DRAFT (not assigned yet)" : "\nStatus: ASSIGNED");

            tvOptimizeSummary.setText(summary);

            lastStops = RouteOrderParser.parseOrderedStops(obj, "OptimizeResult");

            RouteStopsAdapter adapter = new RouteStopsAdapter(this, lastStops, companyId);
            listViewRouteStops.setAdapter(adapter);

            boolean hasRoute = !lastStops.isEmpty();
            boolean canAssign = showingDraft && hasRoute && lastRunId != null && !lastRunId.trim().isEmpty() && !"null".equalsIgnoreCase(lastRunId);

            // Delivery/Map רק אם ASSIGNED
            boolean hasAssigned = resultStore.hasAssigned(companyId, courierId);
            btnStartDeliveryMode.setEnabled(hasAssigned);
            btnOpenRouteMap.setEnabled(hasAssigned);

            btnResetLocalStatuses.setEnabled(companyId != null && !companyId.trim().isEmpty());
            btnAssignToCourier.setEnabled(canAssign);
            btnAssignToCourier.setText(getString(R.string.assign_to_courier));

        } catch (Exception e) {
            tvOptimizeSummary.setText(getString(R.string.failed_to_parse_optimize_result));
            listViewRouteStops.setAdapter(null);

            btnStartDeliveryMode.setEnabled(false);
            btnOpenRouteMap.setEnabled(false);
            btnResetLocalStatuses.setEnabled(false);
            btnAssignToCourier.setEnabled(false);
        }
    }
}