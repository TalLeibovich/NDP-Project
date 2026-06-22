package com.example.ndpclient;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SPLASH";

    private ProgressBar progressSplash;
    private TextView tvSplashStatus;
    private Button btnRetry;

    private SessionManager sessionManager;
    private OptimizeResultStore optimizeResultStore;

    // Initializes the splash screen and starts the server availability check.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        sessionManager = new SessionManager(this);
        optimizeResultStore = new OptimizeResultStore(this);

        progressSplash = findViewById(R.id.progressSplash);
        tvSplashStatus = findViewById(R.id.tvSplashStatus);
        btnRetry = findViewById(R.id.btnRetry);

        btnRetry.setOnClickListener(v -> checkServer());

        checkServer();
    }

    // Checks whether the backend server is available before opening the app flow.
    private void checkServer() {
        progressSplash.setVisibility(View.VISIBLE);
        btnRetry.setVisibility(View.GONE);
        tvSplashStatus.setText("Checking server...");

        ApiClient.get("/", new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String raw) {
                runOnUiThread(() -> {
                    Log.d("SPLASH_RAW", "Response: " + raw);

                    JSONObject obj = tryParseJson(raw);
                    if (obj == null) {
                        obj = tryParseJson(extractFirstJsonObject(raw));
                    }

                    if (obj == null) {
                        showError("Failed to parse server response");
                        return;
                    }

                    String status = obj.optString("status", "");
                    if (!"ok".equalsIgnoreCase(status)) {
                        showError("Server response invalid");
                        return;
                    }

                    Intent next = resolveNextScreen();
                    next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(next);
                    finish();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Server check failed", e);
                    showError("Server unavailable. Please try again.");
                });
            }
        });
    }

    // Resolves the next screen according to the saved session state.
    private Intent resolveNextScreen() {
        if (!sessionManager.hasCompany()) {
            return new Intent(this, CompanySelectionActivity.class);
        }

        if (!sessionManager.hasRole()) {
            return new Intent(this, RoleSelectionActivity.class);
        }

        String role = sessionManager.getRole();

        if ("courier".equalsIgnoreCase(role)) {
            if (!sessionManager.hasCourier()) {
                return new Intent(this, LoginActivity.class);
            }
            return new Intent(this, CourierHomeActivity.class);
        }

        return new Intent(this, MainActivity.class);
    }

    // Displays a server or parsing error on the splash screen.
    private void showError(String msg) {
        progressSplash.setVisibility(View.GONE);
        btnRetry.setVisibility(View.VISIBLE);
        tvSplashStatus.setText(msg);
    }

    // Attempts to parse a string as a JSON object.
    private JSONObject tryParseJson(String s) {
        if (s == null) return null;
        try {
            return new JSONObject(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    // Extracts the first JSON object from a noisy response string.
    private String extractFirstJsonObject(String s) {
        if (s == null) return null;
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) return s.substring(start, end + 1);
        return null;
    }
}