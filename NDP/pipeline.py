from __future__ import annotations
from datetime import date

from optimize_debug import is_enabled as _debug_optimize, log as _debug_log

MAX_FINAL_CANDIDATE_SETS = 300


def optimize_for_courier(req: dict, packages: list, distance_provider) -> dict:
    from scoring import package_score
    from selection import beam_select_package_sets
    from mst_tsp import build_route
    from routing import build_route_stops

    # =========================================================
    # שלב 0 – קלט ומגבלות
    # =========================================================
    c = req["constraints"]
    max_distance_km = float(c.get("max_distance_km", 1e18))
    max_weight = float(c.get("max_weight", 1e18))
    max_volume = float(c.get("max_volume", 1e18))
    max_stops = int(c.get("max_stops", 20))
    service_date = req.get("service_date") or date.today()
    end = req.get("end")
    start_address = req.get("start_address")
    end_address = req.get("end_address")

    # =========================================================
    # שלב 1 – חישוב SCORE לכל חבילה
    # =========================================================
    score_by_id = {str(p["id"]): float(package_score(p, service_date)) for p in packages}

    # =========================================================
    # שלב 2 – טעינת מטריצת מרחקי כביש מלאה
    # אינדקס 0 = start
    # =========================================================
    dummy_points = [None] * (len(packages) + 1 + (1 if end is not None else 0))
    full_dist = distance_provider.distance_matrix_km(dummy_points)

    # =========================================================
    # שלב 2.1 – סינון מוקדם: dist[start][pkg] <= max_distance
    # =========================================================
    filtered_packages = []
    for i, p in enumerate(packages, start=1):
        if full_dist[0][i] <= max_distance_km:
            filtered_packages.append(p)

    if not filtered_packages:
        return _empty_response(req, "NO_PACKAGES_AFTER_DISTANCE_FILTER")

    if _debug_optimize():
        _debug_log(
            f"packages after distance filter: {len(filtered_packages)} "
            f"(from {len(packages)} input packages, max_distance_km={max_distance_km})"
        )

    # =========================================================
    # שלב 2.2 – קיצוץ רשימת מועמדים ראשונית (כדי לשמור על פשטות/ביצועים)
    # נשתמש ב-topK לפי score.
    # =========================================================
    # ל-beam כדאי לתת קצת מרחב מעבר ל-max_stops
    top_k = min(len(filtered_packages), max_stops * 500)
    candidates = sorted(
        filtered_packages, key=lambda p: score_by_id[str(p["id"])], reverse=True
    )[:top_k]

    # מפות אינדקסים מהירות לבניית תת-מטריצה
    full_node_ids = ["start"] + [f"pkg:{p['id']}" for p in packages]
    if end is not None:
        full_node_ids.append("end")
    full_index_by_node_id = {nid: idx for idx, nid in enumerate(full_node_ids)}

    # =========================================================
    # שלב 3 – יצירת כמה קבוצות מועמדות (Beam Search) תחת משקל/נפח/max_stops
    # =========================================================
    beam_width = int(c.get("beam_width", 30)) if isinstance(c, dict) else 30
    expand_width = int(c.get("expand_width", 25)) if isinstance(c, dict) else 25

    if _debug_optimize():
        _debug_log(
            f"beam params: beam_width={beam_width} expand_width={expand_width} "
            f"max_stops={max_stops} top_k={top_k} beam_input_candidates={len(candidates)}"
        )

    candidate_sets = beam_select_package_sets(
        candidates,
        max_weight=max_weight,
        max_volume=max_volume,
        max_stops=max_stops,
        score_by_id=score_by_id,
        beam_width=beam_width,
        expand_width=expand_width,
        max_steps=max_stops,
    )

    if not candidate_sets:
        return _empty_response(req, "NO_CANDIDATE_SETS_AFTER_BEAM")

    # =========================================================
    # שלב 3.1 – קיצוץ קשיח של רשימת המועמדים הסופית לפני בדיקת מסלול
    # beam_select_package_sets מחזיר כבר ממוין יורד לפי ציון.
    # =========================================================
    total_candidates_before_cap = len(candidate_sets)
    candidate_sets = candidate_sets[:MAX_FINAL_CANDIDATE_SETS]

    if _debug_optimize():
        _debug_log(f"Beam generated {total_candidates_before_cap} candidate sets before cap")
        _debug_log(f"Final candidate sets after cap: {len(candidate_sets)}")
        _debug_log(f"Final candidate cap value: {MAX_FINAL_CANDIDATE_SETS}")

    # =========================================================
    # שלב 4 – בדיקת מסלול אמיתי לכל מועמד לפי מטריצה (start=0)
    # נבדוק בסדר יורד של TOTAL_SCORE (beam כבר מחזיר כך).
    # =========================================================
    checked = 0
    for selected in candidate_sets:
        checked += 1

        selected_ids = [str(p["id"]) for p in selected]
        total_score = sum(score_by_id.get(pid, 0.0) for pid in selected_ids)
        total_profit = sum(float(p.get("profit", 0.0)) for p in selected)

        # בונים node_ids בסדר יציב (start + לפי הסדר הנוכחי ב-selected)
        node_ids = ["start"] + [f"pkg:{p['id']}" for p in selected]
        if end is not None:
            node_ids.append("end")

        # אינדקסים לתת-מטריצה מתוך full_dist
        try:
            idxs = [full_index_by_node_id[n] for n in node_ids]
        except KeyError:
            if _debug_optimize():
                _debug_log(
                    f"route candidate #{checked}: skipped (missing node index), "
                    f"packages={len(selected_ids)} ids={selected_ids}"
                )
            continue

        sub_dist = [[full_dist[i][j] for j in idxs] for i in idxs]

        end_idx = len(node_ids) - 1 if end is not None else None
        route_local, route_cost = build_route(sub_dist, start_idx=0, end_idx=end_idx)

        passed_distance = route_cost <= max_distance_km
        if _debug_optimize():
            _debug_log(
                f"route candidate #{checked}: packages={len(selected_ids)} "
                f"score={total_score:.4f} profit={total_profit:.2f} "
                f"route_distance_km={route_cost:.4f} max_distance_km={max_distance_km} "
                f"passed={passed_distance} ids={selected_ids}"
            )

        if not passed_distance:
            continue

        # =====================================================
        # עבר מרחק – בונים route_nodes/stops/totals ומחזירים
        # =====================================================
        route_nodes = [node_ids[i] for i in route_local]

        node_point = {"start": (float(req["start"]["lat"]), float(req["start"]["lon"]))}
        for p in selected:
            node_point[f"pkg:{p['id']}"] = (p["lat"], p["lon"])
        if end is not None:
            node_point["end"] = (float(end["lat"]), float(end["lon"]))

        pkg_by_node = {f"pkg:{p['id']}": p for p in selected}
        location_address_by_node = {"start": start_address}
        if end is not None:
            location_address_by_node["end"] = end_address

        # dist_by_edge על כל המטריצה (כמו שהיה אצלך) כדי ש-build_route_stops יעבוד
        dist_by_edge = {
            (full_node_ids[i], full_node_ids[j]): full_dist[i][j]
            for i in range(len(full_node_ids))
            for j in range(len(full_node_ids))
            if i != j
        }

        stops = build_route_stops(
            route_node_ids=route_nodes,
            node_point_by_id=node_point,
            package_by_node_id=pkg_by_node,
            dist_km_by_edge=dist_by_edge,
            location_address_by_node_id=location_address_by_node,
        )

        totals = {
            "total_distance_km": float(route_cost),
            "total_weight": sum(float(p.get("weight", 0.0)) for p in selected),
            "total_volume": sum(float(p.get("volume", 0.0)) for p in selected),
            "total_profit": float(total_profit),
            "total_score": float(total_score),
            "checked_candidates": int(checked),
        }

        if _debug_optimize():
            _debug_log(f"final checked_candidates={checked} (accepted candidate #{checked})")

        return {
            "selected_package_ids": selected_ids,
            "route_node_ids": route_nodes,
            "route_stops": stops,
            "totals": totals,
        }

    if _debug_optimize():
        _debug_log(f"final checked_candidates={checked} (no route within distance)")

    return _empty_response(req, "NO_ROUTE_WITHIN_DISTANCE")


def _empty_response(req: dict, message: str) -> dict:
    s = req["start"]
    return {
        "selected_package_ids": [],
        "route_node_ids": ["start"],
        "route_stops": [
            {
                "seq": 0,
                "type": "start",
                "lat": float(s["lat"]),
                "lon": float(s["lon"]),
                "Address": req.get("start_address"),
            }
        ],
        "totals": {
            "total_distance_km": 0.0,
            "total_weight": 0.0,
            "total_volume": 0.0,
            "total_profit": 0.0,
        },
        "message": message,
    }
