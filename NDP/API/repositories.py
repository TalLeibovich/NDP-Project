"""
repositories.py

Firestore-based repositories for Company, Courier, Package entities.
Uses Firebase Firestore instead of local files.
"""

from __future__ import annotations
import json
import logging
from datetime import datetime, timedelta, timezone
from typing import List, Optional, Dict, Any

logger = logging.getLogger(__name__)

from google.cloud import firestore

from firebase_init import db
from API.entities import Company, Courier, Package


OPTIMIZATION_LOCK_MINUTES = 5


def _now_utc() -> datetime:
    return datetime.now(timezone.utc)


def _parse_iso_timestamp(value: str) -> datetime:
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        return parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def optimization_lock_expires_at(*, minutes: int = OPTIMIZATION_LOCK_MINUTES) -> str:
    return (_now_utc() + timedelta(minutes=minutes)).isoformat()


def _can_acquire_optimization_lock(data: Dict[str, Any], now: datetime) -> bool:
    if int(data.get("delivered", 0) or 0) != 0:
        return False
    if not data.get("in_delivery"):
        return True

    assigned_run_id = data.get("assigned_run_id")
    expires_at = data.get("in_delivery_expires_at")
    if assigned_run_id and not expires_at:
        return False
    if expires_at:
        return _parse_iso_timestamp(str(expires_at)) <= now
    return False


# =========================================================
# COMPANY REPOSITORY
# =========================================================
class CompanyRepository:
    def __init__(self):
        """Initialize CompanyRepository with Firestore client"""
        self.collection = db.collection("companies")
    
    def list_all(self) -> List[Company]:
        """List all companies from Firestore"""
        companies = []
        docs = self.collection.stream()
        for doc in docs:
            data = doc.to_dict()
            if data:
                companies.append(Company.from_dict(data))
        return companies
    
    def get_by_id(self, company_id: str) -> Optional[Company]:
        """Get company by ID from Firestore"""
        doc = self.collection.document(company_id).get()
        if doc.exists:
            data = doc.to_dict()
            if data:
                return Company.from_dict(data)
        return None
    
    def save(self, company: Company) -> None:
        """Save company to Firestore"""
        self.collection.document(company.company_id).set(company.to_dict())


# =========================================================
# COURIER REPOSITORY
# =========================================================
class CourierRepository:
    def __init__(self):
        """Initialize CourierRepository with Firestore client"""
        self.companies_collection = db.collection("companies")
    
    def list_all(self, company_id: Optional[str] = None) -> List[Courier]:
        """List couriers from Firestore, optionally filtered by company_id"""
        couriers = []
        
        if company_id:
            # Get couriers for specific company
            couriers_ref = self.companies_collection.document(company_id).collection("couriers")
            docs = couriers_ref.stream()
            for doc in docs:
                data = doc.to_dict()
                if data:
                    couriers.append(Courier.from_dict(data))
        else:
            # Get all couriers from all companies (less efficient, but maintains API compatibility)
            companies = self.companies_collection.stream()
            for company_doc in companies:
                couriers_ref = company_doc.reference.collection("couriers")
                docs = couriers_ref.stream()
                for doc in docs:
                    data = doc.to_dict()
                    if data:
                        couriers.append(Courier.from_dict(data))
        
        return couriers
    
    def get_by_id(self, courier_id: str) -> Optional[Courier]:
        """Get courier by ID from Firestore (searches across all companies)"""
        # Search across all companies
        companies = self.companies_collection.stream()
        for company_doc in companies:
            courier_doc = company_doc.reference.collection("couriers").document(courier_id).get()
            if courier_doc.exists:
                data = courier_doc.to_dict()
                if data:
                    return Courier.from_dict(data)
        return None
    
    def save(self, courier: Courier) -> None:
        """Save courier to Firestore under the company's couriers sub-collection"""
        company_ref = self.companies_collection.document(courier.company_id)
        couriers_ref = company_ref.collection("couriers")
        couriers_ref.document(courier.courier_id).set(courier.to_dict())


# =========================================================
# PACKAGE REPOSITORY (per company)
# =========================================================
class PackageRepository:
    def __init__(self, company_id: str):
        """Initialize PackageRepository for a specific company"""
        self.company_id = company_id
        self.collection = db.collection("companies").document(company_id).collection("packages")
    
    def list_all(self, delivered: Optional[int] = None) -> List[Package]:
        """List packages from Firestore, optionally filtered by delivered status"""
        query = self.collection
        
        if delivered is not None:
            query = query.where("delivered", "==", delivered)
        
        packages = []
        docs = query.stream()
        for doc in docs:
            data = doc.to_dict()
            if data:
                packages.append(Package.from_dict(data))
        return packages
    
    def get_by_id(self, package_id: str) -> Optional[Package]:
        """Get package by ID from Firestore"""
        doc = self.collection.document(package_id).get()
        if not doc.exists:
            return None
        data = doc.to_dict()
        if not data:
            return None
        try:
            return Package.from_dict(data)
        except Exception as e:
            logger.warning("Invalid package document for id=%s: %s", package_id, e)
            return None
    
    def save(self, package: Package) -> None:
        """Add or update package in Firestore"""
        data = package.to_dict()
        ref = self.collection.document(package.id)
        ref.set(data, merge=True)

        updates: Dict[str, Any] = {}
        if package.in_delivery_expires_at is not None:
            updates["in_delivery_expires_at"] = package.in_delivery_expires_at
        else:
            updates["in_delivery_expires_at"] = firestore.DELETE_FIELD
        if package.optimization_lock_run_id is not None:
            updates["optimization_lock_run_id"] = package.optimization_lock_run_id
        else:
            updates["optimization_lock_run_id"] = firestore.DELETE_FIELD
        ref.update(updates)

    def list_by_assigned_run(self, run_id: str) -> List[Package]:
        """List packages assigned to a specific run"""
        packages = []
        docs = self.collection.where("assigned_run_id", "==", run_id).stream()
        for doc in docs:
            data = doc.to_dict()
            if data:
                packages.append(Package.from_dict(data))
        return packages

    def list_in_delivery(self) -> List[Package]:
        """List packages currently marked as in delivery"""
        packages = []
        docs = self.collection.where("in_delivery", "==", True).stream()
        for doc in docs:
            data = doc.to_dict()
            if data:
                packages.append(Package.from_dict(data))
        return packages

    def list_available(self) -> List[Package]:
        """List packages that are undelivered and not in delivery."""
        self.release_expired_optimization_locks()
        packages = []
        docs = self.collection.where("delivered", "==", 0).where("in_delivery", "==", False).stream()
        for doc in docs:
            data = doc.to_dict()
            if data:
                packages.append(Package.from_dict(data))
        return packages

    def release_expired_optimization_locks(self) -> int:
        """Lazy expiration for temporary optimization locks."""
        now = _now_utc()
        released = 0
        batch = db.batch()
        pending = 0

        docs = self.collection.where("delivered", "==", 0).where("in_delivery", "==", True).stream()
        for doc in docs:
            data = doc.to_dict() or {}
            expires_at = data.get("in_delivery_expires_at")
            if not expires_at:
                continue
            if data.get("assigned_run_id"):
                continue
            if _parse_iso_timestamp(str(expires_at)) > now:
                continue

            batch.update(
                doc.reference,
                {
                    "in_delivery": False,
                    "in_delivery_expires_at": firestore.DELETE_FIELD,
                    "optimization_lock_run_id": firestore.DELETE_FIELD,
                },
            )
            released += 1
            pending += 1
            if pending >= 400:
                batch.commit()
                batch = db.batch()
                pending = 0

        if pending:
            batch.commit()
        return released

    @staticmethod
    def _acquire_optimization_lock_transaction(
        transaction: firestore.Transaction,
        ref: firestore.DocumentReference,
        *,
        run_id: str,
        expires_at: str,
    ) -> bool:
        snapshot = ref.get(transaction=transaction)
        if not snapshot.exists:
            return False
        data = snapshot.to_dict() or {}
        if not _can_acquire_optimization_lock(data, _now_utc()):
            return False
        transaction.update(
            ref,
            {
                "in_delivery": True,
                "in_delivery_expires_at": expires_at,
                "optimization_lock_run_id": run_id,
            },
        )
        return True

    def acquire_optimization_locks(self, package_ids: List[str], run_id: str, expires_at: str) -> List[str]:
        """Temporarily lock packages for an optimization run."""
        locked_ids: List[str] = []
        for package_id in package_ids:
            ref = self.collection.document(package_id)
            transaction = db.transaction()

            @firestore.transactional
            def _acquire(transaction: firestore.Transaction) -> bool:
                return self._acquire_optimization_lock_transaction(
                    transaction,
                    ref,
                    run_id=run_id,
                    expires_at=expires_at,
                )

            if _acquire(transaction):
                locked_ids.append(package_id)
                continue

            self.release_optimization_locks(run_id)
            raise RuntimeError(f"Failed to acquire optimization lock for package: {package_id}")

        return locked_ids

    def release_optimization_locks(
        self,
        run_id: str,
        *,
        only_package_ids: Optional[List[str]] = None,
    ) -> int:
        """Release temporary optimization locks created by a run."""
        released = 0
        allowed = set(only_package_ids) if only_package_ids is not None else None

        if only_package_ids is not None:
            package_ids = list(only_package_ids)
        else:
            docs = self.collection.where("optimization_lock_run_id", "==", run_id).stream()
            package_ids = [doc.id for doc in docs]

        for package_id in package_ids:
            if allowed is not None and package_id not in allowed:
                continue
            pkg = self.get_by_id(package_id)
            if not pkg:
                continue
            if pkg.optimization_lock_run_id != run_id:
                continue
            if pkg.assigned_run_id and not pkg.in_delivery_expires_at:
                continue

            pkg.in_delivery = False
            pkg.in_delivery_expires_at = None
            pkg.optimization_lock_run_id = None
            self.save(pkg)
            released += 1
        return released


# =========================================================
# RUN REPOSITORY (per company)
# =========================================================
class RunRepository:
    def __init__(self, company_id: str):
        """Initialize RunRepository for a specific company"""
        self.company_id = company_id
        self.collection = db.collection("companies").document(company_id).collection("runs")
    
    def save_run(self, run_id: str, run_data: Dict[str, Any]) -> None:
        """
        Save run data to Firestore.
        Uses merge=True to preserve matrix if it was already saved by save_matrix.
        """
        self.collection.document(run_id).set(run_data, merge=True)
    
    def get_run(self, run_id: str) -> Optional[Dict[str, Any]]:
        """Get run data from Firestore"""
        doc = self.collection.document(run_id).get()
        if doc.exists:
            return doc.to_dict()
        return None


# =========================================================
# DISTANCE MATRIX I/O (per company, per run)
# =========================================================
class DistanceMatrixRepository:
    def __init__(self, company_id: str):
        """Initialize DistanceMatrixRepository for a specific company"""
        self.company_id = company_id
        self.runs_collection = db.collection("companies").document(company_id).collection("runs")
    
    def get_matrix_path(self, run_id: str) -> str:
        """Return Firestore path string (for compatibility, no longer returns file path)"""
        return f"firestore://companies/{self.company_id}/runs/{run_id}/matrix"
    
    def save_matrix(self, run_id: str, matrix: List[List[float]]) -> str:
        """
        Save distance matrix to Firestore within the run document.
        Returns Firestore path string for compatibility.
        Note: The matrix is saved separately and will be merged with run_data in save_run.
        """
        # Prepare matrix data (convert None to 1e9 for safety, as Firestore doesn't support None in arrays)
        safe_matrix = []
        for row in matrix:
            safe_row = [1e9 if x is None else float(x) for x in row]
            safe_matrix.append(safe_row)
        
        # Save matrix to run document (using update to merge, not overwrite)
        run_ref = self.runs_collection.document(run_id)
        run_ref.set({"matrix": json.dumps(safe_matrix)}, merge=True)
        
        return self.get_matrix_path(run_id)
