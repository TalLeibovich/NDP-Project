package com.example.ndpclient;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class CompanyLoginActivity extends AppCompatActivity {

    private EditText etCompanyName;
    private EditText etCompanyId;
    private TextView tvLoginStatus;
    private TextView tvCompaniesPreview;
    private Button btnLoadCompaniesForLogin;
    private Button btnLoginCompany;

    private final List<CompanyItem> companies = new ArrayList<>();
    private SessionManager sessionManager;

    private static class CompanyItem {
        String companyId;
        String name;

        // Stores a company option used during login validation.
        CompanyItem(String companyId, String name) {
            this.companyId = companyId;
            this.name = name;
        }
    }

    // Initializes the company login screen and checks for an existing company session.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_company_login);

        sessionManager = new SessionManager(this);

        etCompanyName = findViewById(R.id.etCompanyName);
        etCompanyId = findViewById(R.id.etCompanyId);
        tvLoginStatus = findViewById(R.id.tvLoginStatus);
        tvCompaniesPreview = findViewById(R.id.tvCompaniesPreview);
        btnLoadCompaniesForLogin = findViewById(R.id.btnLoadCompaniesForLogin);
        btnLoginCompany = findViewById(R.id.btnLoginCompany);

        if (sessionManager.hasCompany()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        btnLoadCompaniesForLogin.setOnClickListener(v -> loadCompanies());
        btnLoginCompany.setOnClickListener(v -> attemptLogin());
    }

    // Loads the available companies from the API.
    private void loadCompanies() {
        tvLoginStatus.setText(getString(R.string.loading_companies));
        btnLoadCompaniesForLogin.setEnabled(false);

        ApiClient.get("/companies", new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String json) {
                runOnUiThread(() -> {
                    btnLoadCompaniesForLogin.setEnabled(true);
                    parseCompanies(json);
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    btnLoadCompaniesForLogin.setEnabled(true);
                    tvLoginStatus.setText(getString(R.string.failed_to_load_companies));
                });
            }
        });
    }

    // Parses the companies response and updates the preview list.
    private void parseCompanies(String json) {
        companies.clear();
        try {
            JSONArray arr = new JSONArray(json);
            StringBuilder preview = new StringBuilder();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject c = arr.getJSONObject(i);
                String id = c.optString("company_id", "");
                String name = c.optString("name", "");
                if (!id.isEmpty() && !name.isEmpty()) {
                    companies.add(new CompanyItem(id, name));
                    preview.append(name).append(" (").append(id).append(")\n");
                }
            }
            tvCompaniesPreview.setText(preview.toString().trim());
            tvLoginStatus.setText(getString(R.string.company_login_loaded));
        } catch (Exception e) {
            tvLoginStatus.setText(getString(R.string.failed_to_parse_companies));
        }
    }

    // Validates the entered company credentials and opens the main screen.
    private void attemptLogin() {
        String username = etCompanyName.getText().toString().trim();
        String password = etCompanyId.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            tvLoginStatus.setText(getString(R.string.company_login_fill));
            return;
        }

        if (companies.isEmpty()) {
            loadCompanies();
            tvLoginStatus.setText(getString(R.string.company_login_load_first));
            return;
        }

        CompanyItem match = null;
        for (CompanyItem c : companies) {
            if (c.name.equalsIgnoreCase(username) && c.companyId.equals(password)) {
                match = c;
                break;
            }
        }

        if (match == null) {
            tvLoginStatus.setText(getString(R.string.company_login_invalid));
            return;
        }

        sessionManager.saveCompany(match.companyId, match.name);

        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}