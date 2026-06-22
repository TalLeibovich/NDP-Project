# קבצים שדורשים מיגרציה ל-Firestore

## ⚠️ שימושים בקבצים מקומיים שצריך להמיר ל-Firestore

### 1. `API/repositories.py` - **כל הקובץ**

קובץ זה משתמש בקבצי `.txt` ו-`.json` בתיקיית `data/`:

#### CompanyRepository (שורות 23-66)
- **קורא/כותב:** `data/companies/companies.txt`
- **שורות רלוונטיות:**
  - שורה 26: `self.companies_file = data_root / "companies" / "companies.txt"`
  - שורה 39: `with self.companies_file.open("r", encoding="utf-8") as f:`
  - שורה 59: `with self.companies_file.open("w", encoding="utf-8") as f:`

#### CourierRepository (שורות 71-113)
- **קורא/כותב:** `data/couriers/couriers.txt`
- **שורות רלוונטיות:**
  - שורה 74: `self.couriers_file = data_root / "couriers" / "couriers.txt"`
  - שורה 87: `with self.couriers_file.open("r", encoding="utf-8") as f:`
  - שורה 109: `with self.couriers_file.open("w", encoding="utf-8") as f:`

#### PackageRepository (שורות 118-163)
- **קורא/כותב:** `data/companies/{company_id}/packages.txt`
- **שורות רלוונטיות:**
  - שורה 123: `self.packages_file = self.company_dir / "packages.txt"`
  - שורה 136: `with self.packages_file.open("r", encoding="utf-8") as f:`
  - שורה 159: `with self.packages_file.open("w", encoding="utf-8") as f:`

#### RunRepository (שורות 168-186)
- **קורא/כותב:** `data/companies/{company_id}/runs/{run_id}.json`
- **שורות רלוונטיות:**
  - שורה 175: `run_file = self.runs_dir / f"{run_id}.json"`
  - שורה 176: `run_file.write_text(...)`
  - שורה 182: `run_file = self.runs_dir / f"{run_id}.json"`
  - שורה 185: `run_file.read_text(...)`

#### DistanceMatrixRepository (שורות 191-225)
- **קורא/כותב:** `data/companies/{company_id}/dist_matrix_{run_id}.txt`
- **שורות רלוונטיות:**
  - שורה 198: `return self.company_dir / f"dist_matrix_{run_id}.txt"`
  - שורה 203: `with matrix_path.open("w", encoding="utf-8") as f:`
  - שורה 217: `with matrix_path.open("r", encoding="utf-8") as f:`

### 2. `API/api.py` - שימוש ב-DistanceMatrixRepository

- **שורה 140:** `matrix_path = matrix_repo.save_matrix(run_id, matrix)`
  - הפונקציה `generate_ors_matrix_for_run` שומרת מטריצה לקובץ `.txt`
  - **המלצה:** לשמור את המטריצה ב-Firestore בתוך ה-Run document

- **שורה 496:** `"matrix_path": str(matrix_path)`
  - הנתיב נשמר ב-run_data, אבל בקובץ מקומי

### 3. `main.py` - **לא רלוונטי ל-API**

קובץ זה קורא קבצים מקומיים (`dist_matrix.txt`, `constraints.txt`, `packages.txt`), אבל הוא לא חלק מה-API server, אז לא צריך לשנות אותו.

### 4. `geo.py` - פונקציה לא בשימוש

- **שורות 285-292:** `save_distance_matrix_to_txt`
  - פונקציה זו לא בשימוש ב-API, אז לא צריך לשנות אותה

---

## 📋 סיכום - מה צריך לעשות

1. **ליצור גרסה חדשה של `API/repositories.py`** שתשתמש ב-Firestore במקום קבצים
2. **לשנות את `API/api.py`** כדי לשמור מטריצות ב-Firestore במקום קבצים
3. **לעדכן את `generate_ors_matrix_for_run`** כדי להחזיר נתיב Firestore במקום נתיב קובץ

---

## 🔧 מבנה Firestore מומלץ

```
companies/ (collection)
  └── {company_id}/ (document)
      ├── name: string
      ├── default_start_lat: number
      ├── default_start_lon: number
      │
      ├── couriers/ (sub-collection)
      │     └── {courier_id}: { name, company_id }
      │
      ├── packages/ (sub-collection)
      │     └── {package_id}: { lat, lon, weight, volume, profit, deadline, delivered }
      │
      └── runs/ (sub-collection)
            └── {run_id}: {
                  run_id, company_id, courier_id,
                  request: {...},
                  response: {...},
                  index_map: {...},
                  matrix: [[...], [...]]  // המטריצה כאן במקום קובץ
                }

couriers/ (root collection - optional, for global lookup)
  └── {courier_id}: { name, company_id }
```
