package com.example.ndpclient;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class CourierSelectionActivity extends AppCompatActivity {

    private TextView tvCourierStatus;
    private TextView tvSelectedCompany;
    private Button btnLoadCouriers;
    private Button btnAddCourier;
    private Button btnBackCourier;
    private ListView listViewCouriers;
    private EditText etCourierId;
    private EditText etCourierName;

    private SessionManager sessionManager;
    private final List<Courier> couriers = new ArrayList<>();
    private ArrayAdapter<Courier> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_courier_selection);

        sessionManager = new SessionManager(this);

        // Guard: חייב חברה
        if (!sessionManager.hasCompany()) {
            finish();
            return;
        }

        tvCourierStatus = findViewById(R.id.tvCourierStatus);
        tvSelectedCompany = findViewById(R.id.tvSelectedCompany);
        btnLoadCouriers = findViewById(R.id.btnLoadCouriers);
        btnAddCourier = findViewById(R.id.btnAddCourier);
        btnBackCourier = findViewById(R.id.btnBackCourier);
        listViewCouriers = findViewById(R.id.listViewCouriers);
        etCourierId = findViewById(R.id.etCourierId);
        etCourierName = findViewById(R.id.etCourierName);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, couriers);
        listViewCouriers.setAdapter(adapter);

        String companyName = sessionManager.getCompanyName();
        if (companyName == null) companyName = "Unknown company";
        tvSelectedCompany.setText(getString(R.string.selected_company_prefix, companyName));

        // Back -> חוזר ל-Main (לא CompanySelection)
        btnBackCourier.setOnClickListener(v -> finish());

        btnLoadCouriers.setOnClickListener(v -> loadCouriers());
        btnAddCourier.setOnClickListener(v -> addCourier());

        listViewCouriers.setOnItemClickListener((parent, view, position, id) -> {
            Courier selected = couriers.get(position);
            sessionManager.saveCourier(selected.getCourierId(), selected.getName());
            sessionManager.saveLastCourierForCompany(sessionManager.getCompanyId(), selected.getCourierId(), selected.getName());
            finish();
        });

        loadCouriers();
    }

    private void loadCouriers() {
        String companyId = sessionManager.getCompanyId();
        if (companyId == null) {
            tvCourierStatus.setText(R.string.no_company_selected);
            return;
        }

        tvCourierStatus.setText(R.string.loading_couriers);

        try {
            String path = "/couriers?company_id=" + URLEncoder.encode(companyId, "UTF-8");

            ApiClient.get(path, new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(String json) {
                    runOnUiThread(() -> {
                        try {
                            couriers.clear();

                            JSONArray array = new JSONArray(json);
                            for (int i = 0; i < array.length(); i++) {
                                JSONObject obj = array.getJSONObject(i);

                                String courierId = obj.optString("courier_id", "");
                                String cId = obj.optString("company_id", "");
                                String name = obj.optString("name", "");

                                couriers.add(new Courier(courierId, cId, name));
                            }

                            adapter.notifyDataSetChanged();

                            if (couriers.isEmpty()) {
                                tvCourierStatus.setText(R.string.no_couriers_found);
                            } else {
                                tvCourierStatus.setText(R.string.select_courier);
                            }

                        } catch (Exception e) {
                            tvCourierStatus.setText(R.string.failed_to_parse_couriers);
                        }
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> tvCourierStatus.setText(R.string.failed_to_load_couriers));
                }
            });

        } catch (Exception e) {
            tvCourierStatus.setText(R.string.failed_to_load_couriers);
        }
    }

    private void addCourier() {
        String companyId = sessionManager.getCompanyId();
        if (companyId == null) {
            tvCourierStatus.setText(R.string.no_company_selected);
            return;
        }

        String courierId = etCourierId.getText().toString().trim();
        String courierName = etCourierName.getText().toString().trim();

        if (courierId.isEmpty() || courierName.isEmpty()) {
            tvCourierStatus.setText(R.string.fill_courier_fields);
            return;
        }

        tvCourierStatus.setText(R.string.creating_courier);

        try {
            JSONObject body = new JSONObject();
            body.put("courier_id", courierId);
            body.put("company_id", companyId);
            body.put("name", courierName);

            ApiClient.post("/couriers", body.toString(), new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(String json) {
                    runOnUiThread(() -> {
                        etCourierId.setText("");
                        etCourierName.setText("");
                        tvCourierStatus.setText(R.string.courier_created);
                        loadCouriers();
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> tvCourierStatus.setText(R.string.failed_to_create_courier));
                }
            });

        } catch (Exception e) {
            tvCourierStatus.setText(R.string.failed_to_create_courier);
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}