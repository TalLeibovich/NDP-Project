package com.example.ndpclient;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class RoleSelectionActivity extends AppCompatActivity {

    private Button btnBack;
    private Button btnManager;
    private Button btnCourier;
    private TextView tvSelectedCompany;

    private SessionManager sessionManager;

    // Initializes the role selection screen for the selected company.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);

        sessionManager = new SessionManager(this);

        if (!sessionManager.hasCompany()) {
            startActivity(new Intent(this, CompanySelectionActivity.class));
            finish();
            return;
        }

        btnBack = findViewById(R.id.btnBackRole);
        btnManager = findViewById(R.id.btnManager);
        btnCourier = findViewById(R.id.btnCourier);
        tvSelectedCompany = findViewById(R.id.tvSelectedCompany);

        tvSelectedCompany.setText("Company: " + sessionManager.getCompanyName());

        btnBack.setOnClickListener(v -> {
            startActivity(new Intent(this, CompanySelectionActivity.class));
            finish();
        });

        btnManager.setOnClickListener(v -> {
            sessionManager.saveRole("manager");
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        btnCourier.setOnClickListener(v -> {
            sessionManager.saveRole("courier");
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    // Returns to the company selection screen.
    @Override
    public void onBackPressed() {
        startActivity(new Intent(this, CompanySelectionActivity.class));
        finish();
    }
}