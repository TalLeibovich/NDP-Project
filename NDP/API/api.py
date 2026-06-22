"""
api.py

FastAPI server for NDP Route Optimizer.
Updated to use file-based data layer with entities and per-run ORS matrices.
"""

from __future__ import annotations
from dotenv import load_dotenv
load_dotenv()

import logging
import os
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from fastapi import FastAPI, HTTPException, Body, Query
from fastapi.middleware.cors import CORSMiddleware

# Import existing algorithm core (unchanged)
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from pipeline import optimize_for_courier
from geo import GeocodingError, geocode_address_details, build_ors_distance_matrix

# Import new entities and repositories
from API.entities import Company, Courier, Package
from API.repositories import (
    CompanyRepository,
    CourierRepository,
    PackageRepository,
    RunRepository,
    DistanceMatrixRepository,
    optimization_lock_expires_at,
)


logger = logging.getLogger(__name__)

app = FastAPI(title="NDP Route Optimizer API", version="2.0")

DEFAULT_ALLOWED_ORIGINS = [
    "https://ndp-optimizer.web.app",
    "https://ndp-optimizer.firebaseapp.com",
    "http://localhost:3000",
    "http://127.0.0.1:3000",
    "http://localhost:5173",
    "http://127.0.0.1:5173",
]
configured_origins = os.getenv("ALLOWED_ORIGINS", "")
if configured_origins.strip():
    allowed_origins = [origin.strip() for origin in configured_origins.split(",") if origin.strip()]
else:
    allowed_origins = DEFAULT_ALLOWED_ORIGINS

app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins,
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


# Initialize repositories
company_repo = CompanyRepository()
courier_repo = CourierRepository()


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _find_run_across_companies(run_id: str) -> tuple[str, Dict[str, Any]]:
    for company in company_repo.list_all():
        run_repo = RunRepository(company.company_id)
        run_data = run_repo.get_run(run_id)
        if run_data:
            return company.company_id, run_data
    raise HTTPException(status_code=404, detail="Run not found")


def _has_coords(location: Dict[str, Any]) -> bool:
    return (
        "lat" in location
        and "lon" in location
        and location.get("lat") is not None
        and location.get("lon") is not None
    )


def resolve_location(
    location: Any,
    *,
    field_name: str,
    require_valid_for_optimization: bool = True,
) -> Dict[str, Any]:
    """
    Resolve location from either coordinates or address.
    Returns {lat, lon, address, formatted_address, geocode_result?}.
    """
    if not isinstance(location, dict):
        raise HTTPException(status_code=400, detail=f"{field_name} must be an object")

    has_coords = _has_coords(location)
    has_address = isinstance(location.get("address"), str) and bool(location.get("address", "").strip())

    if has_coords:
        try:
            lat = float(location["lat"])
            lon = float(location["lon"])
        except (TypeError, ValueError) as exc:
            raise HTTPException(status_code=400, detail=f"{field_name}.lat and {field_name}.lon must be numeric") from exc
        return {
            "lat": lat,
            "lon": lon,
            "address": location.get("address"),
            "formatted_address": location.get("formatted_address"),
            "geocode_result": None,
        }

    if has_address:
        address = location["address"].strip()
        try:
            geocode = geocode_address_details(address)
        except GeocodingError as exc:
            raise HTTPException(
                status_code=422,
                detail={
                    "code": "ADDRESS_GEOCODING_FAILED",
                    "field": field_name,
                    "message": str(exc),
                },
            ) from exc
        if require_valid_for_optimization and not geocode.get("valid_for_optimization", False):
            raise HTTPException(
                status_code=422,
                detail={
                    "code": "LOW_GEOCODE_QUALITY",
                    "field": field_name,
                    "message": "Address was not geocoded accurately enough",
                    "warning_codes": geocode.get("warning_codes", []),
                    "geocode": geocode,
                },
            )
        return {
            "lat": float(geocode["lat"]),
            "lon": float(geocode["lon"]),
            "address": address,
            "formatted_address": geocode.get("formatted_address"),
            "geocode_result": geocode,
        }

    if ("lat" in location) ^ ("lon" in location):
        raise HTTPException(status_code=400, detail=f"{field_name} requires both lat and lon together")
    raise HTTPException(status_code=400, detail=f"{field_name} requires either lat/lon or address")


def _validate_package_payload(item: Dict[str, Any], *, field_prefix: str) -> None:
    required_fields = ["company_id", "weight", "volume", "profit", "deadline"]
    missing = [field for field in required_fields if field not in item or item.get(field) in (None, "")]
    if missing:
        raise HTTPException(
            status_code=400,
            detail={
                "code": "PACKAGE_VALIDATION_ERROR",
                "field": field_prefix,
                "message": f"Missing required fields: {', '.join(missing)}",
                "missing_fields": missing,
            },
        )

    numeric_fields = ["weight", "volume", "profit"]
    numeric_values: Dict[str, float] = {}
    for field in numeric_fields:
        try:
            numeric_values[field] = float(item[field])
        except (TypeError, ValueError) as exc:
            raise HTTPException(
                status_code=400,
                detail={
                    "code": "PACKAGE_VALIDATION_ERROR",
                    "field": field_prefix,
                    "message": f"{field} must be numeric",
                },
            ) from exc

    negative = [field for field, value in numeric_values.items() if value < 0]
    if negative:
        raise HTTPException(
            status_code=400,
            detail={
                "code": "PACKAGE_VALIDATION_ERROR",
                "field": field_prefix,
                "message": f"Negative values are not allowed: {', '.join(negative)}",
                "invalid_fields": negative,
            },
        )


# =========================================================
# DISTANCE PROVIDER (uses per-run matrix file)
# =========================================================
class FileDistanceProvider:
    def __init__(self, matrix: List[List[float]]):
        self.matrix = matrix

    def distance_matrix_km(self, points: List[Any]) -> List[List[float]]:
        """Returns the precomputed matrix (ignores points arg)"""
        n = len(points)
        if n > len(self.matrix):
            raise ValueError(f"Matrix too small: has {len(self.matrix)} but need {n}")
        return [row[:n] for row in self.matrix[:n]]


# =========================================================
# MATRIX GENERATION (per run, with index_map)
# =========================================================
def generate_ors_matrix_for_run(
    run_id: str,
    company_id: str,
    start: Dict[str, float],
    packages: List[Package],
    end: Optional[Dict[str, float]] = None,
) -> Tuple[List[List[float]], Dict[str, str], str]:
    """
    Generate ORS distance matrix for a run.

    Returns:
        - matrix: directed distance matrix (dist[i][j] = i->j in km), may contain None for unreachable pairs
        - index_map: {"0": "start", "1": "P01", ..., "N": "end"}
        - matrix_path: Firestore path string (for compatibility)
    """
    # IMPORTANT: Internal model uses (lat, lon). ORS boundary will convert to [lon, lat].
    # 1) Build ordered points list: [START, P01, P02, ..., END?]
    points: List[tuple[float, float]] = [(float(start["lat"]), float(start["lon"]))]  # (lat, lon)

    # Sort packages by ID (consistent ordering rule)
    packages_sorted = sorted(packages, key=lambda p: p.id)

    for pkg in packages_sorted:
        points.append((float(pkg.lat), float(pkg.lon)))  # (lat, lon)

    if end is not None:
        points.append((float(end["lat"]), float(end["lon"])))  # (lat, lon)

    # 2) Build index_map
    index_map: Dict[str, str] = {"0": "start"}
    for i, pkg in enumerate(packages_sorted, start=1):
        index_map[str(i)] = pkg.id
    if end is not None:
        index_map[str(len(points) - 1)] = "end"

    # 3) Call ORS Matrix API
    logger.info("Generating ORS matrix for run %s with %d points", run_id, len(points))
    matrix = build_ors_distance_matrix(points)
    matrix = [
        [float("inf") if cell is None else cell for cell in row]
        for row in matrix
    ]

    # 4) Save matrix to Firestore
    matrix_repo = DistanceMatrixRepository(company_id)
    matrix_path = matrix_repo.save_matrix(run_id, matrix)
    logger.info("Saved matrix for run %s to %s", run_id, matrix_path)

    return matrix, index_map, matrix_path

# =========================================================
# ENDPOINTS
# =========================================================

# -------------------------
# GEO / GEOCODING
# -------------------------
@app.post("/geo/geocode")
def geo_geocode(body: Dict[str, Any] = Body(...)) -> Dict[str, Any]:
    """Geocode address with backward-compatible top-level lat/lon."""
    address = body.get("address")
    if not isinstance(address, str) or not address.strip():
        raise HTTPException(status_code=400, detail="address is required")
    try:
        result = geocode_address_details(address)
    except GeocodingError as exc:
        raise HTTPException(
            status_code=exc.status_code,
            detail={"code": exc.code, "message": str(exc), "is_fallback": False},
        ) from exc
    except ValueError as exc:
        raise HTTPException(status_code=400, detail={"code": "INVALID_ADDRESS", "message": str(exc)}) from exc
    except Exception as exc:
        raise HTTPException(
            status_code=500,
            detail={"code": "GEOCODING_INTERNAL_ERROR", "message": str(exc), "is_fallback": False},
        ) from exc
    return result


# -------------------------
# COMPANIES
# -------------------------
@app.get("/companies")
def list_companies() -> List[Dict[str, Any]]:
    """List all companies"""
    companies = company_repo.list_all()
    return [c.to_dict() for c in companies]


@app.post("/companies")
def create_company(body: Dict[str, Any] = Body(...)) -> Dict[str, Any]:
    """Create a new company"""
    company = Company.from_dict(body)
    
    # Check if exists
    if company_repo.get_by_id(company.company_id):
        raise HTTPException(status_code=409, detail="Company already exists")
    
    company_repo.save(company)
    return company.to_dict()


# -------------------------
# COURIERS
# -------------------------
@app.get("/couriers")
def list_couriers(company_id: Optional[str] = Query(None)) -> List[Dict[str, Any]]:
    """List couriers, optionally filtered by company_id"""
    couriers = courier_repo.list_all(company_id=company_id)
    return [c.to_dict() for c in couriers]


@app.post("/couriers")
def create_courier(body: Dict[str, Any] = Body(...)) -> Dict[str, Any]:
    """Create a new courier"""
    courier = Courier.from_dict(body)
    
    # Validate company exists
    if not company_repo.get_by_id(courier.company_id):
        raise HTTPException(status_code=404, detail="Company not found")
    
    # Check if exists
    if courier_repo.get_by_id(courier.courier_id):
        raise HTTPException(status_code=409, detail="Courier already exists")
    
    courier_repo.save(courier)
    return courier.to_dict()


# -------------------------
# PACKAGES
# -------------------------
@app.get("/packages")
def get_packages(
    company_id: str = Query(..., description="Company ID"),
    delivered: Optional[int] = Query(None, description="0/1 filter"),
) -> List[Dict[str, Any]]:
    """Get packages for a company"""
    # Validate company
    if not company_repo.get_by_id(company_id):
        raise HTTPException(status_code=404, detail="Company not found")
    
    pkg_repo = PackageRepository(company_id)
    packages = pkg_repo.list_all(delivered=delivered)
    return [p.to_dict() for p in packages]


@app.post("/packages")
def create_packages(body: Any = Body(...)) -> Dict[str, Any]:
    """Create package(s) for a company"""
    # Body can be single package or list
    if isinstance(body, dict):
        items = [body]
    elif isinstance(body, list):
        items = body
    else:
        raise HTTPException(status_code=400, detail="Body must be object or list")
    
    if not items:
        raise HTTPException(status_code=400, detail="No packages provided")
    
    # All packages must have company_id and belong to same company
    company_id = items[0].get("company_id")
    if not company_id:
        raise HTTPException(status_code=400, detail="company_id is required")
    
    # Validate company exists
    if not company_repo.get_by_id(company_id):
        raise HTTPException(status_code=404, detail="Company not found")
    
    pkg_repo = PackageRepository(company_id)
    created_ids = []
    
    for item in items:
        if item.get("company_id") != company_id:
            raise HTTPException(status_code=400, detail="All packages must have same company_id")

        item_data = dict(item)
        _validate_package_payload(
            item_data,
            field_prefix=f"package[{item_data.get('id', '?')}]",
        )
        location_data = resolve_location(
            {
                "lat": item_data.get("lat"),
                "lon": item_data.get("lon"),
                "address": item_data.get("address"),
                "formatted_address": item_data.get("formatted_address"),
            },
            field_name=f"package[{item_data.get('id', '?')}]",
            require_valid_for_optimization=True,
        )
        item_data["lat"] = location_data["lat"]
        item_data["lon"] = location_data["lon"]
        if location_data.get("address") is not None:
            item_data["address"] = location_data.get("address")
        if location_data.get("formatted_address"):
            item_data["formatted_address"] = location_data.get("formatted_address")

        pkg = Package.from_dict(item_data)
        
        # Check if exists
        if pkg_repo.get_by_id(pkg.id):
            raise HTTPException(status_code=409, detail=f"Package {pkg.id} already exists")
        
        pkg_repo.save(pkg)
        created_ids.append(pkg.id)
    
    return {"company_id": company_id, "created_ids": created_ids, "count": len(created_ids)}


@app.put("/packages/{package_id}")
def update_package(
    package_id: str,
    body: Dict[str, Any] = Body(...),
) -> Dict[str, Any]:
    """Update a package (cannot change delivered status - use courier delivery endpoint)"""
    company_id = body.get("company_id")
    if not company_id:
        raise HTTPException(status_code=400, detail="company_id is required")
    
    # Validate company
    if not company_repo.get_by_id(company_id):
        raise HTTPException(status_code=404, detail="Company not found")
    
    pkg_repo = PackageRepository(company_id)
    pkg = pkg_repo.get_by_id(package_id)
    
    if not pkg:
        raise HTTPException(status_code=404, detail="Package not found")

    candidate_payload = pkg.to_dict()
    candidate_payload.update(body)
    _validate_package_payload(candidate_payload, field_prefix=f"package[{package_id}]")
    
    # Resolve optional location update from either coordinates or address.
    has_lat = "lat" in body
    has_lon = "lon" in body
    has_address = "address" in body
    if has_address and not (has_lat and has_lon):
        location_data = resolve_location(
            {
                "lat": body.get("lat"),
                "lon": body.get("lon"),
                "address": body.get("address"),
                "formatted_address": body.get("formatted_address"),
            },
            field_name=f"package[{package_id}]",
            require_valid_for_optimization=True,
        )
        pkg.lat = float(location_data["lat"])
        pkg.lon = float(location_data["lon"])
        pkg.address = location_data.get("address")
        pkg.formatted_address = location_data.get("formatted_address")
    elif has_lat != has_lon:
        raise HTTPException(status_code=400, detail="lat and lon must be provided together")
    elif has_address and has_lat and has_lon and "formatted_address" not in body:
        # Prevent stale formatted_address when address text changes alongside manual coords.
        pkg.formatted_address = None

    # Update fields (delivered is NOT allowed here - use courier delivery endpoint)
    for k in ["lat", "lon", "weight", "volume", "profit", "deadline", "address", "formatted_address"]:
        if k in body:
            if k in ["lat", "lon", "weight", "volume", "profit"]:
                setattr(pkg, k, float(body[k]))
            else:
                setattr(pkg, k, body[k])
    
    pkg_repo.save(pkg)
    return pkg.to_dict()


# -------------------------
# COURIER DELIVERY STATUS (ONLY way to mark packages delivered)
# -------------------------
@app.post("/couriers/{courier_id}/deliveries")
def update_delivery_status(
    courier_id: str,
    body: Dict[str, Any] = Body(...),
) -> Dict[str, Any]:
    """
    Mark a package as delivered or undelivered.
    This is the ONLY endpoint that can change package.delivered status.
    
    Request body:
    {
      "package_id": "P01",
      "delivered": true  (or false)
    }
    """
    package_id = body.get("package_id")
    delivered = body.get("delivered")
    
    if not package_id:
        raise HTTPException(status_code=400, detail="package_id is required")
    if delivered is None:
        raise HTTPException(status_code=400, detail="delivered is required (true or false)")
    
    # Validate courier exists
    courier = courier_repo.get_by_id(courier_id)
    if not courier:
        raise HTTPException(status_code=404, detail="Courier not found")
    
    # Get the courier's company
    company_id = courier.company_id
    
    # Load package from the courier's company
    pkg_repo = PackageRepository(company_id)
    pkg = pkg_repo.get_by_id(package_id)
    
    if not pkg:
        raise HTTPException(status_code=404, detail="Package not found")
    
    # Validate package belongs to same company as courier
    if pkg.company_id != company_id:
        raise HTTPException(status_code=403, detail="Package does not belong to courier's company")

    if delivered and pkg.assigned_to is not None and pkg.assigned_to != courier_id:
        raise HTTPException(status_code=403, detail="Package not assigned to this courier")

    if delivered and pkg.delivered:
        logger.info("Package %s already delivered for courier %s", package_id, courier_id)
        return {
            "courier_id": courier_id,
            "package_id": package_id,
            "delivered": bool(pkg.delivered),
            "company_id": company_id,
        }
    
    # Update delivered status
    pkg.delivered = 1 if delivered else 0
    if delivered:
        pkg.in_delivery = False
        pkg.assigned_to = None
        pkg.assigned_run_id = None
        pkg.assigned_at = None
        pkg.in_delivery_expires_at = None
        pkg.optimization_lock_run_id = None
        if not pkg.delivered_at:
            pkg.delivered_at = _now_iso()
    pkg_repo.save(pkg)
    logger.info(
        "Updated delivery status: package=%s courier=%s delivered=%s",
        package_id,
        courier_id,
        bool(pkg.delivered),
    )
    
    return {
        "courier_id": courier_id,
        "package_id": package_id,
        "delivered": bool(pkg.delivered),
        "company_id": company_id,
    }


# -------------------------
# OPTIMIZE
# -------------------------
@app.post("/optimize")
def optimize(body: Dict[str, Any] = Body(...)) -> Dict[str, Any]:
    """
    Run route optimization for a courier.
    
    Request body:
    {
      "company_id": "C1",
      "courier_id": "K1",
      "start": {"lat": 32.0, "lon": 34.7},
      "end": {"lat": 32.0, "lon": 34.7} (optional),
      "constraints": {
        "max_distance_km": 50,
        "max_weight": 100,
        "max_volume": 100,
        "max_stops": 20
      },
      "service_date": "2026-01-17" (optional)
    }
    """
    # Extract and validate inputs
    company_id = body.get("company_id")
    courier_id = body.get("courier_id")
    start_raw = body.get("start")
    end_raw = body.get("end")  # optional
    constraints = body.get("constraints", {})
    
    if not company_id:
        raise HTTPException(status_code=400, detail="company_id is required")
    if not courier_id:
        raise HTTPException(status_code=400, detail="courier_id is required")
    if not start_raw:
        raise HTTPException(status_code=400, detail="start is required")

    start_location = resolve_location(start_raw, field_name="start", require_valid_for_optimization=True)
    start = {"lat": float(start_location["lat"]), "lon": float(start_location["lon"])}
    end = None
    end_location: Optional[Dict[str, Any]] = None
    if end_raw is not None:
        end_location = resolve_location(end_raw, field_name="end", require_valid_for_optimization=True)
        end = {"lat": float(end_location["lat"]), "lon": float(end_location["lon"])}
    
    # Validate company and courier
    company = company_repo.get_by_id(company_id)
    if not company:
        raise HTTPException(status_code=404, detail="Company not found")
    
    courier = courier_repo.get_by_id(courier_id)
    if not courier:
        raise HTTPException(status_code=404, detail="Courier not found")
    
    if courier.company_id != company_id:
        raise HTTPException(status_code=400, detail="Courier does not belong to company")
    
    # Load packages (only delivered=0 and not currently assigned)
    pkg_repo = PackageRepository(company_id)
    packages_all = pkg_repo.list_available()
    
    if not packages_all:
        return {
            "run_id": None,
            "company_id": company_id,
            "courier_id": courier_id,
            "selected_package_ids": [],
            "route_node_ids": ["start"],
            "route_stops": [{
                "seq": 0,
                "type": "start",
                "lat": start["lat"],
                "lon": start["lon"],
                "Address": start_location.get("formatted_address") or start_location.get("address"),
            }],
            "totals": {
                "total_distance_km": 0.0,
                "total_weight": 0.0,
                "total_volume": 0.0,
                "total_profit": 0.0,
            },
            "message": "NO_UNDELIVERED_PACKAGES",
        }
    
    # Sort packages by ID (consistent ordering)
    packages_sorted = sorted(packages_all, key=lambda p: p.id)
    
    # Generate run_id
    run_id = str(uuid.uuid4())
    locked_package_ids: List[str] = []

    try:
        lock_expires_at = optimization_lock_expires_at()
        locked_package_ids = pkg_repo.acquire_optimization_locks(
            [p.id for p in packages_sorted],
            run_id,
            lock_expires_at,
        )
        logger.info(
            "Acquired optimization locks: run_id=%s locked_count=%d expires_at=%s",
            run_id,
            len(locked_package_ids),
            lock_expires_at,
        )

        logger.info(
            "Starting optimization run_id=%s company=%s courier=%s packages=%d",
            run_id,
            company_id,
            courier_id,
            len(packages_sorted),
        )
        
        matrix, index_map, matrix_path = generate_ors_matrix_for_run(
            run_id=run_id,
            company_id=company_id,
            start=start,
            packages=packages_sorted,
            end=end,
        )
        
        expected_size = len(packages_sorted) + 1 + (1 if end else 0)
        actual_size = len(matrix)
        
        if actual_size != expected_size:
            raise HTTPException(
                status_code=500,
                detail=f"Matrix size mismatch: expected {expected_size}x{expected_size}, got {actual_size}x{actual_size}"
            )
        
        # Create provider using the run matrix
        provider = FileDistanceProvider(matrix)
        
        # Convert packages to dict format for pipeline
        packages_for_pipeline = [p.to_dict() for p in packages_sorted]
        
        # Build request for pipeline
        req = {
            "start": start,
            "start_address": start_location.get("formatted_address") or start_location.get("address"),
            "constraints": constraints,
        }
        if end is not None:
            req["end"] = end
            req["end_address"] = (end_location or {}).get("formatted_address") or (end_location or {}).get("address")
        if "service_date" in body:
            req["service_date"] = body["service_date"]
        
        # Call optimization algorithm
        result = optimize_for_courier(req, packages_for_pipeline, provider)
        
        # Add metadata
        result["run_id"] = run_id
        result["company_id"] = company_id
        result["courier_id"] = courier_id
        result["index_map"] = index_map

        if result.get("message"):
            pkg_repo.release_optimization_locks(run_id)
            logger.info("Released all optimization locks after no solution: run_id=%s", run_id)
        else:
            selected_ids = set(result.get("selected_package_ids", []))
            unselected_ids = [pid for pid in locked_package_ids if pid not in selected_ids]
            if unselected_ids:
                released = pkg_repo.release_optimization_locks(run_id, only_package_ids=unselected_ids)
                logger.info(
                    "Released unselected optimization locks: run_id=%s released=%d kept_selected=%d",
                    run_id,
                    released,
                    len(selected_ids),
                )
        
        # Save run
        run_repo = RunRepository(company_id)
        run_data = {
            "run_id": run_id,
            "company_id": company_id,
            "courier_id": courier_id,
            "request": body,
            "matrix_path": str(matrix_path),
            "index_map": index_map,
            "response": result,
        }
        run_repo.save_run(run_id, run_data)

        totals = result.get("totals", {})
        logger.info(
            "Optimization complete: run_id=%s selected=%d distance_km=%.2f",
            run_id,
            len(result.get("selected_package_ids", [])),
            totals.get("total_distance_km", 0),
        )
        
        return result
    except HTTPException:
        if locked_package_ids:
            pkg_repo.release_optimization_locks(run_id)
            logger.info("Released all optimization locks after HTTP error: run_id=%s", run_id)
        raise
    except RuntimeError as exc:
        if locked_package_ids:
            pkg_repo.release_optimization_locks(run_id)
            logger.info("Released all optimization locks after lock failure: run_id=%s", run_id)
        raise HTTPException(status_code=409, detail=str(exc)) from exc
    except Exception:
        if locked_package_ids:
            pkg_repo.release_optimization_locks(run_id)
            logger.info("Released all optimization locks after exception: run_id=%s", run_id)
        raise


# -------------------------
# DIST-MATRIX (helper endpoint)
# -------------------------
@app.post("/dist-matrix")
def build_dist_matrix(body: Dict[str, Any] = Body(...)) -> Dict[str, Any]:
    """
    Generate ORS distance matrix for a company (helper endpoint).
    Creates a run_id and saves matrix.
    
    Request:
    {
      "company_id": "C1",
      "start": {"lat": 32.0, "lon": 34.7},
      "end": {"lat": 32.0, "lon": 34.7} (optional)
    }
    """
    company_id = body.get("company_id")
    start = body.get("start")
    end = body.get("end")
    
    if not company_id:
        raise HTTPException(status_code=400, detail="company_id is required")
    if not start:
        raise HTTPException(status_code=400, detail="start is required")
    
    # Validate company
    if not company_repo.get_by_id(company_id):
        raise HTTPException(status_code=404, detail="Company not found")
    
    # Load packages (delivered=0)
    pkg_repo = PackageRepository(company_id)
    packages = pkg_repo.list_all(delivered=0)
    
    if not packages:
        raise HTTPException(status_code=400, detail="No undelivered packages found")
    
    # Sort packages
    packages_sorted = sorted(packages, key=lambda p: p.id)
    
    # Generate run_id
    run_id = str(uuid.uuid4())
    
    # Generate matrix
    matrix, index_map, matrix_path = generate_ors_matrix_for_run(
        run_id=run_id,
        company_id=company_id,
        start=start,
        packages=packages_sorted,
        end=end,
    )
    
    return {
        "run_id": run_id,
        "company_id": company_id,
        "matrix_path": str(matrix_path),
        "matrix_size": f"{len(matrix)}x{len(matrix)}",
        "index_map": index_map,
    }


# -------------------------
# RUN LIFECYCLE
# -------------------------
@app.post("/runs/{run_id}/activate")
def activate_run(run_id: str) -> Dict[str, Any]:
    """Assign selected run packages to the run courier."""
    company_id, run_data = _find_run_across_companies(run_id)
    selected_ids = list(run_data.get("response", {}).get("selected_package_ids", []))
    courier_id = run_data.get("courier_id")

    if not courier_id:
        raise HTTPException(status_code=400, detail="Run has no courier_id")
    if not selected_ids:
        return {
            "run_id": run_id,
            "company_id": company_id,
            "courier_id": courier_id,
            "assigned_count": 0,
        }

    pkg_repo = PackageRepository(company_id)
    packages: List[Package] = []
    for package_id in selected_ids:
        pkg = pkg_repo.get_by_id(package_id)
        if not pkg:
            raise HTTPException(status_code=404, detail=f"Package not found: {package_id}")
        if pkg.in_delivery:
            if pkg.assigned_run_id == run_id:
                continue
            if pkg.optimization_lock_run_id == run_id:
                packages.append(pkg)
                continue
            raise HTTPException(status_code=409, detail=f"Package already in delivery: {package_id}")
        packages.append(pkg)

    now = _now_iso()
    latest_packages: List[Package] = []
    for pkg in packages:
        latest = pkg_repo.get_by_id(pkg.id)
        if not latest:
            raise HTTPException(status_code=404, detail=f"Package not found: {pkg.id}")
        if latest.in_delivery:
            if latest.assigned_run_id == run_id:
                latest_packages.append(latest)
                continue
            if latest.optimization_lock_run_id == run_id:
                latest_packages.append(latest)
                continue
            raise HTTPException(status_code=409, detail=f"Package already in delivery: {pkg.id}")
        latest_packages.append(latest)

    for pkg in latest_packages:
        pkg.in_delivery = True
        pkg.assigned_to = courier_id
        pkg.assigned_run_id = run_id
        pkg.assigned_at = now
        pkg.in_delivery_expires_at = None
        pkg.optimization_lock_run_id = None
        pkg_repo.save(pkg)
    logger.info("Activated run: run_id=%s assigned_count=%d", run_id, len(latest_packages))

    return {
        "run_id": run_id,
        "company_id": company_id,
        "courier_id": courier_id,
        "assigned_count": len(latest_packages),
    }


@app.post("/runs/{run_id}/complete")
def complete_run(run_id: str) -> Dict[str, Any]:
    """Complete a run and release undelivered assigned packages."""
    company_id, _ = _find_run_across_companies(run_id)
    pkg_repo = PackageRepository(company_id)
    assigned_packages = pkg_repo.list_by_assigned_run(run_id)
    if not assigned_packages:
        logger.info("Completed run with no assigned packages: run_id=%s", run_id)
        return {
            "run_id": run_id,
            "company_id": company_id,
            "released_count": 0,
            "message": "NO_ASSIGNED_PACKAGES",
        }

    released = 0
    for pkg in assigned_packages:
        if pkg.in_delivery and not pkg.delivered:
            pkg.in_delivery = False
            pkg.assigned_to = None
            pkg.assigned_run_id = None
            pkg.assigned_at = None
            pkg.in_delivery_expires_at = None
            pkg.optimization_lock_run_id = None
            pkg_repo.save(pkg)
            released += 1
    logger.info("Completed run: run_id=%s released_count=%d", run_id, released)

    return {
        "run_id": run_id,
        "company_id": company_id,
        "released_count": released,
    }


@app.get("/runs/active")
def get_active_runs(company_id: str = Query(..., description="Company ID")) -> Dict[str, Any]:
    """List run IDs that currently have packages in delivery."""
    if not company_repo.get_by_id(company_id):
        raise HTTPException(status_code=404, detail="Company not found")

    pkg_repo = PackageRepository(company_id)
    active_run_ids = sorted(
        {
            p.assigned_run_id
            for p in pkg_repo.list_in_delivery()
            if p.assigned_run_id
        }
    )
    return {"company_id": company_id, "active_run_ids": active_run_ids}


# -------------------------
# HEALTH CHECK
# -------------------------
@app.get("/")
def health_check():
    return {
        "service": "NDP Route Optimizer API",
        "version": "2.0",
        "status": "ok"
    }
