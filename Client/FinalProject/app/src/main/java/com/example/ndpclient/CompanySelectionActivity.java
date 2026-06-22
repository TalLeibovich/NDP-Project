package com.example.ndpclient;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class CompanySelectionActivity extends AppCompatActivity {

    private TextView tvCompanyStatus;
    private Button btnLoadCompanies;
    private Button btnBackCompany;
    private ListView listViewCompanies;

    private SessionManager sessionManager;

    private final List<Company> companies = new ArrayList<>();
    private final List<String> display = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    // Initializes the company selection screen and loads the company list.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_company_select_only);

        sessionManager = new SessionManager(this);

        tvCompanyStatus = findViewById(R.id.tvCompanyStatus);
        btnLoadCompanies = findViewById(R.id.btnLoadCompanies);
        btnBackCompany = findViewById(R.id.btnBackCompany);
        listViewCompanies = findViewById(R.id.listViewCompanies);

        adapter = new ArrayAdapter<>(this, R.layout.item_list_row, display);
        listViewCompanies.setAdapter(adapter);

        btnBackCompany.setOnClickListener(v -> finishAffinity());

        btnLoadCompanies.setOnClickListener(v -> loadCompanies());

        listViewCompanies.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= companies.size()) return;

            Company c = companies.get(position);

            sessionManager.saveCompany(c.getCompanyId(), c.getName());

            sessionManager.clearCourier();
            sessionManager.saveRole(null);

            Intent i2 = new Intent(this, RoleSelectionActivity.class);
            startActivity(i2);
            finish();
        });

        loadCompanies();
    }

    // Loads the available companies from the API.
    private void loadCompanies() {
        tvCompanyStatus.setText(getString(R.string.loading_companies));
        btnLoadCompanies.setEnabled(false);

        ApiClient.get("/companies", new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String json) {
                runOnUiThread(() -> {
                    btnLoadCompanies.setEnabled(true);
                    parseCompanies(json);
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    btnLoadCompanies.setEnabled(true);
                    tvCompanyStatus.setText(getString(R.string.failed_to_load_companies));
                });
            }
        });
    }

    // Parses the company response and updates the selection list.
    private void parseCompanies(String json) {
        companies.clear();
        display.clear();

        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);

                String companyId = o.optString("company_id", "");
                String name = o.optString("name", "");
                double lat = o.optDouble("default_start_lat", 0.0);
                double lon = o.optDouble("default_start_lon", 0.0);

                companies.add(new Company(companyId, name, lat, lon));
                display.add(name + " (" + companyId + ")");
            }

            adapter.notifyDataSetChanged();

            if (companies.isEmpty()) tvCompanyStatus.setText(getString(R.string.no_companies_found));
            else tvCompanyStatus.setText(getString(R.string.select_company));

        } catch (Exception e) {
            tvCompanyStatus.setText(getString(R.string.failed_to_parse_companies));
        }
    }

    // Closes the app from the first selection screen.
    @Override
    public void onBackPressed() {
        finishAffinity();
    }
}