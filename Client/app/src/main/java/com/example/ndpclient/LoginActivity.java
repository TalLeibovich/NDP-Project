package com.example.ndpclient;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class LoginActivity extends AppCompatActivity {

    private Button btnBack;
    private TextView tvTitle;
    private TextView tvStatus;
    private EditText etUsername;
    private EditText etPassword;
    private Button btnLogin;
    private ListView listViewCouriers;

    private SessionManager sessionManager;

    private final List<Courier> couriers = new ArrayList<>();
    private final List<String> couriersDisplay = new ArrayList<>();
    private ArrayAdapter<String> courierAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        sessionManager = new SessionManager(this);

        if (!sessionManager.hasCompany()) {
            startActivity(new Intent(this, CompanySelectionActivity.class));
            finish();
            return;
        }

        btnBack = findViewById(R.id.btnBackLogin);
        tvTitle = findViewById(R.id.tvLoginTitle);
        tvStatus = findViewById(R.id.tvLoginStatus);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        listViewCouriers = findViewById(R.id.listViewCouriers);

        // Username fixed = company name
        etUsername.setText(sessionManager.getCompanyName());
        etUsername.setEnabled(false);

        courierAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, couriersDisplay);
        listViewCouriers.setAdapter(courierAdapter);

        btnBack.setOnClickListener(v -> {
            startActivity(new Intent(this, RoleSelectionActivity.class));
            finish();
        });

        String role = sessionManager.getRole();
        if ("manager".equalsIgnoreCase(role)) {
            tvTitle.setText("Manager Login");
            tvStatus.setText("Password = Company ID");
            listViewCouriers.setVisibility(android.view.View.GONE);
        } else {
            tvTitle.setText("Courier Login");
            tvStatus.setText("Password = Courier ID (choose from list)");
            listViewCouriers.setVisibility(android.view.View.VISIBLE);
            loadCouriersForCompany();
        }

        listViewCouriers.setOnItemClickListener((p, v, pos, id) -> {
            if (pos < 0 || pos >= couriers.size()) return;
            Courier c = couriers.get(pos);
            etPassword.setText(c.getCourierId());
        });

        btnLogin.setOnClickListener(v -> attemptLogin());
    }

    private void loadCouriersForCompany() {
        try {
            String companyId = sessionManager.getCompanyId();
            String path = "/couriers?company_id=" + URLEncoder.encode(companyId, "UTF-8");

            ApiClient.get(path, new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(String json) {
                    runOnUiThread(() -> parseCouriers(json));
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> tvStatus.setText(getString(R.string.failed_to_load_couriers)));
                }
            });
        } catch (Exception e) {
            tvStatus.setText(getString(R.string.failed_to_load_couriers));
        }
    }

    private void parseCouriers(String json) {
        couriers.clear();
        couriersDisplay.clear();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String courierId = o.optString("courier_id", "");
                String companyId = o.optString("company_id", "");
                String name = o.optString("name", "");
                Courier c = new Courier(courierId, companyId, name);
                couriers.add(c);
                couriersDisplay.add(name + " (" + courierId + ")");
            }
            courierAdapter.notifyDataSetChanged();
        } catch (Exception e) {
            tvStatus.setText(getString(R.string.failed_to_parse_couriers));
        }
    }

    private void attemptLogin() {
        String password = etPassword.getText().toString().trim();
        if (password.isEmpty()) {
            tvStatus.setText(getString(R.string.company_login_fill));
            return;
        }

        String role = sessionManager.getRole();
        if ("manager".equalsIgnoreCase(role)) {
            // verify company id
            if (!password.equals(sessionManager.getCompanyId())) {
                tvStatus.setText(getString(R.string.company_login_invalid));
                return;
            }
            // manager dashboard
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
            return;
        }

        // courier: verify courier id exists
        String foundName = null;
        for (Courier c : couriers) {
            if (password.equals(c.getCourierId())) {
                foundName = c.getName();
                break;
            }
        }
        if (foundName == null) {
            tvStatus.setText("Invalid courier ID for this company");
            return;
        }

        sessionManager.saveCourier(password, foundName);

        Intent i = new Intent(this, CourierHomeActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    @Override
    public void onBackPressed() {
        startActivity(new Intent(this, RoleSelectionActivity.class));
        finish();
    }
}