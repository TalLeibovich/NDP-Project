from __future__ import annotations

from datetime import date, datetime
from typing import Any, Dict


def _to_date(x: Any) -> date:
    if isinstance(x, date) and not isinstance(x, datetime):
        return x
    if isinstance(x, datetime):
        return x.date()
    if isinstance(x, str):
        s = x.strip()
        try:
            # supports YYYY-MM-DD
            return date.fromisoformat(s)
        except ValueError:
            # supports DD-MM-YYYY
            return datetime.strptime(s, "%d-%m-%Y").date()
    raise ValueError(f"Cannot convert to date: {x!r}")

def package_score(pkg: Dict[str, Any], service_date: date) -> float:
    """
    Score = profit * urgency_factor
    urgency_factor = max(0.1, 1 - 0.1 * days_until_deadline)

    days_until_deadline = (deadline - service_date).days
    """
    # Accept both date and ISO string for service_date (Swagger sends string)
    service_date = _to_date(service_date)

    profit = float(pkg.get("profit", 0.0))

    deadline_raw = pkg.get("deadline")
    if deadline_raw is None:
        # no deadline => treat as low urgency
        return profit * 0.5

    deadline = _to_date(deadline_raw)

    days_until = (deadline - service_date).days
    urgency = 1.0 - (days_until * 0.1)

    if urgency < 0.1:
        urgency = 0.1

    return profit * urgency