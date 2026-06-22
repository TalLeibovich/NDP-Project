package com.example.ndpclient;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RouteMapActivity extends AppCompatActivity {

    private GoogleMap map;
    private Button btnBackMap;
    private Button btnNavigateCurrent;

    private OptimizeResultStore resultStore;
    private DeliveryProgressStore progressStore;
    private SessionManager sessionManager;
    private RouteStopStatusStore statusStore;

    private final List<RouteStop> stops = new ArrayList<>();

    private String companyId;
    private String courierId;

    private static final int COLOR_GRAY = Color.rgb(158, 158, 158);
    private static final int COLOR_GREEN = Color.rgb(46, 173, 98);
    private static final int COLOR_RED = Color.rgb(214, 69, 69);
    private static final int COLOR_BLUE = Color.rgb(30, 136, 229);

    // Initializes the route map screen and loads the assigned route.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_map);

        resultStore = new OptimizeResultStore(this);
        progressStore = new DeliveryProgressStore(this);
        sessionManager = new SessionManager(this);
        statusStore = new RouteStopStatusStore(this);

        companyId = sessionManager.getCompanyId();
        courierId = sessionManager.getCourierId();

        btnBackMap = findViewById(R.id.btnBackMap);
        btnNavigateCurrent = findViewById(R.id.btnNavigateCurrent);

        btnBackMap.setOnClickListener(v -> finish());
        btnNavigateCurrent.setOnClickListener(v -> navigateCurrentLeg());

        SupportMapFragment mapFragment = new SupportMapFragment();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.mapContainer, mapFragment)
                .commit();

        mapFragment.getMapAsync(googleMap -> {
            map = googleMap;

            if (companyId == null || courierId == null) {
                Toast.makeText(this, "Missing session (company/courier).", Toast.LENGTH_SHORT).show();
                return;
            }

            loadStopsFromAssigned();

            if (stops.isEmpty()) {
                Toast.makeText(this, "No assigned route to display.", Toast.LENGTH_SHORT).show();
                return;
            }

            renderRouteOnMap();
        });
    }

    // Loads assigned route stops from local storage.
    private void loadStopsFromAssigned() {
        stops.clear();

        String json = resultStore.getAssigned(companyId, courierId);
        if (json == null || json.trim().isEmpty()) return;

        try {
            JSONObject obj = new JSONObject(json);
            JSONArray routeStopsArr = obj.optJSONArray("route_stops");
            if (routeStopsArr == null) return;

            for (int i = 0; i < routeStopsArr.length(); i++) {
                JSONObject s = routeStopsArr.getJSONObject(i);

                int seq = s.optInt("seq", i);
                String type = s.optString("type", "");
                double lat = s.optDouble("lat", 0.0);
                double lon = s.optDouble("lon", 0.0);
                String packageId = s.optString("package_id", null);
                String address = readAddress(s);

                double legKm = s.optDouble("leg_km", 0.0);
                double cumKm = s.optDouble("cum_km", 0.0);
                double cumWeight = s.optDouble("cum_weight", 0.0);
                double cumVolume = s.optDouble("cum_volume", 0.0);
                double cumProfit = s.optDouble("cum_profit", 0.0);

                if (packageId != null && packageId.trim().isEmpty()) packageId = null;

                stops.add(new RouteStop(
                        seq, type, lat, lon, packageId, address,
                        legKm, cumKm, cumWeight, cumVolume, cumProfit
                ));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Reads a stop address using supported response field names.
    private String readAddress(JSONObject obj) {
        String formatted = obj.optString("formatted_address", null);
        if (isValid(formatted)) return formatted;

        formatted = obj.optString("FormattedAddress", null);
        if (isValid(formatted)) return formatted;

        String address = obj.optString("address", null);
        if (isValid(address)) return address;

        address = obj.optString("Address", null);
        if (isValid(address)) return address;

        return null;
    }

    // Checks whether a string contains meaningful content.
    private boolean isValid(String s) {
        return s != null && !s.trim().isEmpty() && !"null".equalsIgnoreCase(s.trim());
    }

    // Renders the full assigned route on the Google map.
    private void renderRouteOnMap() {
        map.clear();

        PolylineOptions routeLine = new PolylineOptions()
                .width(6f)
                .color(Color.BLACK);

        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        boolean hasAny = false;

        for (RouteStop stop : stops) {
            LatLng pos = new LatLng(stop.getLat(), stop.getLon());
            routeLine.add(pos);
            bounds.include(pos);
            hasAny = true;
        }

        map.addPolyline(routeLine);

        Map<String, List<RouteStop>> groups = groupStopsByLocation();

        for (List<RouteStop> group : groups.values()) {
            if (group.isEmpty()) continue;

            RouteStop first = group.get(0);
            LatLng pos = new LatLng(first.getLat(), first.getLon());

            String label = buildGroupLabel(group);
            int color = resolveGroupColor(group);

            map.addMarker(new MarkerOptions()
                    .position(pos)
                    .icon(createCircleLabelIcon(label, color))
                    .anchor(0.5f, 0.5f)
                    .title(buildGroupTitle(group))
                    .snippet(buildGroupSnippet(group)));
        }

        if (hasAny) {
            try {
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 140));
            } catch (Exception e) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(stops.get(0).getLat(), stops.get(0).getLon()), 11f));
            }
        }
    }

    // Groups route stops that share the same coordinates.
    private Map<String, List<RouteStop>> groupStopsByLocation() {
        Map<String, List<RouteStop>> groups = new LinkedHashMap<>();

        for (RouteStop stop : stops) {
            String key = locationKey(stop);
            if (!groups.containsKey(key)) {
                groups.put(key, new ArrayList<>());
            }
            groups.get(key).add(stop);
        }

        return groups;
    }

    // Builds a stable grouping key from stop coordinates.
    private String locationKey(RouteStop stop) {
        return String.format(Locale.US, "%.6f,%.6f", stop.getLat(), stop.getLon());
    }

    // Builds the marker label for one or more stops at the same location.
    private String buildGroupLabel(List<RouteStop> group) {
        if (group.size() == 1) {
            return String.valueOf(group.get(0).getSeq());
        }

        int min = group.get(0).getSeq();
        int max = group.get(0).getSeq();

        for (RouteStop stop : group) {
            min = Math.min(min, stop.getSeq());
            max = Math.max(max, stop.getSeq());
        }

        boolean contiguous = (max - min + 1 == group.size());

        if (contiguous) {
            return min + "-" + max;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < group.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(group.get(i).getSeq());
        }
        return sb.toString();
    }

    // Resolves the marker color according to route progress and delivery status.
    private int resolveGroupColor(List<RouteStop> group) {
        for (RouteStop stop : group) {
            if (stop.getSeq() == 0) return COLOR_BLUE;
        }

        int currentSeq = progressStore.getCurrentSeq(companyId);
        for (RouteStop stop : group) {
            if (stop.getSeq() == currentSeq) return COLOR_BLUE;
        }

        boolean hasFailed = false;
        boolean allDelivered = true;

        for (RouteStop stop : group) {
            if (stop.getPackageId() == null) {
                allDelivered = false;
                continue;
            }

            RouteStopStatusStore.Status st = statusStore.getStatus(companyId, stop.getPackageId());

            if (st == RouteStopStatusStore.Status.FAILED) {
                hasFailed = true;
            }

            if (st != RouteStopStatusStore.Status.DELIVERED) {
                allDelivered = false;
            }
        }

        if (hasFailed) return COLOR_RED;
        if (allDelivered) return COLOR_GREEN;

        return COLOR_GRAY;
    }

    // Builds the marker title for a grouped location.
    private String buildGroupTitle(List<RouteStop> group) {
        if (group.size() == 1) {
            RouteStop stop = group.get(0);
            if (stop.getSeq() == 0) return "#0 • START";
            return "#" + stop.getSeq() + " • DELIVERY • " +
                    (stop.getPackageId() == null ? "-" : stop.getPackageId());
        }

        return "Stops " + buildGroupLabel(group);
    }

    // Builds the marker snippet with address, package, and status details.
    private String buildGroupSnippet(List<RouteStop> group) {
        StringBuilder sb = new StringBuilder();

        RouteStop first = group.get(0);

        if (isValid(first.getAddress())) {
            sb.append("Address: ").append(first.getAddress());
        } else {
            sb.append("Coordinates: ")
                    .append(round5(first.getLat()))
                    .append(", ")
                    .append(round5(first.getLon()));
        }

        if (group.size() > 1) {
            sb.append("\nPackages at this address:");
        }

        for (RouteStop stop : group) {
            if (stop.getSeq() == 0) {
                sb.append("\n#0 START");
                continue;
            }

            sb.append("\n#")
                    .append(stop.getSeq())
                    .append(" • ")
                    .append(stop.getPackageId() == null ? "-" : stop.getPackageId())
                    .append(" | Weight: ")
                    .append(round2(stop.getCumWeight()))
                    .append(" | Volume: ")
                    .append(round2(stop.getCumVolume()))
                    .append(" | Profit: ")
                    .append(round2(stop.getCumProfit()))
                    .append(" | Status: ")
                    .append(resolveStatusText(stop));
        }

        return sb.toString();
    }

    // Resolves the status text shown in map marker details.
    private String resolveStatusText(RouteStop stop) {
        if (stop.getSeq() == 0) return "START";

        if (stop.getPackageId() != null) {
            RouteStopStatusStore.Status st = statusStore.getStatus(companyId, stop.getPackageId());
            if (st != null) return st.name();
        }

        return "PENDING";
    }

    // Creates a circular numbered marker icon.
    private BitmapDescriptor createCircleLabelIcon(String label, int color) {
        int size;

        if (label.length() <= 1) {
            size = 90;
        } else if (label.length() <= 3) {
            size = 105;
        } else {
            size = 125;
        }

        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(color);
        fill.setStyle(Paint.Style.FILL);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setColor(Color.WHITE);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(6f);

        float cx = size / 2f;
        float cy = size / 2f;
        float radius = size / 2f - 6f;

        canvas.drawCircle(cx, cy, radius, fill);
        canvas.drawCircle(cx, cy, radius, stroke);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        if (label.length() <= 1) {
            text.setTextSize(40f);
        } else if (label.length() <= 3) {
            text.setTextSize(32f);
        } else {
            text.setTextSize(26f);
        }

        float y = cy - ((text.descent() + text.ascent()) / 2f);
        canvas.drawText(label, cx, y, text);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    // Opens Google Maps navigation to the current delivery stop.
    private void navigateCurrentLeg() {
        int currentSeq = progressStore.getCurrentSeq(companyId);
        int destSeq = Math.max(1, currentSeq);

        RouteStop dest = findBySeq(destSeq);
        if (dest == null) {
            Toast.makeText(this, "No next stop to navigate to.", Toast.LENGTH_SHORT).show();
            return;
        }

        String uri = "google.navigation:q=" + dest.getLat() + "," + dest.getLon() + "&mode=d";
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        i.setPackage("com.google.android.apps.maps");

        try {
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/dir/?api=1&destination="
                            + dest.getLat() + "," + dest.getLon())));
        }
    }

    // Finds a route stop by sequence number.
    private RouteStop findBySeq(int seq) {
        for (RouteStop stop : stops) {
            if (stop.getSeq() == seq) return stop;
        }
        return null;
    }

    // Formats a number with two decimal digits.
    private String round2(double v) {
        return String.format(Locale.US, "%.2f", v);
    }

    // Formats a coordinate with five decimal digits.
    private String round5(double v) {
        return String.format(Locale.US, "%.5f", v);
    }
}