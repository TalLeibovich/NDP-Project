package com.example.ndpclient;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
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
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private final List<Marker> labelMarkers = new ArrayList<>();
    private final List<Marker> dotMarkers = new ArrayList<>();
    private final Map<Integer, com.google.android.gms.maps.model.Polyline> leaderLines = new HashMap<>();

    private String companyId;
    private String courierId;

    // palette
    private static final int COLOR_GRAY = Color.rgb(158, 158, 158);
    private static final int COLOR_GREEN = Color.rgb(46, 173, 98);
    private static final int COLOR_RED = Color.rgb(214, 69, 69);
    private static final int COLOR_BLUE = Color.rgb(30, 136, 229);
    private static final int COLOR_LINE = Color.argb(200, 0, 0, 0);

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

            RouteOrderParser.logStopList("polylineStops", stops, "RouteMap");

            // Clicking a label opens details like a marker
            map.setOnMarkerClickListener(marker -> {
                marker.showInfoWindow();
                return true;
            });

            renderRouteOnMap();

            // When zoom/pan changes, recompute label offsets in screen pixels
            map.setOnCameraIdleListener(this::updateLeaderLabelsPositions);
        });
    }

    private void loadStopsFromAssigned() {
        stops.clear();

        String json = resultStore.getAssigned(companyId, courierId);
        if (json == null || json.trim().isEmpty()) return;

        try {
            JSONObject obj = new JSONObject(json);
            stops.clear();
            stops.addAll(RouteOrderParser.parseOrderedStops(obj, "RouteMap"));
            RouteOrderParser.logStopList("mapStops", stops, "RouteMap");
        } catch (Exception ignored) {}
    }

    private void renderRouteOnMap() {
        map.clear();
        labelMarkers.clear();
        dotMarkers.clear();
        leaderLines.clear();

        // route polyline (real order)
        PolylineOptions routeLine = new PolylineOptions()
                .width(6f)
                .color(Color.BLACK);

        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        boolean hasAny = false;

        for (RouteStop s : stops) {
            LatLng pos = new LatLng(s.getLat(), s.getLon());
            routeLine.add(pos);
            bounds.include(pos);
            hasAny = true;
        }
        map.addPolyline(routeLine);

        if (hasAny) {
            try {
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 120));
            } catch (Exception e) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(stops.get(0).getLat(), stops.get(0).getLon()), 12f));
            }
        }

        // Create markers AFTER camera is set (so projection exists nicely)
        createDotAndLabelMarkers();
        updateLeaderLabelsPositions();
    }

    private void createDotAndLabelMarkers() {
        int currentSeq = progressStore.getCurrentSeq(companyId);

        for (RouteStop s : stops) {
            LatLng truePos = new LatLng(s.getLat(), s.getLon());

            int color = resolveColor(s, currentSeq);

            // 1) Dot marker (real location)
            Marker dot = map.addMarker(new MarkerOptions()
                    .position(truePos)
                    .icon(createDotIcon(color))
                    .anchor(0.5f, 0.5f)
                    .zIndex(5f)
                    .title(buildTitle(s))
                    .snippet(buildSnippet(s)));
            if (dot != null) dotMarkers.add(dot);

            // 2) Label marker (will be repositioned by projection later)
            Marker label = map.addMarker(new MarkerOptions()
                    .position(truePos) // temporary, will be moved
                    .icon(createLabelIcon(s.getSeq(), color))
                    .anchor(0.5f, 1.0f)
                    .zIndex(10f)
                    .title(buildTitle(s))
                    .snippet(buildSnippet(s)));
            if (label != null) labelMarkers.add(label);
        }
    }

    /**
     * Repositions numeric labels in screen pixels so they don't overlap,
     * and draws a leader line from the real dot to the numeric label.
     */
    private void updateLeaderLabelsPositions() {
        if (map == null) return;
        if (dotMarkers.size() != labelMarkers.size()) return;

        // Remove old leader lines
        for (com.google.android.gms.maps.model.Polyline pl : leaderLines.values()) {
            if (pl != null) pl.remove();
        }
        leaderLines.clear();

        for (int i = 0; i < dotMarkers.size(); i++) {
            Marker dot = dotMarkers.get(i);
            Marker label = labelMarkers.get(i);

            LatLng truePos = dot.getPosition();

            // Convert to screen, apply pixel offset pattern (spiral-ish)
            Point p = map.getProjection().toScreenLocation(truePos);
            Point off = offsetForIndex(i);

            Point p2 = new Point(p.x + off.x, p.y + off.y);
            LatLng labelPos = map.getProjection().fromScreenLocation(p2);

            label.setPosition(labelPos);

            // Leader line (dot -> label)
            com.google.android.gms.maps.model.Polyline pl = map.addPolyline(
                    new PolylineOptions()
                            .add(truePos, labelPos)
                            .width(4f)
                            .color(COLOR_LINE)
                            .zIndex(3f)
            );
            leaderLines.put(i, pl);
        }
    }

    // Offsets are in screen pixels - spread labels so numbers never overlap
    private Point offsetForIndex(int i) {
        // pattern: rotate around + different radius
        // (px) tuned for phone screens
        int[] dx = new int[]{ 0, 80, -80, 90, -90, 0, 110, -110, 60, -60 };
        int[] dy = new int[]{ -90, -70, -70, 20, 20, 95, 60, 60, 115, 115 };

        int idx = i % dx.length;

        // Slightly increase spread for larger routes
        int scale = 1 + (i / dx.length);
        return new Point(dx[idx] * scale, dy[idx] * scale);
    }

    private int resolveColor(RouteStop s, int currentSeq) {
        if (s.getSeq() == 0) return COLOR_BLUE;

        RouteStopStatusStore.Status st = statusStore.getStatus(companyId, s.getPackageId());
        if (st == RouteStopStatusStore.Status.DELIVERED) return COLOR_GREEN;
        if (st == RouteStopStatusStore.Status.FAILED) return COLOR_RED;

        if (s.getSeq() == currentSeq) return COLOR_BLUE;
        return COLOR_GRAY;
    }

    private String buildTitle(RouteStop s) {
        if (s.getSeq() == 0) return "#0 • START";
        return "#" + s.getSeq() + " • DELIVERY • " + (s.getPackageId() == null ? "-" : s.getPackageId());
    }

    private String buildSnippet(RouteStop s) {
        return "lat/lon: " + s.getLat() + ", " + s.getLon() +
                "\nleg_km: " + round3(s.getLegKm()) + " | cum_km: " + round3(s.getCumKm()) +
                "\ncum_weight: " + round3(s.getCumWeight()) +
                " | cum_volume: " + round3(s.getCumVolume()) +
                " | cum_profit: " + round3(s.getCumProfit()) +
                "\nStatus: " + resolveStatusText(s);
    }

    private String resolveStatusText(RouteStop s) {
        if (s.getSeq() == 0) return "START";
        RouteStopStatusStore.Status st = statusStore.getStatus(companyId, s.getPackageId());
        return st == null ? "PENDING" : st.name();
    }

    private BitmapDescriptor createDotIcon(int color) {
        int size = 26;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(color);
        fill.setStyle(Paint.Style.FILL);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setColor(Color.WHITE);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(4f);

        float r = size / 2f - 2f;
        c.drawCircle(size / 2f, size / 2f, r, fill);
        c.drawCircle(size / 2f, size / 2f, r, stroke);

        return BitmapDescriptorFactory.fromBitmap(b);
    }

    private BitmapDescriptor createLabelIcon(int number, int color) {
        int w = 110;
        int h = 70;

        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(color);
        bg.setStyle(Paint.Style.FILL);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setColor(Color.WHITE);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(6f);

        RectF r = new RectF(6, 6, w - 6, h - 6);
        c.drawRoundRect(r, 26f, 26f, bg);
        c.drawRoundRect(r, 26f, 26f, stroke);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setTextSize(34f);

        c.drawText(String.valueOf(number), w / 2f, h / 2f + 12f, text);

        return BitmapDescriptorFactory.fromBitmap(b);
    }

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
                    Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + dest.getLat() + "," + dest.getLon())));
        }
    }

    private RouteStop findBySeq(int seq) {
        for (RouteStop s : stops) {
            if (s.getSeq() == seq) return s;
        }
        return null;
    }

    private double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}