package com.example.ndpclient;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.List;

public class DeliveryModeActivity extends AppCompatActivity {

    private Button btnBackDeliveryMode;
    private Button btnNextStop;
    private Button btnCompleteRoute;

    private TextView tvDeliveryHeader;
    private ListView listViewDeliveryStops;

    private SessionManager sessionManager;
    private OptimizeResultStore optimizeResultStore;
    private RouteStopStatusStore statusStore;
    private DeliveryProgressStore progressStore;

    private String runId = null;
    private boolean completeInFlight = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delivery_mode);

        sessionManager = new SessionManager(this);
        optimizeResultStore = new OptimizeResultStore(this);
        statusStore = new RouteStopStatusStore(this);
        progressStore = new DeliveryProgressStore(this);

        btnBackDeliveryMode = findViewById(R.id.btnBackDeliveryMode);
        btnNextStop = findViewById(R.id.btnNextStop);
        btnCompleteRoute = findViewById(R.id.btnCompleteRoute);

        tvDeliveryHeader = findViewById(R.id.tvDeliveryHeader);
        listViewDeliveryStops = findViewById(R.id.listViewDeliveryStops);

        btnBackDeliveryMode.setOnClickListener(v -> finish());

        btnNextStop.setOnClickListener(v -> {
            Toast.makeText(this, "Next stop (UI only)", Toast.LENGTH_SHORT).show();
            // אם יש לך לוגיקה קיימת ל-Next Stop תשאיר אותה כאן
        });

        btnCompleteRoute.setOnClickListener(v -> showCompleteDialog());

        loadAssignedRouteOrExit();
    }

    private void loadAssignedRouteOrExit() {
        String companyId = sessionManager.getCompanyId();
        String courierId = sessionManager.getCourierId();

        String assigned = optimizeResultStore.getAssigned(companyId, courierId);
        if (assigned == null || assigned.trim().isEmpty()) {
            Toast.makeText(this, "No assigned route. Please run optimization and Assign first.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, CourierHomeActivity.class));
            finish();
            return;
        }

        try {
            JSONObject obj = new JSONObject(assigned);
            runId = obj.optString("run_id", null);

            List<RouteStop> stops = RouteOrderParser.parseOrderedStops(obj, "DeliveryMode");

            tvDeliveryHeader.setText("Delivery Mode");

            RouteStopsAdapter adapter = new RouteStopsAdapter(this, stops, companyId);
            listViewDeliveryStops.setAdapter(adapter);

            boolean hasRunId = runId != null && !runId.trim().isEmpty() && !"null".equalsIgnoreCase(runId);
            btnCompleteRoute.setEnabled(hasRunId);

        } catch (Exception e) {
            Toast.makeText(this, "Failed to load assigned route", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, CourierHomeActivity.class));
            finish();
        }
    }

    private void showCompleteDialog() {
        if (completeInFlight) return;

        boolean hasRunId = runId != null && !runId.trim().isEmpty() && !"null".equalsIgnoreCase(runId);
        if (!hasRunId) {
            btnCompleteRoute.setEnabled(false);
            Toast.makeText(this, "No active run to complete.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Complete Route")
                .setMessage("This will complete the run and release locked packages. Continue?")
                .setPositiveButton("Yes", (d, which) -> completeRoute())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void completeRoute() {
        completeInFlight = true;
        btnCompleteRoute.setEnabled(false);
        btnCompleteRoute.setText("Completing...");

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
                    Toast.makeText(DeliveryModeActivity.this, "Route completed. Packages released.", Toast.LENGTH_LONG).show();

                    Intent i = new Intent(DeliveryModeActivity.this, CourierHomeActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    finish();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    completeInFlight = false;
                    btnCompleteRoute.setEnabled(true);
                    btnCompleteRoute.setText(getString(R.string.complete_route_release));
                    Toast.makeText(DeliveryModeActivity.this,
                            "Complete failed: " + (e == null ? "" : e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}