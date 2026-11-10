"""Join reviewed session outcomes with production Kotlin replay reports."""

from __future__ import annotations

from typing import Any

from .annotations import AnnotationValidation
from .core import Inspection
from .splits import assign_session_splits


def evaluate_dataset(inspection: Inspection, annotations: AnnotationValidation,
                     pipeline_reports: dict[str, dict[str, Any]]) -> dict[str, Any]:
    by_session = {entry["session_id"]: entry for entry in annotations.valid}
    eligible: list[dict[str, Any]] = []
    excluded: list[dict[str, str]] = []
    counts = {"true_positives": 0, "true_negatives": 0, "false_positives": 0, "false_negatives": 0}
    latencies: list[dict[str, Any]] = []
    invalid_recording_sessions = {issue.session_id for issue in inspection.issues
                                  if issue.severity == "error" and issue.session_id}
    all_ids = set(annotations.declared_session_ids) | set(inspection.sessions)
    for sid in sorted(all_ids):
        entry = by_session.get(sid)
        truth = entry.get("binary_outcome") if entry else None
        reason = None
        if sid in annotations.invalid_session_ids:
            reason = "duplicate or invalid session annotation"
        elif sid in annotations.invalid_binary_outcome_session_ids:
            reason = "invalid or conflicting binary review/audit history"
        elif sid in invalid_recording_sessions or sid not in inspection.sessions:
            reason = "recording missing or contains invalid sensor rows"
        elif truth is None:
            reason = "unreviewed or missing binary outcome"
        elif truth["outcome"] == "INDETERMINATE":
            reason = "indeterminate outcome excluded from binary metrics"
        elif truth["recording_origin"] != "REAL":
            reason = "simulated, mixed, or unknown-origin recording excluded"
        report = pipeline_reports.get(sid)
        if reason is None and report is None:
            reason = "Kotlin evaluation report missing"
        if reason is None and (report.get("schema_version") != 1 or report.get("session_id") != sid):
            reason = "Kotlin evaluation report schema/session mismatch"
        if reason is None and report.get("provenance") != "OBSERVED":
            reason = "synthetic or non-observed Kotlin report excluded"
        if reason is None and report.get("final_countdown_state") != "IDLE":
            reason = "evaluation report is not isolated from countdown state"
        if reason is None:
            report_truth = report.get("ground_truth")
            required_match = ("session_id", "outcome", "review_id", "reviewer_id", "reviewed_at_utc", "evidence_provenance", "recording_origin", "crash_onset_timestamp_nanos")
            if (not isinstance(report_truth, dict) or report_truth.get("review_status") != "REVIEWED"
                    or any(report_truth.get(key) != truth.get(key) for key in required_match)):
                reason = "Kotlin report does not contain the same reviewed annotation metadata"
        if reason:
            excluded.append({"session_id": sid, "reason": reason})
            continue
        detected = report.get("outcome") == "EMERGENCY_CANDIDATE"
        crashed = truth["outcome"] == "CRASH_CONFIRMED"
        if crashed and detected: counts["true_positives"] += 1
        elif crashed: counts["false_negatives"] += 1
        elif detected: counts["false_positives"] += 1
        else: counts["true_negatives"] += 1
        eligible.append({"session_id": sid, "expected_outcome": truth["outcome"], "detected_candidate": detected,
                         "pipeline_outcome": report.get("outcome")})
        latency = report.get("detection_latency_millis")
        if crashed and isinstance(latency, (int, float)) and not isinstance(latency, bool) and latency >= 0:
            latencies.append({"session_id": sid, "detection_latency_millis": latency})
    positives = counts["true_positives"] + counts["false_positives"]
    actual_positives = counts["true_positives"] + counts["false_negatives"]
    return {
        "schema_version": 1,
        "eligible_sessions": eligible,
        "eligible_session_count": len(eligible),
        "excluded_sessions": excluded,
        "confusion_counts": counts,
        "precision": counts["true_positives"] / positives if positives else None,
        "recall": counts["true_positives"] / actual_positives if actual_positives else None,
        "detection_latencies": latencies,
        "mean_detection_latency_millis": sum(x["detection_latency_millis"] for x in latencies) / len(latencies) if latencies else None,
        "session_splits": assign_session_splits({row["session_id"] for row in eligible}),
        "limitations": [
            "Candidate detection is compared with reviewed crash outcome; it is not proof of a real-world crash classifier's accuracy.",
            "Only sessions with valid recordings, matching observed Kotlin reports, and explicit reviewed REAL outcomes are eligible.",
            "Synthetic fixtures and INDETERMINATE outcomes are excluded. Session splits are structural and not a generalization claim.",
        ],
    }
