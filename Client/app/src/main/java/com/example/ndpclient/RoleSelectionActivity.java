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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);

        sessionManager = new SessionManager(this);

        // Guard: חייב חברה
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
            // חזרה לבחירת חברה (לא exit)
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

    @Override
    public void onBackPressed() {
        startActivity(new Intent(this, CompanySelectionActivity.class));
        finish();
    }
}