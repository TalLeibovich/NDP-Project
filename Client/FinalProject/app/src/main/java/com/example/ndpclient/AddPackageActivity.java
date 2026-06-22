package com.example.ndpclient;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class AddPackageActivity extends AppCompatActivity {

    private Button btnBackAddPackage;
    private Button btnGeocode;
    private Button btnSavePackage;
    private EditText etPackageId;
    private EditText etAddress;
    private EditText etWeight;
    private EditText etVolume;
    private EditText etProfit;
    private EditText etDeadline;
    private TextView tvLocationStatus;
    private TextView tvLatLon;

    private SessionManager sessionManager;

    private Double currentLat = null;
    private Double currentLon = null;

    // Initializes the add-package screen and connects all UI actions.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_package);

        sessionManager = new SessionManager(this);

        btnBackAddPackage = findViewById(R.id.btnBackAddPackage);
        btnGeocode = findViewById(R.id.btnGeocode);
        btnSavePackage = findViewById(R.id.btnSavePackage);

        etPackageId = findViewById(R.id.etPackageId);
        etAddress = findViewById(R.id.etAddress);
        etWeight = findViewById(R.id.etWeight);
        etVolume = findViewById(R.id.etVolume);
        etProfit = findViewById(R.id.etProfit);
        etDeadline = findViewById(R.id.etDeadline);

        tvLocationStatus = findViewById(R.id.tvLocationStatus);
        tvLatLon = findViewById(R.id.tvLatLon);

        btnBackAddPackage.setOnClickListener(v -> {
            Intent intent = new Intent(AddPackageActivity.this, PackagesListActivity.class);
            startActivity(intent);
            finish();
        });

        btnGeocode.setOnClickListener(v -> geocodeAddress());
        btnSavePackage.setOnClickListener(v -> savePackage());
    }

    // Converts the entered address into latitude and longitude using the geocoding API.
    private void geocodeAddress() {
        String address = etAddress.getText().toString().trim();
        if (address.isEmpty()) {
            tvLocationStatus.setText(R.string.enter_address_first);
            return;
        }

        tvLocationStatus.setText(R.string.geocoding);

        try {
            JSONObject body = new JSONObject();
            body.put("address", address);

            ApiClient.post("/geo/geocode", body.toString(), new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(String json) {
                    runOnUiThread(() -> {
                        try {
                            JSONObject obj = new JSONObject(json);
                            currentLat = obj.optDouble("lat");
                            currentLon = obj.optDouble("lon");

                            tvLocationStatus.setText(R.string.location_found);
                            tvLatLon.setText("lat: " + currentLat + " | lon: " + currentLon);

                        } catch (Exception e) {
                            tvLocationStatus.setText(R.string.failed_to_parse_geocode);
                        }
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> tvLocationStatus.setText(R.string.geocode_failed));
                }
            });

        } catch (Exception e) {
            tvLocationStatus.setText(R.string.geocode_failed);
        }
    }

    // Validates the package form and sends the package data to the server.
    private void savePackage() {
        String companyId = sessionManager.getCompanyId();
        if (companyId == null) {
            tvLocationStatus.setText(R.string.no_company_selected);
            return;
        }

        String packageId = etPackageId.getText().toString().trim();
        String weightText = etWeight.getText().toString().trim();
        String volumeText = etVolume.getText().toString().trim();
        String profitText = etProfit.getText().toString().trim();
        String deadline = etDeadline.getText().toString().trim();

        if (packageId.isEmpty() || weightText.isEmpty() || volumeText.isEmpty() ||
                profitText.isEmpty() || deadline.isEmpty()) {
            tvLocationStatus.setText(R.string.fill_all_package_fields);
            return;
        }

        if (currentLat == null || currentLon == null) {
            tvLocationStatus.setText(R.string.geocode_before_save);
            return;
        }

        try {
            double weight = Double.parseDouble(weightText);
            double volume = Double.parseDouble(volumeText);
            double profit = Double.parseDouble(profitText);

            JSONObject body = new JSONObject();
            body.put("id", packageId);
            body.put("company_id", companyId);
            body.put("lat", currentLat);
            body.put("lon", currentLon);
            body.put("weight", weight);
            body.put("volume", volume);
            body.put("profit", profit);
            body.put("deadline", deadline);
            body.put("delivered", 0);

            tvLocationStatus.setText(R.string.saving_package);

            ApiClient.post("/packages", body.toString(), new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(String json) {
                    runOnUiThread(() -> {
                        Intent intent = new Intent(AddPackageActivity.this, PackagesListActivity.class);
                        startActivity(intent);
                        finish();
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> tvLocationStatus.setText(R.string.failed_to_create_package));
                }
            });

        } catch (Exception e) {
            tvLocationStatus.setText(R.string.invalid_numeric_values);
        }
    }
}