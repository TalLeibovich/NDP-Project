package com.example.ndpclient;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RouteStopsAdapter extends ArrayAdapter<RouteStop> {

    private final String companyId;
    private final RouteStopStatusStore statusStore;
    private final DeliveryProgressStore progressStore;
    private final SessionManager sessionManager;

    public RouteStopsAdapter(Context context, List<RouteStop> stops, String companyId) {
        super(context, 0, stops);
        this.companyId = companyId;
        this.statusStore = new RouteStopStatusStore(context);
        this.progressStore = new DeliveryProgressStore(context);
        this.sessionManager = new SessionManager(context);
    }

    private int getCurrentSeq() {
        return progressStore.getCurrentSeq(companyId);
    }

    private List<RouteStop> snapshotStops() {
        ArrayList<RouteStop> list = new ArrayList<>();
        for (int i = 0; i < getCount(); i++) {
            RouteStop s = getItem(i);
            if (s != null) list.add(s);
        }
        return list;
    }

    private boolean hasRouteStarted(List<RouteStop> allStops) {
        int currentSeq = getCurrentSeq();
        if (currentSeq >= 1) return true;

        if (allStops != null && allStops.size() > 1) {
            RouteStop firstDelivery = allStops.get(1);
            if (firstDelivery.isDelivery() && firstDelivery.getPackageId() != null) {
                RouteStopStatusStore.Status st = statusStore.getStatus(companyId, firstDelivery.getPackageId());
                return st == RouteStopStatusStore.Status.DELIVERED || st == RouteStopStatusStore.Status.FAILED;
            }
        }
        return false;
    }

    private void advanceToNextDelivery(List<RouteStop> allStops, int currentStopSeq) {
        int nextSeq = currentStopSeq;

        for (RouteStop s : allStops) {
            if (s.getSeq() > currentStopSeq && s.isDelivery()) {
                nextSeq = s.getSeq();
                break;
            }
        }

        progressStore.setCurrentSeq(companyId, nextSeq);
    }

    private String formatStartDetails(RouteStop stop) {
        // Keep START minimal & stable
        return String.format(
                Locale.US,
                "Location: %.5f, %.5f",
                stop.getLat(),
                stop.getLon()
        );
    }

    private String formatDeliveryDetails(RouteStop stop) {
        // Requested: only full words (no coords, no km, no cum_*)
        // NOTE: We display the values we have on RouteStop (currently cumWeight/cumVolume/cumProfit).
        return String.format(
                Locale.US,
                "Weight: %.2f\nVolume: %.2f\nProfit: %.2f",
                stop.getCumWeight(),
                stop.getCumVolume(),
                stop.getCumProfit()
        );
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        RouteStop stop = getItem(position);

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.route_stop_item, parent, false);
        }

        TextView tvTitle = convertView.findViewById(R.id.tvStopTitle);
        TextView tvDetails = convertView.findViewById(R.id.tvStopDetails);
        TextView tvStatus = convertView.findViewById(R.id.tvStopStatus);
        LinearLayout layoutButtons = convertView.findViewById(R.id.layoutButtons);
        Button btnDelivered = convertView.findViewById(R.id.btnDelivered);
        Button btnFailed = convertView.findViewById(R.id.btnFailed);

        List<RouteStop> allStops = snapshotStops();
        int currentSeq = getCurrentSeq();
        boolean started = hasRouteStarted(allStops);

        // Title
        String title;
        if ("start".equalsIgnoreCase(stop.getType())) {
            title = "#0 • START";
        } else if (stop.isDelivery()) {
            title = "#" + stop.getSeq() + " • DELIVERY • " + stop.getPackageId();
        } else {
            title = "#" + stop.getSeq() + " • " + stop.getType();
        }
        tvTitle.setText(title);

        // Details (UPDATED)
        if ("start".equalsIgnoreCase(stop.getType())) {
            tvDetails.setText(formatStartDetails(stop));
        } else if (stop.isDelivery()) {
            tvDetails.setText(formatDeliveryDetails(stop));
        } else {
            // fallback
            tvDetails.setText("");
        }

        // START status
        if ("start".equalsIgnoreCase(stop.getType())) {
            tvStatus.setText("Status: " + (started ? "STARTED" : "NOT_STARTED"));
            layoutButtons.setVisibility(View.GONE);
            return convertView;
        }

        // Delivery stops
        if (stop.isDelivery() && stop.getPackageId() != null) {

            RouteStopStatusStore.Status stored = statusStore.getStatus(companyId, stop.getPackageId());
            RouteStopStatusStore.Status displayStatus = stored;

            if (stop.getSeq() == currentSeq &&
                    stored != RouteStopStatusStore.Status.DELIVERED &&
                    stored != RouteStopStatusStore.Status.FAILED) {
                displayStatus = RouteStopStatusStore.Status.CURRENT;
            } else if (stored == RouteStopStatusStore.Status.CURRENT) {
                displayStatus = RouteStopStatusStore.Status.PENDING;
            }

            tvStatus.setText("Status: " + displayStatus.name());
            layoutButtons.setVisibility(View.VISIBLE);

            // Disable buttons if already done
            boolean alreadyFinished = (stored == RouteStopStatusStore.Status.DELIVERED || stored == RouteStopStatusStore.Status.FAILED);
            btnDelivered.setEnabled(!alreadyFinished);
            btnFailed.setEnabled(!alreadyFinished);

            // ✅ DELIVERED -> update server + local
            btnDelivered.setOnClickListener(v -> {
                // prevent double taps
                btnDelivered.setEnabled(false);
                btnFailed.setEnabled(false);

                String courierId = sessionManager.getCourierId();
                if (courierId == null || courierId.trim().isEmpty()) {
                    Toast.makeText(getContext(), "Missing courier_id", Toast.LENGTH_SHORT).show();
                    btnDelivered.setEnabled(true);
                    btnFailed.setEnabled(true);
                    return;
                }

                try {
                    JSONObject body = new JSONObject();
                    body.put("package_id", stop.getPackageId());
                    body.put("delivered", true);

                    ApiClient.post("/couriers/" + courierId + "/deliveries", body.toString(), new ApiClient.ApiCallback() {
                        @Override
                        public void onSuccess(String json) {
                            statusStore.setStatus(companyId, stop.getPackageId(), RouteStopStatusStore.Status.DELIVERED);
                            advanceToNextDelivery(allStops, stop.getSeq());

                            ((android.app.Activity) getContext()).runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Delivered ✔", Toast.LENGTH_SHORT).show();
                                notifyDataSetChanged();
                            });
                        }

                        @Override
                        public void onError(Exception e) {
                            ((android.app.Activity) getContext()).runOnUiThread(() -> {
                                Toast.makeText(getContext(),
                                        "Deliver update failed: " + (e == null ? "" : e.getMessage()),
                                        Toast.LENGTH_LONG).show();
                                btnDelivered.setEnabled(true);
                                btnFailed.setEnabled(true);
                            });
                        }
                    });

                } catch (Exception ex) {
                    Toast.makeText(getContext(), "Failed to build request", Toast.LENGTH_SHORT).show();
                    btnDelivered.setEnabled(true);
                    btnFailed.setEnabled(true);
                }
            });

            // FAILED -> local only
            btnFailed.setOnClickListener(v -> {
                statusStore.setStatus(companyId, stop.getPackageId(), RouteStopStatusStore.Status.FAILED);
                advanceToNextDelivery(allStops, stop.getSeq());
                notifyDataSetChanged();
            });

            return convertView;
        }

        // Default non-delivery
        tvStatus.setText("Status: PENDING");
        layoutButtons.setVisibility(View.GONE);
        return convertView;
    }
}