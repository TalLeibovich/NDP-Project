package com.example.ndpclient;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class CourierDefaultsActivity extends AppCompatActivity {

    private Button btnBackCourierDefaults;
    private Button btnSaveCourierDefaults;

    private TextView tvCourierDefaultsInfo;

    private EditText etDefaultMaxDistance;
    private EditText etDefaultMaxWeight;
    private EditText etDefaultMaxVolume;
    private EditText etDefaultMaxStops;
    private EditText etDefaultEndAddress;

    private ScrollView scrollCourierDefaults;
    private LinearLayout layoutCourierDefaultValues;

    private LinearLayout cardPrivateCar;
    private LinearLayout cardMotorcycle;
    private LinearLayout cardTruck;
    private LinearLayout cardVan;

    private SessionManager sessionManager;
    private DefaultsStore defaultsStore;

    private String selectedVehicle = "";

    private static final String VEHICLE_PREFS = "vehicle_defaults";

    // Initializes the courier defaults screen and loads saved values.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_courier_defaults);

        sessionManager = new SessionManager(this);
        defaultsStore = new DefaultsStore(this);

        btnBackCourierDefaults = findViewById(R.id.btnBackCourierDefaults);
        btnSaveCourierDefaults = findViewById(R.id.btnSaveCourierDefaults);

        tvCourierDefaultsInfo = findViewById(R.id.tvCourierDefaultsInfo);

        etDefaultMaxDistance = findViewById(R.id.etDefaultMaxDistance);
        etDefaultMaxWeight = findViewById(R.id.etDefaultMaxWeight);
        etDefaultMaxVolume = findViewById(R.id.etDefaultMaxVolume);
        etDefaultMaxStops = findViewById(R.id.etDefaultMaxStops);
        etDefaultEndAddress = findViewById(R.id.etDefaultEndAddress);

        scrollCourierDefaults = findViewById(R.id.scrollCourierDefaults);
        layoutCourierDefaultValues = findViewById(R.id.layoutCourierDefaultValues);

        cardPrivateCar = findViewById(R.id.cardPrivateCar);
        cardMotorcycle = findViewById(R.id.cardMotorcycle);
        cardTruck = findViewById(R.id.cardTruck);
        cardVan = findViewById(R.id.cardVan);

        String companyId = sessionManager.getCompanyId();
        String companyName = sessionManager.getCompanyName();
        String courierId = sessionManager.getCourierId();
        String courierName = sessionManager.getCourierName();

        tvCourierDefaultsInfo.setText(
                "Company: " + (companyName == null ? "-" : companyName) +
                        "\nCompany ID: " + (companyId == null ? "-" : companyId) +
                        "\nCourier: " + (courierName == null ? "-" : courierName) +
                        "\nCourier ID: " + (courierId == null ? "-" : courierId)
        );

        if (companyId == null || courierId == null) {
            Toast.makeText(this, "Please select courier first.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        etDefaultMaxDistance.setText(defaultsStore.getMaxDistanceKm(companyId, courierId));
        etDefaultMaxWeight.setText(defaultsStore.getMaxWeight(companyId, courierId));
        etDefaultMaxVolume.setText(defaultsStore.getMaxVolume(companyId, courierId));
        etDefaultMaxStops.setText(defaultsStore.getMaxStops(companyId, courierId));
        etDefaultEndAddress.setText(defaultsStore.getDefaultEndAddress(companyId, courierId));

        setupVehicleSelection();

        btnBackCourierDefaults.setOnClickListener(v -> finish());

        btnSaveCourierDefaults.setOnClickListener(v -> saveDefaults());
    }

    // Connects vehicle cards to their default weight and volume values.
    private void setupVehicleSelection() {
        loadSavedVehicleSelection();

        cardPrivateCar.setOnClickListener(v -> selectVehicle("Private Car", "80", "80"));
        cardMotorcycle.setOnClickListener(v -> selectVehicle("Motorcycle", "20", "20"));
        cardTruck.setOnClickListener(v -> selectVehicle("Truck", "500", "500"));
        cardVan.setOnClickListener(v -> selectVehicle("Van / Berlingo", "150", "150"));
    }

    // Applies the selected vehicle defaults and scrolls to the editable values.
    private void selectVehicle(String vehicleName, String maxWeight, String maxVolume) {
        selectedVehicle = vehicleName;

        etDefaultMaxWeight.setText(maxWeight);
        etDefaultMaxVolume.setText(maxVolume);

        saveVehicleSelection(vehicleName);
        updateVehicleUi();

        scrollCourierDefaults.postDelayed(() -> {
            scrollCourierDefaults.smoothScrollTo(0, layoutCourierDefaultValues.getTop());
        }, 150);
    }

    // Updates the vehicle cards according to the current selection.
    private void updateVehicleUi() {
        clearVehicleSelectionUi();

        if ("Private Car".equals(selectedVehicle)) {
            markVehicleSelected(cardPrivateCar);
        } else if ("Motorcycle".equals(selectedVehicle)) {
            markVehicleSelected(cardMotorcycle);
        } else if ("Truck".equals(selectedVehicle)) {
            markVehicleSelected(cardTruck);
        } else if ("Van / Berlingo".equals(selectedVehicle)) {
            markVehicleSelected(cardVan);
        }
    }

    // Clears the visual selection state from all vehicle cards.
    private void clearVehicleSelectionUi() {
        cardPrivateCar.setBackgroundColor(Color.TRANSPARENT);
        cardMotorcycle.setBackgroundColor(Color.TRANSPARENT);
        cardTruck.setBackgroundColor(Color.TRANSPARENT);
        cardVan.setBackgroundColor(Color.TRANSPARENT);

        cardPrivateCar.setAlpha(1.0f);
        cardMotorcycle.setAlpha(1.0f);
        cardTruck.setAlpha(1.0f);
        cardVan.setAlpha(1.0f);
    }

    // Marks one vehicle card as selected and dims the other cards.
    private void markVehicleSelected(LinearLayout selectedCard) {
        cardPrivateCar.setAlpha(0.45f);
        cardMotorcycle.setAlpha(0.45f);
        cardTruck.setAlpha(0.45f);
        cardVan.setAlpha(0.45f);

        selectedCard.setAlpha(1.0f);
        selectedCard.setBackgroundColor(Color.parseColor("#22283A"));
    }

    // Saves the selected vehicle for the current courier.
    private void saveVehicleSelection(String vehicleName) {
        String companyId = sessionManager.getCompanyId();
        String courierId = sessionManager.getCourierId();

        if (companyId == null || courierId == null) {
            return;
        }

        SharedPreferences prefs = getSharedPreferences(VEHICLE_PREFS, MODE_PRIVATE);
        prefs.edit()
                .putString(getVehicleKey(companyId, courierId), vehicleName)
                .apply();
    }

    // Loads the saved vehicle selection for the current courier.
    private void loadSavedVehicleSelection() {
        String companyId = sessionManager.getCompanyId();
        String courierId = sessionManager.getCourierId();

        if (companyId == null || courierId == null) {
            selectedVehicle = "";
            updateVehicleUi();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(VEHICLE_PREFS, MODE_PRIVATE);
        selectedVehicle = prefs.getString(getVehicleKey(companyId, courierId), "");

        updateVehicleUi();
    }

    // Builds the storage key for a courier vehicle selection.
    private String getVehicleKey(String companyId, String courierId) {
        return "vehicle_" + companyId + "_" + courierId;
    }

    // Validates and saves the courier optimization defaults.
    private void saveDefaults() {
        String companyId = sessionManager.getCompanyId();
        String courierId = sessionManager.getCourierId();

        String maxDistance = etDefaultMaxDistance.getText().toString().trim();
        String maxWeight = etDefaultMaxWeight.getText().toString().trim();
        String maxVolume = etDefaultMaxVolume.getText().toString().trim();
        String maxStops = etDefaultMaxStops.getText().toString().trim();
        String endAddress = etDefaultEndAddress.getText().toString().trim();

        if (maxDistance.isEmpty() || maxWeight.isEmpty() || maxVolume.isEmpty() || maxStops.isEmpty()) {
            Toast.makeText(this, "Please fill all numeric fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Double.parseDouble(maxDistance);
            Double.parseDouble(maxWeight);
            Double.parseDouble(maxVolume);
            Integer.parseInt(maxStops);
        } catch (Exception e) {
            Toast.makeText(this, "Invalid numeric values.", Toast.LENGTH_SHORT).show();
            return;
        }

        defaultsStore.saveCourierDefaults(
                companyId,
                courierId,
                maxDistance,
                maxWeight,
                maxVolume,
                maxStops,
                endAddress
        );

        if (!selectedVehicle.trim().isEmpty()) {
            saveVehicleSelection(selectedVehicle);
        }

        Toast.makeText(this, "Courier optimization defaults saved.", Toast.LENGTH_SHORT).show();
        finish();
    }
}