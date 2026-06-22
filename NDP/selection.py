"""
selection.py
Package selection under weight/volume/max-stops constraints via Beam Search.
"""

from __future__ import annotations

from typing import Dict, List, Tuple


def beam_select_package_sets(
    candidates: List[Dict],
    *,
    max_weight: float,
    max_volume: float,
    max_stops: int,
    score_by_id: Dict[str, float],
    beam_width: int = 30,
    expand_width: int = 25,
    max_steps: int | None = None,
) -> List[List[Dict]]:
    """
    Beam Search on package combinations under weight/volume/max_stops constraints.
    Returns candidate sets sorted by descending TOTAL_SCORE.

    Distance feasibility is checked later in pipeline.py on the real sub-matrix.
    """

    if not candidates:
        return []

    if max_steps is None:
        max_steps = max(1, int(max_stops))

    items = sorted(
        candidates,
        key=lambda p: score_by_id.get(str(p["id"]), 0.0),
        reverse=True,
    )

    State = Tuple[float, float, float, frozenset, Tuple[Dict, ...], int]

    start_state: State = (0.0, 0.0, 0.0, frozenset(), tuple(), 0)
    beam: List[State] = [start_state]

    best_by_set: Dict[frozenset, Tuple[float, Tuple[Dict, ...]]] = {}

    def try_store_solution(st: State) -> None:
        score_sum, w_sum, v_sum, chosen_ids, chosen_pkgs, _ = st
        if not chosen_ids:
            return
        prev = best_by_set.get(chosen_ids)
        if prev is None or score_sum > prev[0]:
            best_by_set[chosen_ids] = (score_sum, chosen_pkgs)

    for st in beam:
        try_store_solution(st)

    for _step in range(max_steps):
        new_states: List[State] = []
        seen_state_keys = set()

        for st in beam:
            score_sum, w_sum, v_sum, chosen_ids, chosen_pkgs, next_i = st

            if len(chosen_pkgs) >= max_stops:
                try_store_solution(st)
                continue

            end_i = min(len(items), next_i + expand_width)

            skip_state: State = (score_sum, w_sum, v_sum, chosen_ids, chosen_pkgs, end_i)
            key_skip = (chosen_ids, end_i)
            if key_skip not in seen_state_keys:
                new_states.append(skip_state)
                seen_state_keys.add(key_skip)

            for i in range(next_i, end_i):
                p = items[i]
                pid = str(p["id"])
                if pid in chosen_ids:
                    continue

                w = float(p.get("weight", 0.0))
                v = float(p.get("volume", 0.0))

                if w_sum + w > max_weight or v_sum + v > max_volume:
                    continue

                new_ids = frozenset(set(chosen_ids) | {pid})
                new_pkgs = chosen_pkgs + (p,)
                new_score = score_sum + float(score_by_id.get(pid, 0.0))

                nxt = i + 1
                new_state: State = (new_score, w_sum + w, v_sum + v, new_ids, new_pkgs, nxt)

                key = (new_ids, nxt)
                if key in seen_state_keys:
                    continue
                seen_state_keys.add(key)

                new_states.append(new_state)
                try_store_solution(new_state)

        if not new_states:
            break

        new_states.sort(key=lambda s: s[0], reverse=True)
        beam = new_states[: max(1, beam_width)]

    sols = list(best_by_set.values())
    sols.sort(key=lambda x: x[0], reverse=True)

    return [list(pkgs_tuple) for (_score, pkgs_tuple) in sols]
