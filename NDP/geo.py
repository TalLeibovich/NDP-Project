"""
geo.py
Helpers for geocoding and ORS distance matrix.
"""

from __future__ import annotations
import json
import logging
import os
from urllib.parse import urlencode
import requests
from typing import Any, List, Tuple, Dict, Optional

logger = logging.getLogger(__name__)


# =========================================================
# ORS KEY
# =========================================================
def load_ors_api_key() -> str:
    """Load ORS_API_KEY from environment."""
    key = os.getenv("ORS_API_KEY")
    if not key:
        raise RuntimeError("ORS_API_KEY is not set")
    return key


# =========================================================
# ADDRESS NORMALIZATION
# =========================================================
def normalize_and_validate_address(address: str) -> str:
    """Normalize and validate address string."""
    if not address or not isinstance(address, str):
        raise ValueError("Invalid address")
    return " ".join(address.strip().split())


# =========================================================
# GEOCODING
# =========================================================
class GeocodingError(Exception):
    """Raised for geocoding failures with a meaningful API status."""

    def __init__(self, message: str, status_code: int = 502, code: str = "GEOCODING_FAILED"):
        super().__init__(message)
        self.status_code = status_code
        self.code = code


def _is_debug_mode() -> bool:
    return os.getenv("GEOCODE_DEBUG", "").strip().lower() in {"1", "true", "yes", "on"}


def _load_google_geocoding_api_key() -> str:
    key = os.getenv("GOOGLE_GEOCODING_API_KEY")
    if not key:
        raise GeocodingError(
            "GOOGLE_GEOCODING_API_KEY is not set",
            status_code=502,
            code="GOOGLE_GEOCODER_MISSING_KEY",
        )
    return key


def _build_response(
    *,
    address: str,
    normalized_address: str,
    formatted_address: str,
    lat: Optional[float],
    lon: Optional[float],
    provider: str,
    confidence: Optional[float],
    precision: str,
    is_fallback: bool,
    warning_codes: List[str],
    provider_metadata: Dict[str, Any],
    provider_raw_response: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    unique_warnings = list(dict.fromkeys(warning_codes))
    result: Dict[str, Any] = {
        "lat": float(lat) if lat is not None else None,
        "lon": float(lon) if lon is not None else None,
        "input_address": address,
        "formatted_address": formatted_address,
        "provider": provider,
        "confidence": confidence,
        "precision": precision,
        "is_fallback": is_fallback,
        "valid_for_optimization": len(unique_warnings) == 0,
        "warning_codes": unique_warnings,
        "provider_metadata": provider_metadata,
    }
    if _is_debug_mode() and provider_raw_response is not None:
        result["provider_raw_response"] = provider_raw_response
    return result


def _google_confidence_for_result(location_type: str, types: List[str]) -> float:
    location_type_upper = location_type.upper()
    types_set = {str(t).lower() for t in types}
    if location_type_upper == "ROOFTOP" and "street_address" in types_set:
        return 1.0
    if location_type_upper in {"RANGE_INTERPOLATED"} and "street_address" in types_set:
        return 0.75
    if location_type_upper in {"GEOMETRIC_CENTER"}:
        return 0.5
    if location_type_upper in {"APPROXIMATE"}:
        return 0.3
    return 0.4


def _geocode_with_google(address: str, normalized_address: str) -> Dict[str, Any]:
    api_key = _load_google_geocoding_api_key()
    url = "https://maps.googleapis.com/maps/api/geocode/json"
    params = {"address": normalized_address, "key": api_key}

    if _is_debug_mode():
        query_for_log = urlencode({"address": normalized_address, "key": "***"})
        logger.debug("Google geocode request: GET %s?%s", url, query_for_log)

    try:
        response = requests.get(url, params=params, timeout=10)
    except requests.RequestException as exc:
        raise GeocodingError(
            f"Failed to call Google geocoder: {exc}",
            status_code=502,
            code="GOOGLE_GEOCODER_UNAVAILABLE",
        ) from exc

    if _is_debug_mode():
        logger.debug("Google geocode response status=%s body=%s", response.status_code, response.text)

    if response.status_code != 200:
        raise GeocodingError(
            f"Google geocoding failed with HTTP {response.status_code}",
            status_code=502,
            code="GOOGLE_GEOCODER_HTTP_ERROR",
        )

    try:
        data = response.json()
    except json.JSONDecodeError as exc:
        raise GeocodingError("Google geocoder returned invalid JSON", status_code=502, code="GOOGLE_GEOCODER_INVALID_JSON") from exc

    status = str(data.get("status") or "")
    if status == "ZERO_RESULTS":
        return _build_response(
            address=address,
            normalized_address=normalized_address,
            formatted_address=normalized_address,
            lat=None,
            lon=None,
            provider="google",
            confidence=0.0,
            precision="no_result",
            is_fallback=False,
            warning_codes=["ADDRESS_NOT_FOUND", "LOW_GEOCODE_PRECISION"],
            provider_metadata={"provider_status": status},
            provider_raw_response=data,
        )
    if status != "OK":
        raise GeocodingError(
            f"Google geocoding status: {status or 'UNKNOWN'}",
            status_code=502,
            code="GOOGLE_GEOCODER_STATUS_ERROR",
        )

    results = data.get("results", [])
    if not results:
        raise GeocodingError("No geocoding result", status_code=404, code="ADDRESS_NOT_FOUND")

    best = results[0]
    geometry = best.get("geometry", {})
    location = geometry.get("location", {})
    lat = location.get("lat")
    lon = location.get("lng")
    if lat is None or lon is None:
        raise GeocodingError("Missing coordinates in Google geocoding response", status_code=502, code="GOOGLE_GEOCODER_BAD_PAYLOAD")

    location_type = str(geometry.get("location_type") or "UNKNOWN")
    result_types = list(best.get("types", []))
    confidence = _google_confidence_for_result(location_type, result_types)
    precision = location_type.lower()
    warning_codes: List[str] = []
    types_set = {str(t).lower() for t in result_types}
    if location_type.upper() != "ROOFTOP":
        warning_codes.append("LOW_GEOCODE_PRECISION")
    if "street_address" not in types_set:
        warning_codes.append("GEOCODE_FALLBACK_RESULT")
    if location_type.upper() in {"APPROXIMATE", "GEOMETRIC_CENTER"}:
        warning_codes.append("LOW_GEOCODE_CONFIDENCE")

    provider_metadata = {
        "provider_place_id": best.get("place_id"),
        "provider_location_type": location_type,
        "provider_types": result_types,
        "provider_partial_match": bool(best.get("partial_match", False)),
    }
    if provider_metadata["provider_partial_match"]:
        warning_codes.append("ADDRESS_MISMATCH")

    return _build_response(
        address=address,
        normalized_address=normalized_address,
        formatted_address=str(best.get("formatted_address") or normalized_address),
        lat=float(lat),
        lon=float(lon),
        provider="google",
        confidence=confidence,
        precision=precision,
        is_fallback=False,
        warning_codes=warning_codes,
        provider_metadata=provider_metadata,
        provider_raw_response=data,
    )


def geocode_address_details(address: str) -> Dict[str, Any]:
    """Geocode address using Google Geocoding."""
    normalized_address = normalize_and_validate_address(address)
    return _geocode_with_google(address, normalized_address)


# =========================================================
# SNAP TO ROAD NETWORK
# =========================================================
def snap_points_to_road(
    locations: List[List[float]],
    radius: int = 350,
) -> Tuple[List[List[float]], List[int]]:
    """
    Snap points to nearest road using ORS Snap API.

    Args:
        locations: List of [lon, lat] coordinates
        radius: Search radius in meters (default 350m)

    Returns:
        - snapped_locations: List of [lon, lat] with snapped coordinates
        - failed_indices: List of indices that failed to snap
    """
    if not locations:
        return [], []

    api_key = load_ors_api_key()
    url = "https://api.openrouteservice.org/v2/snap/driving-car"

    payload = {
        "locations": locations,
        "radius": radius,
    }

    headers = {
        "Authorization": api_key,
        "Content-Type": "application/json",
    }

    logger.info("Calling ORS Snap API with %d points (radius=%dm)", len(locations), radius)

    try:
        r = requests.post(url, json=payload, headers=headers, timeout=30)

        if r.status_code != 200:
            logger.warning(
                "ORS Snap API failed: status=%s; falling back to original coordinates",
                r.status_code,
            )
            return locations, []

        data = r.json()
        snapped_locations = data.get("locations", [])

        if not snapped_locations or len(snapped_locations) != len(locations):
            logger.warning("ORS Snap API returned invalid response; falling back to original coordinates")
            return locations, []

        result_locations: List[List[float]] = []
        failed_indices: List[int] = []

        for i, snap_result in enumerate(snapped_locations):
            if snap_result is None or snap_result.get("location") is None:
                logger.warning(
                    "Point %d failed to snap at [%.6f, %.6f]",
                    i,
                    locations[i][0],
                    locations[i][1],
                )
                result_locations.append(locations[i])
                failed_indices.append(i)
            else:
                snapped_coord = snap_result["location"]
                snapped_distance = snap_result.get("snapped_distance", 0)
                result_locations.append(snapped_coord)

                if snapped_distance > 50:
                    logger.debug("Point %d snapped %.1fm from original", i, snapped_distance)

        success_count = len(locations) - len(failed_indices)
        logger.info("Snapped %d/%d points to road network", success_count, len(locations))

        if failed_indices:
            logger.warning("Snap failed for indices (using original coords): %s", failed_indices)

        return result_locations, failed_indices

    except Exception as exc:
        logger.warning("Exception during ORS snap: %s; falling back to original coordinates", exc)
        return locations, []


# =========================================================
# DISTANCE MATRIX (CORE)
# =========================================================
INF_DISTANCE = 9999.0


def build_ors_distance_matrix(
    points: List[Tuple[float, float]],
) -> List[List[float]]:
    """
    Build directed distance matrix from ORS Matrix API.

    Points are first snapped to the road network, then the snapped coordinates
    are used for the Matrix API call. ORS returns distances in meters; converted to km.
    """
    api_key = load_ors_api_key()

    locations = [[lon, lat] for lat, lon in points]

    snapped_locations, failed_indices = snap_points_to_road(locations)

    url = "https://api.openrouteservice.org/v2/matrix/driving-car"

    payload = {
        "locations": snapped_locations,
        "metrics": ["distance"],
        "resolve_locations": True,
    }

    headers = {
        "Authorization": api_key,
        "Content-Type": "application/json",
    }

    logger.info("Calling ORS Matrix API with %d snapped points", len(snapped_locations))

    r = requests.post(url, json=payload, headers=headers, timeout=30)
    if r.status_code != 200:
        raise RuntimeError(f"ORS matrix request failed: {r.status_code} {r.text}")

    data = r.json()

    if "distances" not in data:
        raise RuntimeError(f"ORS matrix response invalid: {data}")

    raw = data["distances"]

    matrix: List[List[float]] = []
    null_count = 0
    for row in raw:
        processed_row = []
        for v in row:
            if v is None:
                processed_row.append(INF_DISTANCE)
                null_count += 1
            else:
                processed_row.append(float(v) / 1000.0)
        matrix.append(processed_row)

    if null_count > 0:
        logger.warning(
            "%d null distances replaced with %s (unreachable pairs)",
            null_count,
            INF_DISTANCE,
        )
    elif failed_indices:
        logger.warning("Matrix built with %d unsnapped point(s)", len(failed_indices))

    return matrix
