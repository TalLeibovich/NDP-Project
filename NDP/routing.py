"""
Routing helpers: build route stops + cumulative metrics.
"""

from __future__ import annotations

from typing import Any, Dict, List, Tuple


def build_route_stops(
    *,
    route_node_ids: List[str],
    node_point_by_id: Dict[str, Tuple[float, float]],
    package_by_node_id: Dict[str, Dict[str, Any]],
    dist_km_by_edge: Dict[Tuple[str, str], float],
    location_address_by_node_id: Dict[str, Any] | None = None,
) -> List[Dict[str, Any]]:
    """
    בונה route_stops בפורמט שמחזירים ל־API.
    """
    stops: List[Dict[str, Any]] = []

    cum_km = 0.0
    cum_w = 0.0
    cum_v = 0.0
    cum_profit = 0.0

    for idx, node_id in enumerate(route_node_ids):
        lat, lon = node_point_by_id[node_id]
        node_address = (location_address_by_node_id or {}).get(node_id)

        # קביעת סוג
        if node_id == "start":
            stop_type = "start"
            pkg_id = None
        elif node_id == "end":
            stop_type = "end"
            pkg_id = None
        else:
            stop_type = "delivery"
            pkg_id = str(package_by_node_id[node_id]["id"])

        # leg_km
        if idx == 0:
            leg = 0.0
        else:
            prev_id = route_node_ids[idx - 1]
            leg = float(dist_km_by_edge[(prev_id, node_id)])
            cum_km += leg

        # אם זו חבילה – עדכון מצטברים
        if stop_type == "delivery":
            pkg = package_by_node_id[node_id]
            cum_w += float(pkg.get("weight", 0.0))
            cum_v += float(pkg.get("volume", 0.0))
            cum_profit += float(pkg.get("profit", 0.0))
            # Prefer geocoder-cleaned address, fallback to original, else null.
            stop_address = pkg.get("formatted_address") or pkg.get("address")
        else:
            stop_address = node_address

        stops.append(
            {
                "seq": idx,
                "type": stop_type,
                "lat": float(lat),
                "lon": float(lon),
                "package_id": pkg_id,
                "Address": stop_address,
                "leg_km": float(leg),
                "cum_km": float(cum_km),
                "cum_weight": float(cum_w),
                "cum_volume": float(cum_v),
                "cum_profit": float(cum_profit),
            }
        )

    return stops
