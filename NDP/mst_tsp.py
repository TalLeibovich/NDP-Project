"""
Route building: MST + DFS + 2-OPT.

הקלט כאן הוא מטריצת מרחקים (יכולה להיות לא סימטרית).
ל־MST משתמשים במשקל לא־מכוון: w(i,j) = min(d_ij, d_ji).
"""

from __future__ import annotations

from typing import Dict, List, Tuple


INF = 1e18


def build_undirected_mst(dist: List[List[float]]) -> Dict[int, List[int]]:
    """
    Prim MST על גרף מלא.
    """
    n = len(dist)
    if n == 0:
        return {}

    in_mst = [False] * n
    key = [INF] * n
    parent = [-1] * n

    key[0] = 0.0

    for _ in range(n):
        u = -1
        best = INF
        for i in range(n):
            if (not in_mst[i]) and key[i] < best:
                best = key[i]
                u = i

        if u == -1:
            break

        in_mst[u] = True

        for v in range(n):
            if v == u or in_mst[v]:
                continue
            w = min(dist[u][v], dist[v][u])
            if w < key[v]:
                key[v] = w
                parent[v] = u

    adj: Dict[int, List[int]] = {i: [] for i in range(n)}
    for v in range(n):
        p = parent[v]
        if p != -1:
            adj[p].append(v)
            adj[v].append(p)

    return adj


def dfs_preorder(adj: Dict[int, List[int]], start: int) -> List[int]:
    """
    DFS preorder על MST.
    """
    visited = set()
    order: List[int] = []

    def dfs(u: int) -> None:
        visited.add(u)
        order.append(u)
        for v in adj.get(u, []):
            if v not in visited:
                dfs(v)

    dfs(start)
    return order


def route_cost(route: List[int], dist: List[List[float]]) -> float:
    """
    עלות מסלול לפי מטריצה מכוונת.
    """
    total = 0.0
    for i in range(len(route) - 1):
        total += dist[route[i]][route[i + 1]]
    return total


def two_opt(route: List[int], dist: List[List[float]]) -> List[int]:
    """
    שיפור 2-OPT למסלול (קירוב TSP).
    """
    best = route[:]
    improved = True

    while improved:
        improved = False
        best_cost = route_cost(best, dist)

        for i in range(1, len(best) - 2):
            for j in range(i + 1, len(best) - 1):
                new_route = best[:i] + list(reversed(best[i : j + 1])) + best[j + 1 :]
                new_cost = route_cost(new_route, dist)
                if new_cost + 1e-9 < best_cost:
                    best = new_route
                    improved = True
                    break
            if improved:
                break

    return best


def _nearest_neighbor_route(
    dist: List[List[float]],
    *,
    start_idx: int,
    end_idx: int | None = None,
) -> List[int]:
    """
    Greedy nearest-neighbor tour from start.
    For open routes this avoids MST+DFS jumping to far nodes first.
    """
    n = len(dist)
    if n == 0:
        return []
    if n == 1:
        return [start_idx]

    visited: List[int] = [start_idx]
    blocked = {start_idx}
    if end_idx is not None:
        blocked.add(end_idx)

    unvisited = {i for i in range(n) if i not in blocked}
    current = start_idx
    while unvisited:
        nxt = min(unvisited, key=lambda j: dist[current][j])
        visited.append(nxt)
        unvisited.remove(nxt)
        current = nxt

    if end_idx is not None and end_idx != start_idx:
        visited.append(end_idx)
    return visited


def build_route(
    dist: List[List[float]],
    *,
    start_idx: int,
    end_idx: int | None,
) -> Tuple[List[int], float]:
    """
    בונה מסלול:
    - Open route (no end): nearest-neighbor + 2-OPT
    - Closed route (explicit end): MST + DFS preorder + fixed end + 2-OPT
    """
    n = len(dist)
    if n == 0:
        return [], 0.0
    if n == 1:
        return [0], 0.0

    if end_idx is None:
        visited = _nearest_neighbor_route(dist, start_idx=start_idx)
    else:
        mst = build_undirected_mst(dist)
        initial = dfs_preorder(mst, start_idx)

        # מסיר כפילויות (ליתר ביטחון)
        visited = []
        for x in initial:
            if x not in visited:
                visited.append(x)

        # אם יש end — מבטיח שהוא אחרון
        if end_idx not in visited:
            visited.append(end_idx)
        elif visited[-1] != end_idx:
            visited = [x for x in visited if x != end_idx] + [end_idx]

    improved = two_opt(visited, dist)
    # 2-OPT may move the end node; keep explicit end fixed as the last stop.
    if end_idx is not None and improved and improved[-1] != end_idx:
        improved = [x for x in improved if x != end_idx] + [end_idx]
    cost = route_cost(improved, dist)
    return improved, cost
