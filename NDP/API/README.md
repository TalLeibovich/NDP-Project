# NDP Route Optimizer API - Version 2.0

## Architecture Overview

This API uses a **file-based data layer** with entities (Company, Courier, Package) and **per-run ORS distance matrices**.

### Key Design Principles

1. **Distance Matrix per Run**: Every optimization generates a new ORS matrix saved as `dist_matrix_{run_id}.txt`
2. **Index 0 = START**: Matrix always has start point at index 0, packages at indices 1..N, optional end at last index
3. **Package Ordering**: Packages are sorted by ID (e.g., P01, P02, ...) for consistent index mapping
4. **Directed Matrix**: `dist[i][j]` = distance from node i to node j (not symmetric)
5. **Company-Scoped Data**: Each company has its own packages and runs directory

## Directory Structure

```
data/
├── companies/
│   ├── companies.txt
│   └── {company_id}/
│       ├── packages.txt
│       ├── runs/
│       │   └── {run_id}.json
│       └── dist_matrix_{run_id}.txt
└── couriers/
    └── couriers.txt
```

## Setup

### 1. Install Dependencies

```bash
pip install fastapi uvicorn requests
```

### 2. Set ORS API Key

```bash
# Windows PowerShell
$env:ORS_API_KEY="your_ors_api_key_here"

# Linux/Mac
export ORS_API_KEY="your_ors_api_key_here"
```

### 3. Initialize Data

```bash
cd API
python setup_initial_data.py
```

This creates:
- Default company (C1)
- Default courier (K1)
- Migrates existing packages.txt to company C1

### 4. Start API Server

```bash
cd API
uvicorn api:app --reload
```

API will be available at: `http://localhost:8000`

## API Endpoints

### Companies

#### `GET /companies`
List all companies.

**Response:**
```json
[
  {
    "company_id": "C1",
    "name": "Default Company",
    "default_start_lat": 32.0,
    "default_start_lon": 34.7
  }
]
```

#### `POST /companies`
Create a new company.

**Request:**
```json
{
  "company_id": "C2",
  "name": "Express Delivery",
  "default_start_lat": 32.0,
  "default_start_lon": 34.7
}
```

### Couriers

#### `GET /couriers?company_id=C1`
List couriers, optionally filtered by company.

**Response:**
```json
[
  {
    "courier_id": "K1",
    "company_id": "C1",
    "name": "Default Courier"
  }
]
```

#### `POST /couriers`
Create a new courier.

**Request:**
```json
{
  "courier_id": "K2",
  "company_id": "C1",
  "name": "John Doe"
}
```

### Packages

#### `GET /packages?company_id=C1&delivered=0`
Get packages for a company.

**Query Params:**
- `company_id` (required): Company ID
- `delivered` (optional): Filter by delivery status (0=not delivered, 1=delivered)

**Response:**
```json
[
  {
    "id": "P01",
    "company_id": "C1",
    "lat": 32.01,
    "lon": 34.71,
    "weight": 2.0,
    "volume": 2.0,
    "profit": 40.0,
    "deadline": "2026-01-20",
    "delivered": 0
  }
]
```

#### `POST /packages`
Create package(s). Can be single object or array.

**Request (single):**
```json
{
  "id": "P27",
  "company_id": "C1",
  "lat": 32.51,
  "lon": 35.31,
  "weight": 5.0,
  "volume": 5.0,
  "profit": 320.0,
  "deadline": "2026-01-25",
  "delivered": 0
}
```

**Request (batch):**
```json
[
  {"id": "P27", "company_id": "C1", ...},
  {"id": "P28", "company_id": "C1", ...}
]
```

#### `PUT /packages/{package_id}`
Update a package (e.g., mark as delivered).

**Request:**
```json
{
  "company_id": "C1",
  "delivered": 1
}
```

### Optimization

#### `POST /optimize`
Run route optimization for a courier.

**Request:**
```json
{
  "company_id": "C1",
  "courier_id": "K1",
  "start": {
    "lat": 32.0,
    "lon": 34.7
  },
  "end": {
    "lat": 32.0,
    "lon": 34.7
  },
  "constraints": {
    "max_distance_km": 18,
    "max_weight": 40,
    "max_volume": 40,
    "max_stops": 25
  },
  "service_date": "2026-01-17"
}
```

**Notes:**
- `start` is always required (sent in each request)
- `end` is optional (if provided, included in matrix at last index)
- Only packages with `delivered=0` are included in optimization

**Response:**
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
      "lon": 34.7
    },
    {
      "seq": 1,
      "type": "delivery",
      "package_id": "P01",
      "lat": 32.01,
      "lon": 34.71,
      "distance_from_prev_km": 1.2
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

**Run Data Saved:**
- `data/companies/C1/runs/{run_id}.json` - Full run details
- `data/companies/C1/dist_matrix_{run_id}.txt` - ORS distance matrix for this run

### Utilities

#### `POST /geo/geocode`
Convert address to coordinates.

**Request:**
```json
{
  "address": "Tel Aviv, Israel"
}
```

**Response:**
```json
{
  "lat": 32.0853,
  "lon": 34.7818
}
```

#### `POST /dist-matrix`
Generate ORS distance matrix (helper endpoint).

**Request:**
```json
{
  "company_id": "C1",
  "start": {"lat": 32.0, "lon": 34.7},
  "end": {"lat": 32.0, "lon": 34.7}
}
```

**Response:**
```json
{
  "run_id": "550e8400-e29b-41d4-a716-446655440000",
  "company_id": "C1",
  "matrix_path": "data/companies/C1/dist_matrix_550e8400-e29b-41d4-a716-446655440000.txt",
  "matrix_size": "27x27",
  "index_map": {"0": "start", "1": "P01", ...}
}
```

## Distance Matrix Convention

### Matrix Semantics
- **Directed matrix**: `dist[i][j]` = shortest path distance from node i to node j (kilometers)
- **Not symmetric**: `dist[i][j]` may not equal `dist[j][i]` (road network is directed)

### Index Mapping
```
Index 0      = START point (always)
Index 1..K   = Delivery packages (sorted by package ID)
Index K+1    = END point (optional, only if provided in request)
```

### Example (3 packages, no end):
```
points = [START, P01, P02, P03]
matrix = 4x4

index_map = {
  "0": "start",
  "1": "P01",
  "2": "P02",
  "3": "P03"
}

dist[0][1] = distance from START to P01
dist[1][2] = distance from P01 to P02
dist[2][0] = distance from P02 back to START
```

## File Formats

### companies.txt
```
company_id|name|default_start_lat|default_start_lon
C1|Default Company|32.0|34.7
```

### couriers.txt
```
courier_id|company_id|name
K1|C1|Default Courier
```

### packages.txt (per company)
```
id|company_id|lat|lon|weight|volume|profit|deadline|delivered
P01|C1|32.01|34.71|2.0|2.0|40.0|2026-01-20|0
```

### dist_matrix_{run_id}.txt
```
0.0 1.2 2.5 3.8
1.2 0.0 1.5 2.9
2.5 1.5 0.0 1.8
3.8 2.9 1.8 0.0
```

Each row is space-separated, representing distances in km.

## Troubleshooting

### "ORS_API_KEY is not set"
Make sure you've set the environment variable before starting the server.

### "Company not found"
Run `setup_initial_data.py` to create the default company.

### "No undelivered packages found"
All packages are marked as delivered. Use `PUT /packages/{id}` to set `delivered=0`.

### Matrix size mismatch
This should not happen with the new architecture. If it does, check that:
- Packages are being sorted by ID consistently
- The ORS API returned the expected matrix size

## Testing

Example test flow:

```bash
# 1. Create a company
curl -X POST http://localhost:8000/companies \
  -H "Content-Type: application/json" \
  -d '{"company_id":"C1","name":"Test Co","default_start_lat":32.0,"default_start_lon":34.7}'

# 2. Create a courier
curl -X POST http://localhost:8000/couriers \
  -H "Content-Type: application/json" \
  -d '{"courier_id":"K1","company_id":"C1","name":"Courier 1"}'

# 3. Add packages (run setup_initial_data.py or add via API)

# 4. Run optimization
curl -X POST http://localhost:8000/optimize \
  -H "Content-Type: application/json" \
  -d '{
    "company_id":"C1",
    "courier_id":"K1",
    "start":{"lat":32.0,"lon":34.7},
    "constraints":{"max_distance_km":18,"max_weight":40,"max_volume":40,"max_stops":25}
  }'
```

## Architecture Notes

### Why per-run matrices?
- Each optimization may have different packages (some delivered since last run)
- Different start/end points per run
- Ensures matrix always matches the exact package set being optimized
- Provides full audit trail (matrix saved with run results)

### Why file-based storage?
- Simple, no database required
- Human-readable formats
- Easy to backup and version control
- Sufficient for single-server deployments

### Algorithm Core (Unchanged)
The following files remain unchanged:
- `pipeline.py` - Main optimization logic
- `selection.py` - Beam search for package selection
- `scoring.py` - Package scoring
- `routing.py` - Route stop generation
- `mst_tsp.py` - MST-based TSP solver
- `geo.py` - ORS API integration

