package com.example.ndpclient;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class CompanyDefaultAddressActivity extends AppCompatActivity {

    private Button btnBackCompanyDefaultAddress;
    private Button btnSaveCompanyDefaultAddress;
    private TextView tvCompanyDefaultAddressInfo;
    private EditText etCompanyDefaultAddress;

    private SessionManager sessionManager;
    private DefaultsStore defaultsStore;

    // Initializes the company default address screen and loads the saved address.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_company_default_address);

        sessionManager = new SessionManager(this);
        defaultsStore = new DefaultsStore(this);

        btnBackCompanyDefaultAddress = findViewById(R.id.btnBackCompanyDefaultAddress);
        btnSaveCompanyDefaultAddress = findViewById(R.id.btnSaveCompanyDefaultAddress);
        tvCompanyDefaultAddressInfo = findViewById(R.id.tvCompanyDefaultAddressInfo);
        etCompanyDefaultAddress = findViewById(R.id.etCompanyDefaultAddress);

        String companyName = sessionManager.getCompanyName();
        String companyId = sessionManager.getCompanyId();

        tvCompanyDefaultAddressInfo.setText(
                "Company: " + (companyName == null ? "-" : companyName) +
                        "\nCompany ID: " + (companyId == null ? "-" : companyId)
        );

        String savedAddress = defaultsStore.getCompanyDefaultAddress(companyId);
        if (savedAddress == null || savedAddress.trim().isEmpty()) {
            etCompanyDefaultAddress.setText("Bar Ilan Street 29, Raanana, Israel");
        } else {
            etCompanyDefaultAddress.setText(savedAddress);
        }

        btnBackCompanyDefaultAddress.setOnClickListener(v -> finish());

        btnSaveCompanyDefaultAddress.setOnClickListener(v -> {
            String address = etCompanyDefaultAddress.getText().toString().trim();

            if (address.isEmpty()) {
                Toast.makeText(this, "Please enter default address.", Toast.LENGTH_SHORT).show();
                return;
            }

            defaultsStore.saveCompanyDefaultAddress(companyId, address);
            Toast.makeText(this, "Company default address saved.", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}