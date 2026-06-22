# Quick Start Guide - NDP Route Optimizer

## Prerequisites

- Python 3.8+
- ORS API Key (get free key at https://openrouteservice.org/)

## 5-Minute Setup

### Step 1: Install Dependencies

```bash
pip install fastapi uvicorn requests
```

### Step 2: Set ORS API Key

**Windows PowerShell:**
```powershell
$env:ORS_API_KEY="your_ors_api_key_here"
```

**Linux/Mac:**
```bash
export ORS_API_KEY="your_ors_api_key_here"
```

### Step 3: Initialize Data

```bash
cd API
python setup_initial_data.py
```

**Output:**
```
============================================================
SETTING UP INITIAL DATA
============================================================

[OK] Created company: C1 - Default Company
[OK] Created courier: K1 - Default Courier
[INFO] Found existing packages.txt, migrating to company C1...
[OK] Migrated 26 packages to company C1

============================================================
SETUP COMPLETE
============================================================
```

### Step 4: Start API Server

```bash
uvicorn api:app --reload
```

**Output:**
```
INFO:     Uvicorn running on http://127.0.0.1:8000 (Press CTRL+C to quit)
INFO:     Started reloader process
INFO:     Started server process
INFO:     Waiting for application startup.
INFO:     Application startup complete.
```

### Step 5: Test API

Open browser to: **http://localhost:8000/docs**

Or run the test script:
```bash
# In another terminal
cd API
python test_api.py
```

## Your First Optimization

### Using curl:

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
    }
  }'
```

### Using Python:

```python
import requests

response = requests.post("http://localhost:8000/optimize", json={
    "company_id": "C1",
    "courier_id": "K1",
    "start": {"lat": 32.0, "lon": 34.7},
    "constraints": {
        "max_distance_km": 18,
        "max_weight": 40,
        "max_volume": 40,
        "max_stops": 25
    }
})

result = response.json()
print(f"Run ID: {result['run_id']}")
print(f"Selected packages: {result['selected_package_ids']}")
print(f"Total distance: {result['totals']['total_distance_km']} km")
```

### Expected Response:

```json
{
  "run_id": "550e8400-e29b-41d4-a716-446655440000",
  "company_id": "C1",
  "courier_id": "K1",
  "selected_package_ids": ["P01", "P02", "P03", "P04", "P05"],
  "route_node_ids": ["start", "pkg:P01", "pkg:P02", "pkg:P03", "pkg:P04", "pkg:P05"],
  "route_stops": [...],
  "totals": {
    "total_distance_km": 15.5,
    "total_weight": 12.0,
    "total_volume": 12.0,
    "total_profit": 285.0,
    "total_score": 950.0
  },
  "index_map": {
    "0": "start",
    "1": "P01",
    "2": "P02",
    ...
  }
}
```

## Common Operations

### List Packages

```bash
curl "http://localhost:8000/packages?company_id=C1&delivered=0"
```

### Mark Package as Delivered

```bash
curl -X PUT http://localhost:8000/packages/P01 \
  -H "Content-Type: application/json" \
  -d '{"company_id": "C1", "delivered": 1}'
```

### Add New Package

```bash
curl -X POST http://localhost:8000/packages \
  -H "Content-Type: application/json" \
  -d '{
    "id": "P27",
    "company_id": "C1",
    "lat": 32.51,
    "lon": 35.31,
    "weight": 5.0,
    "volume": 5.0,
    "profit": 320.0,
    "deadline": "2026-01-25",
    "delivered": 0
  }'
```

### Geocode Address

```bash
curl -X POST http://localhost:8000/geo/geocode \
  -H "Content-Type: application/json" \
  -d '{"address": "Tel Aviv, Israel"}'
```

## Viewing Results

### Run Data

All optimization runs are saved in:
```
data/companies/C1/runs/{run_id}.json
```

Each file contains:
- Request parameters
- Selected packages
- Route details
- Distance matrix path
- Index map

### Distance Matrices

Per-run matrices are saved in:
```
data/companies/C1/dist_matrix_{run_id}.txt
```

Format: Space-separated values, `dist[i][j]` = distance from i to j in km.

## API Documentation

Interactive API docs available at:
- **Swagger UI**: http://localhost:8000/docs
- **ReDoc**: http://localhost:8000/redoc

## Troubleshooting

### "ORS_API_KEY is not set"
Set the environment variable before starting the server.

### "Company not found"
Run `setup_initial_data.py` to create the default company.

### "No undelivered packages found"
All packages are marked as delivered. Use PUT endpoint to set `delivered=0`.

### Connection refused
Make sure the API server is running on port 8000.

## Next Steps

- Read the full [API README](API/README.md)
- Check the [Implementation Summary](IMPLEMENTATION_SUMMARY.md)
- Explore the [Swagger UI](http://localhost:8000/docs)
- Create additional companies and couriers
- Integrate with your frontend application

## Support

For issues or questions, check:
1. API logs in the terminal
2. Run data in `data/companies/C1/runs/`
3. Package data in `data/companies/C1/packages.txt`

