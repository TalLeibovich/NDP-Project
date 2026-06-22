# NDP Route Optimizer

## 1. Project Overview

NDP Route Optimizer is a FastAPI-based backend for courier route optimization under operational constraints.  
Given a courier, delivery packages, and limits (distance, weight, volume, max stops), the system chooses a high-value subset of packages and builds a feasible delivery route.

The core problem is a practical combination of NP-hard components:
- package subset selection (Knapsack-like combinatorial search),
- route ordering (TSP-like problem on real road-network distances),
- constrained feasibility filtering.

At a high level, the architecture combines:
- **FastAPI** for service endpoints,
- **Firestore** for multi-tenant operational data and run history,
- **OpenRouteService (ORS)** for directed road distance matrices,
- a **hybrid approximation pipeline** (scoring + beam selection + MST/DFS + 2-opt).

---

## 2. System Architecture

### Core Components

- **API Layer (`API/api.py`)**  
  Exposes endpoints for companies, couriers, packages, optimization, and matrix helper operations.

- **Data Layer (`API/entities.py`, `API/repositories.py`)**  
  Implements entity models and Firestore repositories for CRUD, run persistence, and matrix storage.

- **Geospatial Layer (`geo.py`)**  
  Integrates with ORS geocoding/snap/matrix APIs and returns directed distance data.

- **Optimization Layer (`pipeline.py`, `selection.py`, `mst_tsp.py`, `routing.py`, `scoring.py`)**  
  Executes candidate scoring, constrained selection, route construction, local improvement, and output shaping.

### Data Flow (End-to-End)

1. Client calls `POST /optimize` with `company_id`, `courier_id`, `start`, and `constraints`.
2. API validates tenant context and loads undelivered packages from Firestore.
3. System builds ordered points `[start, packages..., end?]` and requests ORS matrix.
4. Matrix is sanitized, indexed, and persisted under the run document.
5. Optimization pipeline selects candidates and evaluates route feasibility.
6. Best accepted result is returned and full run metadata (request/response/index map/matrix reference) is saved.

---

## 3. Algorithm Explanation

The optimization process is implemented as a staged heuristic pipeline:

1. **Scoring (`scoring.py`)**  
   Each package gets a score derived from profit and deadline urgency.

2. **Early Filtering (`pipeline.py`)**  
   Packages are pre-filtered by start-to-package distance threshold (`max_distance_km`) to reduce search space.

3. **Matrix Generation (`API/api.py` + `geo.py`)**  
   ORS provides a directed matrix over all relevant nodes; matrix indices are aligned with a deterministic index map.

4. **Candidate Selection (`selection.py`)**  
   Beam Search generates feasible package subsets under weight/volume/max-stops constraints.

5. **Route Construction (`mst_tsp.py`)**  
   For each candidate set, route seed is built using undirected MST (Prim-style) and DFS preorder traversal.

6. **Route Improvement (`mst_tsp.py`)**  
   2-opt local search improves stop order by reversing subsequences when route cost decreases.

7. **Constraint Validation (`pipeline.py`)**  
   Final route distance is checked against `max_distance_km`; first high-score feasible candidate is returned.

### Complexity (Practical View)

- Scoring/filtering: roughly **O(P)**.
- Matrix handling: **O(N²)** data size (N nodes, including start/end).
- Beam candidate generation: bounded combinatorial search, controlled by `beam_width`, `expand_width`, `max_steps`.
- Per-candidate route evaluation: MST + DFS + 2-opt, typically quadratic neighborhood behavior per iteration.

This is an approximation-oriented design intended for operational quality and bounded runtime, not exact global optimality.

---

## 4. API Endpoints

### Main Endpoints

- `POST /optimize` - Run full optimization for a courier.
- `POST /dist-matrix` - Generate matrix and index map for a run (helper/debug flow).
- `GET/POST /companies` - List/create companies.
- `GET/POST /couriers` - List/create couriers.
- `GET/POST/PUT /packages` - Manage package lifecycle (company-scoped).

### Example `/optimize` Request

```json
{
  "company_id": "C1",
  "courier_id": "K1",
  "start": { "lat": 32.0, "lon": 34.7 },
  "constraints": {
    "max_distance_km": 18,
    "max_weight": 40,
    "max_volume": 40,
    "max_stops": 25
  },
  "service_date": "2026-01-17"
}
```

---

## 5. Data Model (Firestore)

The backend uses Firestore with company-scoped operational data and run-level auditability.

### Collections and Documents

- `companies/{company_id}`  
  Core company metadata (`name`, default start coordinates).

- `companies/{company_id}/couriers/{courier_id}`  
  Courier identity and company linkage.

- `companies/{company_id}/packages/{package_id}`  
  Package attributes (`lat/lon`, weight, volume, profit, deadline, delivered).

- `companies/{company_id}/runs/{run_id}`  
  Optimization run trace: request payload, response, index map, and matrix.

### Matrix Storage

Distance matrices are stored in run documents as **JSON-serialized strings** (`json.dumps(...)`) and loaded back with `json.loads(...)`, preserving matrix shape and index ordering.

### Index Convention (Critical)

- Index `0` = `start`
- Indices `1..K` = packages sorted by package ID
- Optional final index = `end`

This deterministic mapping guarantees matrix-to-node consistency during routing.

---

## 6. Setup & Running

### Prerequisites

- Python 3.8+
- ORS API key
- Firestore access (service account locally or workload identity in cloud)

### Install

```bash
pip install -r requirements.txt
```

### Environment Variables

- `ORS_API_KEY` - OpenRouteService API key.
- `GOOGLE_APPLICATION_CREDENTIALS` - path to service account JSON (local development if needed).

Example (PowerShell):

```powershell
$env:ORS_API_KEY="your_ors_api_key_here"
$env:GOOGLE_APPLICATION_CREDENTIALS="C:\path\to\service-account.json"
```

### Local Run

```bash
uvicorn API.api:app --reload
```

Service URLs:
- Swagger: `http://localhost:8000/docs`
- ReDoc: `http://localhost:8000/redoc`

### Cloud Run Deployment (Short Version)

1. Build container image from project `Dockerfile`.
2. Deploy to Cloud Run with Firestore-enabled GCP project.
3. Configure runtime env vars/secrets (`ORS_API_KEY`).
4. Use default service account or dedicated service account with Firestore permissions.

---

## 7. Example Usage

### Request

```bash
curl -X POST http://localhost:8000/optimize \
  -H "Content-Type: application/json" \
  -d '{
    "company_id": "C1",
    "courier_id": "K1",
    "start": {"lat": 32.0, "lon": 34.7},
    "constraints": {
      "max_distance_km": 18,
      "max_weight": 40,
      "max_volume": 40,
      "max_stops": 25
    },
    "service_date": "2026-01-17"
  }'
```

### Response (Representative)

```json
{
  "run_id": "550e8400-e29b-41d4-a716-446655440000",
  "company_id": "C1",
  "courier_id": "K1",
  "selected_package_ids": ["P01", "P02", "P03"],
  "route_node_ids": ["start", "pkg:P01", "pkg:P02", "pkg:P03"],
  "route_stops": [
    {
      "seq": 0,
      "type": "start",
      "lat": 32.0,
      "lon": 34.7,
      "package_id": null,
      "leg_km": 0.0,
      "cum_km": 0.0
    },
    {
      "seq": 1,
      "type": "delivery",
      "lat": 32.01,
      "lon": 34.71,
      "package_id": "P01",
      "leg_km": 1.2,
      "cum_km": 1.2
    }
  ],
  "totals": {
    "total_distance_km": 15.5,
    "total_weight": 6.0,
    "total_volume": 6.0,
    "total_profit": 135.0,
    "total_score": 450.0
  },
  "index_map": {
    "0": "start",
    "1": "P01",
    "2": "P02",
    "3": "P03"
  }
}
```

---

## 8. Design Decisions

### Why Beam Search instead of exact Knapsack?

Exact combinatorial optimization is costly as package count grows.  
Beam Search provides a controllable trade-off between solution quality and runtime by pruning the search frontier (`beam_width`, `expand_width`) while preserving diverse high-score candidates.

### Why MST + DFS + 2-opt?

- **MST + DFS** yields a fast constructive route from candidate nodes.
- **2-opt** improves route quality through local reordering without exhaustive TSP solving.
- This layered strategy is practical for real-time API use with directed road distances.

### Why Firestore?

Firestore fits the project’s multi-tenant, document-oriented operational model:
- natural hierarchy (`company -> couriers/packages/runs`),
- easy persistence of run audit data,
- cloud-native deployment compatibility,
- simpler operations than managing a relational schema at project scale.

---

## 9. Notes / Limitations

- **Approximation, not exact optimization:**  
  The routing/selection stack is heuristic due to NP-hardness of combined subset + route optimization.

- **Triangle inequality and road-network realism:**  
  ORS shortest-path distances are generally metric-like in practice, but directed/asymmetric effects (one-way roads, turn constraints) can limit classical symmetric-TSP guarantees.

- **Heuristic early filtering:**  
  Start-distance filtering reduces runtime but may exclude packages that could still fit in a globally better combined route.

- **External API dependency:**  
  ORS availability/latency directly affects matrix generation and total optimization response time.

- **Data quality sensitivity:**  
  Invalid coordinates, malformed deadlines, or inconsistent tenant data can degrade results or trigger validation failures.

---

**Version:** 2.x  
**Status:** Production backend with Firestore persistence and ORS-integrated optimization pipeline

