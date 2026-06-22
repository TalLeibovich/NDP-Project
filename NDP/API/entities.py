"""
entities.py

Entity classes for NDP Route Optimizer.
Simple dataclasses for Company, Courier, Package.
"""

from __future__ import annotations
from dataclasses import dataclass
from typing import Optional


@dataclass
class Company:
    company_id: str
    name: str
    default_start_lat: float
    default_start_lon: float
    
    def to_dict(self) -> dict:
        return {
            "company_id": self.company_id,
            "name": self.name,
            "default_start_lat": self.default_start_lat,
            "default_start_lon": self.default_start_lon,
        }
    
    @staticmethod
    def from_dict(d: dict) -> Company:
        return Company(
            company_id=str(d["company_id"]),
            name=str(d["name"]),
            default_start_lat=float(d["default_start_lat"]),
            default_start_lon=float(d["default_start_lon"]),
        )


@dataclass
class Courier:
    courier_id: str
    company_id: str
    name: str
    
    def to_dict(self) -> dict:
        return {
            "courier_id": self.courier_id,
            "company_id": self.company_id,
            "name": self.name,
        }
    
    @staticmethod
    def from_dict(d: dict) -> Courier:
        return Courier(
            courier_id=str(d["courier_id"]),
            company_id=str(d["company_id"]),
            name=str(d["name"]),
        )


@dataclass
class Package:
    id: str
    company_id: str
    lat: float
    lon: float
    weight: float
    volume: float
    profit: float
    address: Optional[str] = None
    formatted_address: Optional[str] = None
    deadline: Optional[str] = None
    delivered: int = 0
    in_delivery: bool = False
    assigned_to: Optional[str] = None
    assigned_run_id: Optional[str] = None
    assigned_at: Optional[str] = None
    delivered_at: Optional[str] = None
    in_delivery_expires_at: Optional[str] = None
    optimization_lock_run_id: Optional[str] = None

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "company_id": self.company_id,
            "lat": self.lat,
            "lon": self.lon,
            "weight": self.weight,
            "volume": self.volume,
            "profit": self.profit,
            "address": self.address,
            "formatted_address": self.formatted_address,
            "deadline": self.deadline,
            "delivered": self.delivered,
            "in_delivery": self.in_delivery,
            "assigned_to": self.assigned_to,
            "assigned_run_id": self.assigned_run_id,
            "assigned_at": self.assigned_at,
            "delivered_at": self.delivered_at,
        }
    
    @staticmethod
    def from_dict(d: dict) -> Package:
        return Package(
            id=str(d["id"]),
            company_id=str(d["company_id"]),
            lat=float(d["lat"]),
            lon=float(d["lon"]),
            weight=float(d["weight"]),
            volume=float(d["volume"]),
            profit=float(d.get("profit", 0) or 0),
            address=d.get("address") or None,
            formatted_address=d.get("formatted_address") or None,
            deadline=d.get("deadline") or None,
            delivered=int(d.get("delivered", 0) or 0),
            in_delivery=bool(d.get("in_delivery", False)),
            assigned_to=d.get("assigned_to") or None,
            assigned_run_id=d.get("assigned_run_id") or None,
            assigned_at=d.get("assigned_at") or None,
            delivered_at=d.get("delivered_at") or None,
            in_delivery_expires_at=d.get("in_delivery_expires_at") or None,
            optimization_lock_run_id=d.get("optimization_lock_run_id") or None,
        )
