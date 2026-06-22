"""
Guarded debug logging for the optimization pipeline.

Enable with: NDP_DEBUG_OPTIMIZE=1
"""
from __future__ import annotations

import os
import sys


def is_enabled() -> bool:
    return os.getenv("NDP_DEBUG_OPTIMIZE", "").strip().lower() in frozenset({"yes", "on", "true", "1"})


def log(message: str) -> None:
    if is_enabled():
        print(f"[NDP_DEBUG_OPTIMIZE] {message}", file=sys.stdout, flush=True)
