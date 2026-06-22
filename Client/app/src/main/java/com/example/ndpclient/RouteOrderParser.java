package com.example.ndpclient;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Canonical route-stop ordering from /optimize responses.
 * Visit order comes from route_stops (sorted by seq), never selected_package_ids.
 */
public final class RouteOrderParser {

    private static final String TAG = "RouteOrder";

    private RouteOrderParser() {}

    public static List<RouteStop> parseOrderedStops(JSONObject optimizeResponse, String logContext) {
        if (optimizeResponse == null) {
            return Collections.emptyList();
        }

        logRawRouteFields(optimizeResponse, logContext);

        JSONArray routeStopsArr = optimizeResponse.optJSONArray("route_stops");
        List<RouteStop> rawStops = parseRouteStopsArray(routeStopsArr);
        List<RouteStop> orderedStops = sortBySeq(rawStops);

        logStopList("adapterStops", orderedStops, logContext);
        return orderedStops;
    }

    public static void logStopList(String label, List<RouteStop> stops, String logContext) {
        if (stops == null) return;
        String prefix = (logContext == null || logContext.trim().isEmpty()) ? label : logContext + " " + label;
        for (int i = 0; i < stops.size(); i++) {
            RouteStop s = stops.get(i);
            String id = s.getPackageId() != null ? s.getPackageId() : s.getType();
            Log.d(TAG, prefix + "[" + i + "] seq=" + s.getSeq() + " id=" + id + " type=" + s.getType());
        }
    }

    private static void logRawRouteFields(JSONObject obj, String logContext) {
        String ctx = (logContext == null || logContext.trim().isEmpty()) ? "" : logContext + " ";

        JSONArray selected = obj.optJSONArray("selected_package_ids");
        if (selected != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < selected.length(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(selected.optString(i));
            }
            Log.d(TAG, ctx + "raw selected_package_ids (selection set, NOT visit order): [" + sb + "]");
        }

        JSONArray routeNodeIds = obj.optJSONArray("route_node_ids");
        if (routeNodeIds != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < routeNodeIds.length(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(routeNodeIds.optString(i));
            }
            Log.d(TAG, ctx + "raw route_node_ids: [" + sb + "]");
        }

        JSONArray routeStopsArr = obj.optJSONArray("route_stops");
        if (routeStopsArr != null) {
            for (int i = 0; i < routeStopsArr.length(); i++) {
                JSONObject s = routeStopsArr.optJSONObject(i);
                if (s == null) continue;
                int seq = s.optInt("seq", i);
                String type = s.optString("type", "");
                String id = s.optString("package_id", type);
                Log.d(TAG, ctx + "raw route_stops[" + i + "] seq=" + seq + " id=" + id + " type=" + type);
            }
        }

        List<RouteStop> sortedPreview = sortBySeq(parseRouteStopsArray(routeStopsArr));
        logStopList("route_stops sorted by seq", sortedPreview, logContext);
    }

    private static List<RouteStop> parseRouteStopsArray(JSONArray routeStopsArr) {
        List<RouteStop> stops = new ArrayList<>();
        if (routeStopsArr == null) {
            return stops;
        }

        for (int i = 0; i < routeStopsArr.length(); i++) {
            JSONObject s = routeStopsArr.optJSONObject(i);
            if (s == null) continue;

            int seq = s.optInt("seq", i);
            String type = s.optString("type", "");
            double lat = s.optDouble("lat", 0.0);
            double lon = s.optDouble("lon", 0.0);
            String packageId = s.optString("package_id", null);

            double legKm = s.optDouble("leg_km", 0.0);
            double cumKm = s.optDouble("cum_km", 0.0);
            double cumWeight = s.optDouble("cum_weight", 0.0);
            double cumVolume = s.optDouble("cum_volume", 0.0);
            double cumProfit = s.optDouble("cum_profit", 0.0);

            if (packageId != null && packageId.trim().isEmpty()) packageId = null;

            stops.add(new RouteStop(seq, type, lat, lon, packageId,
                    legKm, cumKm, cumWeight, cumVolume, cumProfit));
        }
        return stops;
    }

    private static List<RouteStop> sortBySeq(List<RouteStop> stops) {
        if (stops == null || stops.size() <= 1) {
            return stops == null ? Collections.emptyList() : new ArrayList<>(stops);
        }
        List<RouteStop> ordered = new ArrayList<>(stops);
        Collections.sort(ordered, Comparator.comparingInt(RouteStop::getSeq));
        return ordered;
    }
}
