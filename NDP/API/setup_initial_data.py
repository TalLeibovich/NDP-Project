"""
setup_initial_data.py

Creates initial seed data for companies, couriers, and packages.
Run this once to set up the initial data structure.
"""

from pathlib import Path
from API.entities import Company, Courier, Package
from API.repositories import CompanyRepository, CourierRepository, PackageRepository


def setup_initial_data():
    """Create initial companies, couriers, and migrate existing packages"""
    
    print("=" * 60)
    print("SETTING UP INITIAL DATA")
    print("=" * 60)
    
    # Initialize repositories
    company_repo = CompanyRepository()
    courier_repo = CourierRepository()
    
    # =========================================================
    # COMPANY C1 (Default Company)
    # =========================================================
    company = Company(
        company_id="C1",
        name="Default Company",
        default_start_lat=32.000,
        default_start_lon=34.700,
    )
    company_repo.save(company)
    print(f"\n[OK] Created company: {company.company_id} - {company.name}")
    
    # Create default courier for C1
    courier = Courier(
        courier_id="K1",
        company_id="C1",
        name="Default Courier",
    )
    courier_repo.save(courier)
    print(f"[OK] Created courier: {courier.courier_id} - {courier.name}")
    
    # Migrate existing packages from root packages.txt if exists
    old_packages_file = Path(__file__).resolve().parents[1] / "packages.txt"
    
    if old_packages_file.exists():
        print(f"\n[INFO] Found existing packages.txt, migrating to company C1...")
        pkg_repo = PackageRepository("C1")
        
        migrated = 0
        with old_packages_file.open("r", encoding="utf-8") as f:
            header = f.readline()  # skip header
            for line in f:
                line = line.strip()
                if not line or line.startswith("#"):
                    continue
                
                # Parse old format: id|lat|lon|weight|volume|profit|deadline|delivered
                parts = [p.strip() for p in line.split("|")]
                if len(parts) < 8:
                    continue
                
                pkg = Package(
                    id=parts[0],
                    company_id="C1",
                    lat=float(parts[1]),
                    lon=float(parts[2]),
                    weight=float(parts[3]),
                    volume=float(parts[4]),
                    profit=float(parts[5]) if parts[5] else 0.0,
                    deadline=parts[6] if parts[6] else None,
                    delivered=int(parts[7]) if parts[7] else 0,
                )
                pkg_repo.save(pkg)
                migrated += 1
        
        print(f"[OK] Migrated {migrated} packages to company C1")
    else:
        print(f"\n[INFO] No existing packages.txt found, skipping migration")
    
    # =========================================================
    # COMPANY C2 (Tel Aviv Express - realistic urban data)
    # =========================================================
    company2 = Company(
        company_id="C2",
        name="Tel Aviv Express",
        default_start_lat=32.0853,  # Tel Aviv center
        default_start_lon=34.7818,
    )
    company_repo.save(company2)
    print(f"\n[OK] Created company: {company2.company_id} - {company2.name}")
    
    # Create courier for C2
    courier2 = Courier(
        courier_id="K2",
        company_id="C2",
        name="Tel Aviv Courier",
    )
    courier_repo.save(courier2)
    print(f"[OK] Created courier: {courier2.courier_id} - {courier2.name}")
    
    # Create realistic packages for C2 in Tel Aviv center
    # All coordinates are on real roads in central Tel Aviv
    pkg_repo_c2 = PackageRepository("C2")
    
    c2_packages = [
        # Dizengoff area
        Package(id="T01", company_id="C2", lat=32.0753, lon=34.7748, weight=1.5, volume=2.0, profit=45.0, deadline="2026-01-25", delivered=0),
        Package(id="T02", company_id="C2", lat=32.0771, lon=34.7732, weight=2.0, volume=1.5, profit=52.0, deadline="2026-01-24", delivered=0),
        # Rothschild Blvd area
        Package(id="T03", company_id="C2", lat=32.0636, lon=34.7726, weight=1.0, volume=1.0, profit=38.0, deadline="2026-01-23", delivered=0),
        Package(id="T04", company_id="C2", lat=32.0658, lon=34.7712, weight=2.5, volume=2.5, profit=65.0, deadline="2026-01-22", delivered=0),
        # Ibn Gabirol St area
        Package(id="T05", company_id="C2", lat=32.0836, lon=34.7816, weight=1.8, volume=2.2, profit=55.0, deadline="2026-01-21", delivered=0),
        Package(id="T06", company_id="C2", lat=32.0808, lon=34.7813, weight=1.2, volume=1.8, profit=42.0, deadline="2026-01-20", delivered=0),
        # Allenby St area
        Package(id="T07", company_id="C2", lat=32.0665, lon=34.7699, weight=3.0, volume=3.5, profit=78.0, deadline="2026-01-19", delivered=0),
        Package(id="T08", company_id="C2", lat=32.0689, lon=34.7685, weight=2.2, volume=2.8, profit=62.0, deadline="2026-01-18", delivered=0),
        # Carmel Market area
        Package(id="T09", company_id="C2", lat=32.0676, lon=34.7666, weight=1.5, volume=1.5, profit=48.0, deadline="2026-01-17", delivered=0),
        Package(id="T10", company_id="C2", lat=32.0692, lon=34.7651, weight=2.8, volume=3.0, profit=72.0, deadline="2026-01-20", delivered=0),
        # Habima Square area
        Package(id="T11", company_id="C2", lat=32.0722, lon=34.7785, weight=1.0, volume=1.2, profit=35.0, deadline="2026-01-22", delivered=0),
        Package(id="T12", company_id="C2", lat=32.0738, lon=34.7801, weight=1.8, volume=2.0, profit=50.0, deadline="2026-01-21", delivered=0),
    ]
    
    for pkg in c2_packages:
        pkg_repo_c2.save(pkg)
    
    print(f"[OK] Created {len(c2_packages)} packages for company C2 (Tel Aviv center)")
    
    print("\n" + "=" * 60)
    print("SETUP COMPLETE")
    print("=" * 60)
    print("\nInitial data saved to Firestore.")
    print("Start the API server with:")
    print("  uvicorn API.api:app --reload")
    print()


if __name__ == "__main__":
    setup_initial_data()
