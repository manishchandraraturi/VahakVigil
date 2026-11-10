"""Session-level split assignment; never splits individual sensor rows."""

from __future__ import annotations

from typing import Any


def assign_session_splits(
    reviewed_labeled_session_ids: set[str],
    *,
    minimum_per_split: int = 1,
) -> dict[str, Any]:
    """Return deterministic assignments only when each requested split has support.

    This is a split foundation, not an evaluation claim. A minimum of one session
    per split is only structural; reliable evaluation needs many more independent rides.
    """
    if minimum_per_split < 1:
        raise ValueError("minimum_per_split must be positive")
    ordered = sorted(reviewed_labeled_session_ids)
    required = 3 * minimum_per_split
    if len(ordered) < required:
        return {
            "created": False,
            "reason": f"Need at least {required} reviewed, labeled sessions for three splits at minimum_per_split={minimum_per_split}; found {len(ordered)}.",
            "training": [], "validation": [], "test": [],
        }
    # Keep all records from a session together. Stable lexical assignment avoids
    # nondeterministic changes between runs and makes no row-level random split.
    buckets: dict[str, list[str]] = {"training": [], "validation": [], "test": []}
    names = list(buckets)
    for index, session_id in enumerate(ordered):
        buckets[names[index % 3]].append(session_id)
    return {"created": True, "reason": "Session-level assignments created; small counts are not evidence of reliable generalization.", **buckets}
