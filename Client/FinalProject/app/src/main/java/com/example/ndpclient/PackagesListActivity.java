package com.example.ndpclient;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class PackagesListActivity extends AppCompatActivity {

    private TextView tvPackagesStatus;
    private TextView tvPackagesCompany;
    private Button btnBackPackages;
    private Button btnLoadPackages;
    private Button btnAddPackage;
    private ListView listViewPackages;

    private CheckBox cbFilterAll;
    private CheckBox cbFilterOpen;
    private CheckBox cbFilterDelivered;

    private int deliveredFilter = 0;
    private boolean suppressFilterEvents = false;

    private SessionManager sessionManager;
    private final List<PackageItem> packages = new ArrayList<>();
    private PackageListAdapter adapter;

    // Initializes the package list screen and loads company packages.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_packages_list);

        sessionManager = new SessionManager(this);

        tvPackagesStatus = findViewById(R.id.tvPackagesStatus);
        tvPackagesCompany = findViewById(R.id.tvPackagesCompany);
        btnBackPackages = findViewById(R.id.btnBackPackages);
        btnLoadPackages = findViewById(R.id.btnLoadPackages);
        btnAddPackage = findViewById(R.id.btnAddPackage);
        listViewPackages = findViewById(R.id.listViewPackages);

        cbFilterAll = findViewById(R.id.cbFilterAll);
        cbFilterOpen = findViewById(R.id.cbFilterOpen);
        cbFilterDelivered = findViewById(R.id.cbFilterDelivered);

        adapter = new PackageListAdapter(this, packages);
        listViewPackages.setAdapter(adapter);

        String companyName = sessionManager.getCompanyName();
        if (companyName == null) companyName = "Unknown company";
        tvPackagesCompany.setText(getString(R.string.selected_company_prefix, companyName));

        btnBackPackages.setOnClickListener(v -> {
            Intent intent = new Intent(PackagesListActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });

        btnLoadPackages.setOnClickListener(v -> loadPackages());

        btnAddPackage.setOnClickListener(v -> {
            Intent intent = new Intent(PackagesListActivity.this, AddPackageActivity.class);
            startActivity(intent);
        });

        listViewPackages.setOnItemClickListener((parent, view, position, id) -> {
            PackageItem item = packages.get(position);

            Intent intent = new Intent(PackagesListActivity.this, EditPackageActivity.class);
            intent.putExtra("package_id", item.getId());
            intent.putExtra("weight", String.valueOf(item.getWeight()));
            intent.putExtra("volume", String.valueOf(item.getVolume()));
            intent.putExtra("profit", String.valueOf(item.getProfit()));
            intent.putExtra("deadline", item.getDeadline());
            intent.putExtra("lat", String.valueOf(item.getLat()));
            intent.putExtra("lon", String.valueOf(item.getLon()));
            intent.putExtra("address", item.getAddress());
            intent.putExtra("formatted_address", item.getFormattedAddress());
            startActivity(intent);
        });

        setupFilterCheckboxes();

        loadPackages();
    }

    // Reloads packages when returning from add or edit screens.
    @Override
    protected void onResume() {
        super.onResume();
        loadPackages();
    }

    // Configures package status filters as mutually exclusive checkboxes.
    private void setupFilterCheckboxes() {
        suppressFilterEvents = true;
        cbFilterAll.setChecked(false);
        cbFilterOpen.setChecked(true);
        cbFilterDelivered.setChecked(false);
        suppressFilterEvents = false;

        CompoundButton.OnCheckedChangeListener listener = (buttonView, isChecked) -> {
            if (suppressFilterEvents) return;
            if (!isChecked) return;

            suppressFilterEvents = true;

            if (buttonView == cbFilterAll) {
                deliveredFilter = -1;
                cbFilterAll.setChecked(true);
                cbFilterOpen.setChecked(false);
                cbFilterDelivered.setChecked(false);
            } else if (buttonView == cbFilterOpen) {
                deliveredFilter = 0;
                cbFilterAll.setChecked(false);
                cbFilterOpen.setChecked(true);
                cbFilterDelivered.setChecked(false);
            } else if (buttonView == cbFilterDelivered) {
                deliveredFilter = 1;
                cbFilterAll.setChecked(false);
                cbFilterOpen.setChecked(false);
                cbFilterDelivered.setChecked(true);
            }

            suppressFilterEvents = false;
            loadPackages();
        };

        cbFilterAll.setOnCheckedChangeListener(listener);
        cbFilterOpen.setOnCheckedChangeListener(listener);
        cbFilterDelivered.setOnCheckedChangeListener(listener);
    }

    // Updates the title according to the active package filter.
    private void updatePackageCountTitle() {
        int count = packages.size();

        if (deliveredFilter == -1) {
            tvPackagesStatus.setText(getString(R.string.all_packages_count, count));
        } else if (deliveredFilter == 0) {
            tvPackagesStatus.setText(getString(R.string.open_packages_count_title, count));
        } else {
            tvPackagesStatus.setText(getString(R.string.delivered_packages_count, count));
        }
    }

    // Loads company packages from the API using the selected delivery filter.
    private void loadPackages() {
        String companyId = sessionManager.getCompanyId();
        if (companyId == null) {
            tvPackagesStatus.setText(R.string.no_company_selected);
            return;
        }

        tvPackagesStatus.setText(R.string.loading_packages);

        try {
            String path = "/packages?company_id=" + URLEncoder.encode(companyId, "UTF-8");

            if (deliveredFilter == 0) path += "&delivered=0";
            else if (deliveredFilter == 1) path += "&delivered=1";

            ApiClient.get(path, new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(String json) {
                    runOnUiThread(() -> {
                        try {
                            packages.clear();

                            JSONArray array = new JSONArray(json);
                            for (int i = 0; i < array.length(); i++) {
                                JSONObject obj = array.getJSONObject(i);

                                String id = obj.optString("id", "");
                                String cId = obj.optString("company_id", "");
                                double lat = obj.optDouble("lat", 0.0);
                                double lon = obj.optDouble("lon", 0.0);
                                double weight = obj.optDouble("weight", 0.0);
                                double volume = obj.optDouble("volume", 0.0);
                                double profit = obj.optDouble("profit", 0.0);
                                String deadline = obj.optString("deadline", "");
                                int delivered = obj.optInt("delivered", 0);

                                String address = readAddress(obj);
                                String formattedAddress = readFormattedAddress(obj);

                                packages.add(new PackageItem(
                                        id, cId, lat, lon, weight, volume, profit,
                                        deadline, delivered, address, formattedAddress
                                ));
                            }

                            adapter.notifyDataSetChanged();

                            if (packages.isEmpty()) {
                                tvPackagesStatus.setText(R.string.no_packages_found);
                            } else {
                                updatePackageCountTitle();
                            }

                        } catch (Exception e) {
                            tvPackagesStatus.setText(R.string.failed_to_parse_packages);
                        }
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> tvPackagesStatus.setText(R.string.failed_to_load_packages));
                }
            });

        } catch (Exception e) {
            tvPackagesStatus.setText(R.string.failed_to_load_packages);
        }
    }

    // Reads the package address using supported response field names.
    private String readAddress(JSONObject obj) {
        String address = obj.optString("address", null);
        if (address != null && !address.trim().isEmpty() && !"null".equalsIgnoreCase(address)) return address;

        address = obj.optString("Address", null);
        if (address != null && !address.trim().isEmpty() && !"null".equalsIgnoreCase(address)) return address;

        return null;
    }

    // Reads the formatted package address using supported response field names.
    private String readFormattedAddress(JSONObject obj) {
        String formatted = obj.optString("formatted_address", null);
        if (formatted != null && !formatted.trim().isEmpty() && !"null".equalsIgnoreCase(formatted)) return formatted;

        formatted = obj.optString("FormattedAddress", null);
        if (formatted != null && !formatted.trim().isEmpty() && !"null".equalsIgnoreCase(formatted)) return formatted;

        return null;
    }
}