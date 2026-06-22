package com.example.ndpclient;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class EditPackageActivity extends AppCompatActivity {

    private Button btnBackEditPackage;
    private Button btnGeocodeEdit;
    private Button btnSaveChanges;

    private EditText etEditPackageId;
    private EditText etEditAddress;
    private EditText etEditWeight;
    private EditText etEditVolume;
    private EditText etEditProfit;
    private EditText etEditDeadline;

    private TextView tvEditLocationStatus;
    private TextView tvEditLatLon;

    private SessionManager sessionManager;

    private String packageId;
    private Double currentLat = null;
    private Double currentLon = null;

    // Initializes the edit-package screen and loads the selected package data.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_package);

        sessionManager = new SessionManager(this);

        btnBackEditPackage = findViewById(R.id.btnBackEditPackage);
        btnGeocodeEdit = findViewById(R.id.btnGeocodeEdit);
        btnSaveChanges = findViewById(R.id.btnSaveChanges);

        etEditPackageId = findViewById(R.id.etEditPackageId);
        etEditAddress = findViewById(R.id.etEditAddress);
        etEditWeight = findViewById(R.id.etEditWeight);
        etEditVolume = findViewById(R.id.etEditVolume);
        etEditProfit = findViewById(R.id.etEditProfit);
        etEditDeadline = findViewById(R.id.etEditDeadline);

        tvEditLocationStatus = findViewById(R.id.tvEditLocationStatus);
        tvEditLatLon = findViewById(R.id.tvEditLatLon);

        Intent intent = getIntent();
        packageId = intent.getStringExtra("package_id");

        String weight = intent.getStringExtra("weight");
        String volume = intent.getStringExtra("volume");
        String profit = intent.getStringExtra("profit");
        String deadline = intent.getStringExtra("deadline");
        String lat = intent.getStringExtra("lat");
        String lon = intent.getStringExtra("lon");

        etEditPackageId.setText(packageId);
        etEditPackageId.setEnabled(false);

        etEditWeight.setText(weight);
        etEditVolume.setText(volume);
        etEditProfit.setText(profit);
        etEditDeadline.setText(deadline);

        if (lat != null && lon != null) {
            try {
                currentLat = Double.parseDouble(lat);
                currentLon = Double.parseDouble(lon);
                tvEditLatLon.setText("lat: " + currentLat + " | lon: " + currentLon);
            } catch (Exception ignored) {
            }
        }

        btnBackEditPackage.setOnClickListener(v -> {
            Intent backIntent = new Intent(EditPackageActivity.this, PackagesListActivity.class);
            startActivity(backIntent);
            finish();
        });

        btnGeocodeEdit.setOnClickListener(v -> geocodeAddress());
        btnSaveChanges.setOnClickListener(v -> saveChanges());
    }

    // Converts the entered address into latitude and longitude using the geocoding API.
    private void geocodeAddress() {
        String address = etEditAddress.getText().toString().trim();
        if (address.isEmpty()) {
            tvEditLocationStatus.setText(R.string.enter_address_first);
            return;
        }

        tvEditLocationStatus.setText(R.string.geocoding);

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

                            tvEditLocationStatus.setText(R.string.location_found);
                            tvEditLatLon.setText("lat: " + currentLat + " | lon: " + currentLon);

                        } catch (Exception e) {
                            tvEditLocationStatus.setText(R.string.failed_to_parse_geocode);
                        }
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> tvEditLocationStatus.setText(R.string.geocode_failed));
                }
            });

        } catch (Exception e) {
            tvEditLocationStatus.setText(R.string.geocode_failed);
        }
    }

    // Validates the edited package form and sends the updated package data to the server.
    private void saveChanges() {
        String companyId = sessionManager.getCompanyId();
        if (companyId == null) {
            tvEditLocationStatus.setText(R.string.no_company_selected);
            return;
        }

        String weightText = etEditWeight.getText().toString().trim();
        String volumeText = etEditVolume.getText().toString().trim();
        String profitText = etEditProfit.getText().toString().trim();
        String deadline = etEditDeadline.getText().toString().trim();

        if (weightText.isEmpty() || volumeText.isEmpty() || profitText.isEmpty() || deadline.isEmpty()) {
            tvEditLocationStatus.setText(R.string.fill_all_package_fields);
            return;
        }

        if (currentLat == null || currentLon == null) {
            tvEditLocationStatus.setText(R.string.geocode_before_save);
            return;
        }

        try {
            double weight = Double.parseDouble(weightText);
            double volume = Double.parseDouble(volumeText);
            double profit = Double.parseDouble(profitText);

            JSONObject body = new JSONObject();
            body.put("company_id", companyId);
            body.put("lat", currentLat);
            body.put("lon", currentLon);
            body.put("weight", weight);
            body.put("volume", volume);
            body.put("profit", profit);
            body.put("deadline", deadline);

            tvEditLocationStatus.setText(R.string.saving_package);

            ApiClient.put("/packages/" + packageId, body.toString(), new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(String json) {
                    runOnUiThread(() -> {
                        Intent backIntent = new Intent(EditPackageActivity.this, PackagesListActivity.class);
                        startActivity(backIntent);
                        finish();
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> tvEditLocationStatus.setText(R.string.failed_to_update_package));
                }
            });

        } catch (Exception e) {
            tvEditLocationStatus.setText(R.string.invalid_numeric_values);
        }
    }
}