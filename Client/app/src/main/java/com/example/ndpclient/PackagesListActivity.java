package com.example.ndpclient;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
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

    // ✅ Filters
    private CheckBox cbFilterAll;
    private CheckBox cbFilterOpen;
    private CheckBox cbFilterDelivered;

    // -1 = ALL, 0 = OPEN (delivered=0), 1 = DELIVERED (delivered=1)
    private int deliveredFilter = 0; // default OPEN
    private boolean suppressFilterEvents = false;

    private SessionManager sessionManager;
    private final List<PackageItem> packages = new ArrayList<>();
    private ArrayAdapter<PackageItem> adapter;

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

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, packages);
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
            startActivity(intent);
        });

        setupFilterCheckboxes();

        loadPackages();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPackages();
    }

    private void setupFilterCheckboxes() {
        // default: OPEN
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

    private void loadPackages() {
        String companyId = sessionManager.getCompanyId();
        if (companyId == null) {
            tvPackagesStatus.setText(R.string.no_company_selected);
            return;
        }

        tvPackagesStatus.setText(R.string.loading_packages);

        try {
            String path = "/packages?company_id=" + URLEncoder.encode(companyId, "UTF-8");

            // ✅ apply filter only if not ALL
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

                                packages.add(new PackageItem(
                                        id, cId, lat, lon, weight, volume, profit, deadline, delivered
                                ));
                            }

                            adapter.notifyDataSetChanged();

                            if (packages.isEmpty()) {
                                tvPackagesStatus.setText(R.string.no_packages_found);
                            } else {
                                // אם יש לך string מתאים לכל מצב - אפשר לשפר. כרגע נשאיר את מה שיש:
                                tvPackagesStatus.setText(getString(R.string.open_packages_count, packages.size()));
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
}