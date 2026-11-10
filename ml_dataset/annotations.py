"""Human-reviewed ground-truth annotation manifest validation."""

from __future__ import annotations

import json
from datetime import datetime
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

LABELS = {
    "NORMAL_RIDE", "BRAKING", "POTHOLE_OR_ROUGH_ROAD", "SUDDEN_MANEUVER",
    "PHONE_DROP", "MOUNTED_PHONE_MOVEMENT", "SIMULATED_IMPACT", "UNKNOWN",
}
LEVELS = {"SESSION", "EVENT"}
EVENT_ORIGINS = {"REAL", "SIMULATED", "UNKNOWN"}
REVIEW_STATUSES = {"UNREVIEWED", "REVIEWED", "NEEDS_REVIEW"}
BINARY_OUTCOMES = {"CRASH_CONFIRMED", "NO_CRASH_CONFIRMED", "INDETERMINATE"}
RECORDING_ORIGINS = {"REAL", "SIMULATED", "MIXED", "UNKNOWN"}


@dataclass
class AnnotationValidation:
    valid: list[dict[str, Any]]
    errors: list[dict[str, Any]]
    unreviewed_sessions: list[str]
    invalid_session_ids: list[str] = field(default_factory=list)
    invalid_binary_outcome_session_ids: list[str] = field(default_factory=list)
    declared_session_ids: list[str] = field(default_factory=list)


def empty_manifest(session_ids: set[str]) -> dict[str, Any]:
    return {
        "schema_version": 2,
        "description": "Human-reviewed contextual labels and explicit binary outcomes. Missing outcomes are unreviewed; never infer NO_CRASH_CONFIRMED from absence of a crash label.",
        "sessions": [
            {"session_id": sid, "review_status": "UNREVIEWED", "labels": [], "binary_outcome": None}
            for sid in sorted(session_ids)
        ],
    }


def validate_manifest(document: dict[str, Any], existing_session_ids: set[str]) -> AnnotationValidation:
    valid: list[dict[str, Any]] = []
    errors: list[dict[str, Any]] = []
    unreviewed: list[str] = []
    invalid_sessions: set[str] = set()
    invalid_binary: set[str] = set()
    declared: set[str] = set()
    schema_version = document.get("schema_version")
    if type(schema_version) is not int or schema_version not in {1, 2}:
        errors.append({"path": "schema_version", "error": "Must equal 1 (contextual-label compatibility) or 2."})
    sessions = document.get("sessions")
    if not isinstance(sessions, list):
        return AnnotationValidation([], errors + [{"path": "sessions", "error": "Must be an array."}], sorted(existing_session_ids), [], [], [])
    seen_sessions: set[str] = set()
    for index, session in enumerate(sessions):
        path = f"sessions[{index}]"
        if not isinstance(session, dict):
            errors.append({"path": path, "error": "Must be an object."})
            continue
        sid = session.get("session_id")
        if not isinstance(sid, str) or not sid:
            errors.append({"path": path + ".session_id", "error": "Required non-empty session ID."})
            continue
        declared.add(sid)
        if sid not in existing_session_ids:
            errors.append({"path": path + ".session_id", "error": f"Unknown session: {sid}."})
            invalid_sessions.add(sid)
            continue
        if sid in seen_sessions:
            errors.append({"path": path + ".session_id", "error": "Duplicate session entry."})
            invalid_sessions.add(sid)
            valid = [entry for entry in valid if entry["session_id"] != sid]
            continue
        seen_sessions.add(sid)
        status = session.get("review_status", "UNREVIEWED")
        if not isinstance(status, str) or status not in REVIEW_STATUSES:
            errors.append({"path": path + ".review_status", "error": f"Expected one of {sorted(REVIEW_STATUSES)}."})
            status = "NEEDS_REVIEW"
        if status != "REVIEWED":
            unreviewed.append(sid)
        labels = session.get("labels", [])
        if not isinstance(labels, list):
            errors.append({"path": path + ".labels", "error": "Must be an array."})
            continue
        valid_labels: list[dict[str, Any]] = []
        for label_index, label in enumerate(labels):
            label_path = f"{path}.labels[{label_index}]"
            if not isinstance(label, dict):
                errors.append({"path": label_path, "error": "Must be an object."})
                continue
            before = len(errors)
            name, level = label.get("label"), label.get("level")
            if not isinstance(name, str) or name not in LABELS:
                errors.append({"path": label_path + ".label", "error": f"Expected one of {sorted(LABELS)}."})
            if not isinstance(level, str) or level not in LEVELS:
                errors.append({"path": label_path + ".level", "error": "Expected SESSION or EVENT."})
            start, end = label.get("event_start_timestamp_nanos"), label.get("event_end_timestamp_nanos")
            if level == "EVENT":
                for field in ("event_start_timestamp_nanos", "event_end_timestamp_nanos"):
                    value = label.get(field)
                    if not isinstance(value, int) or value < 0:
                        errors.append({"path": label_path + "." + field, "error": "Required non-negative integer for event labels."})
                if isinstance(start, int) and isinstance(end, int) and start > end:
                    errors.append({"path": label_path, "error": "Event start timestamp must not be after end timestamp."})
            elif level == "SESSION" and (start is not None or end is not None):
                errors.append({"path": label_path, "error": "Session labels must not define event timestamps."})
            origin = label.get("event_origin", "UNKNOWN")
            if not isinstance(origin, str) or origin not in EVENT_ORIGINS:
                errors.append({"path": label_path + ".event_origin", "error": f"Expected one of {sorted(EVENT_ORIGINS)}."})
            source = label.get("annotation_source")
            if not isinstance(source, str) or not source.strip():
                errors.append({"path": label_path + ".annotation_source", "error": "Required annotation provenance."})
            confidence = label.get("confidence")
            if confidence is not None and (not isinstance(confidence, (int, float)) or isinstance(confidence, bool) or not 0 <= confidence <= 1):
                errors.append({"path": label_path + ".confidence", "error": "Must be between 0 and 1."})
            review = label.get("review_status", status)
            if not isinstance(review, str) or review not in REVIEW_STATUSES:
                errors.append({"path": label_path + ".review_status", "error": f"Expected one of {sorted(REVIEW_STATUSES)}."})
            if len(errors) == before:
                normalized = dict(label)
                normalized["event_origin"] = origin
                normalized["review_status"] = review
                valid_labels.append(normalized)
        binary_outcome, binary_errors = validate_binary_outcome(session.get("binary_outcome"), sid, path + ".binary_outcome")
        errors.extend(binary_errors)
        if binary_errors:
            invalid_binary.add(sid)
        if binary_outcome is None:
            unreviewed.append(sid)
        if binary_outcome and binary_outcome["recording_origin"] == "REAL":
            has_simulated_context = any(
                label.get("label") == "SIMULATED_IMPACT" or label.get("event_origin") == "SIMULATED"
                for label in valid_labels
            )
            if has_simulated_context:
                errors.append({"path": path + ".binary_outcome.recording_origin", "error": "REAL recording origin conflicts with a contextual label marked SIMULATED."})
                invalid_binary.add(sid)
                binary_outcome = None
        valid.append({"session_id": sid, "review_status": status, "labels": valid_labels, "binary_outcome": binary_outcome})
    for sid in sorted(existing_session_ids - seen_sessions):
        unreviewed.append(sid)
    return AnnotationValidation(valid, errors, sorted(set(unreviewed)), sorted(invalid_sessions), sorted(invalid_binary), sorted(declared))


def _valid_timestamp(value: Any) -> bool:
    if not isinstance(value, str) or not value.strip():
        return False
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return False
    return parsed.tzinfo is not None and parsed.utcoffset() is not None


def validate_binary_outcome(value: Any, session_id: str, path: str = "binary_outcome") -> tuple[dict[str, Any] | None, list[dict[str, Any]]]:
    if value is None:
        return None, []
    errors: list[dict[str, Any]] = []
    if not isinstance(value, dict):
        return None, [{"path": path, "error": "Must be null or a reviewed binary outcome object."}]

    history = value.get("audit_history", [])
    current = {key: val for key, val in value.items() if key != "audit_history"}
    if not isinstance(history, list):
        errors.append({"path": path + ".audit_history", "error": "Must be an array."})
        history = []
    reviews = history + [current]
    review_ids: list[str] = []
    normalized_history: list[dict[str, Any]] = []
    for index, review in enumerate(reviews):
        review_path = path + (f".audit_history[{index}]" if index < len(history) else "")
        if not isinstance(review, dict):
            errors.append({"path": review_path, "error": "Must be an object."})
            continue
        review_id = review.get("review_id")
        if not isinstance(review_id, str) or not review_id.strip():
            errors.append({"path": review_path + ".review_id", "error": "Required non-empty review identifier."})
        else:
            if review_id in review_ids:
                errors.append({"path": review_path + ".review_id", "error": "Duplicate review identifier; conflicting review history."})
            review_ids.append(review_id)
        if review.get("session_id") != session_id:
            errors.append({"path": review_path + ".session_id", "error": "Must match the containing session identifier."})
        if review.get("outcome") not in BINARY_OUTCOMES:
            errors.append({"path": review_path + ".outcome", "error": f"Expected one of {sorted(BINARY_OUTCOMES)}."})
        for field in ("reviewer_id", "evidence_provenance"):
            if not isinstance(review.get(field), str) or not review[field].strip():
                errors.append({"path": review_path + "." + field, "error": "Required non-empty review provenance."})
        if not _valid_timestamp(review.get("reviewed_at_utc")):
            errors.append({"path": review_path + ".reviewed_at_utc", "error": "Must be an ISO-8601 timestamp with a timezone."})
        origin = review.get("recording_origin")
        if origin not in RECORDING_ORIGINS:
            errors.append({"path": review_path + ".recording_origin", "error": f"Expected one of {sorted(RECORDING_ORIGINS)}."})
        if review.get("outcome") == "CRASH_CONFIRMED" and origin != "REAL":
            errors.append({"path": review_path + ".outcome", "error": "CRASH_CONFIRMED requires recording_origin REAL; simulated, mixed, or unknown data cannot be marked as a real crash."})
        onset = review.get("crash_onset_timestamp_nanos")
        if onset is not None and (type(onset) is not int or onset < 0 or review.get("outcome") != "CRASH_CONFIRMED"):
            errors.append({"path": review_path + ".crash_onset_timestamp_nanos", "error": "Must be a non-negative integer and only applies to CRASH_CONFIRMED."})
        if review.get("notes") is not None and not isinstance(review.get("notes"), str):
            errors.append({"path": review_path + ".notes", "error": "Must be a string when present."})
        if index < len(history):
            next_id = reviews[index + 1].get("review_id") if isinstance(reviews[index + 1], dict) else None
            if review.get("superseded_by_review_id") != next_id:
                errors.append({"path": review_path + ".superseded_by_review_id", "error": "Review history is conflicting or not linked to the next explicit revision."})
            if not _valid_timestamp(review.get("superseded_at_utc")):
                errors.append({"path": review_path + ".superseded_at_utc", "error": "Must be a timezone-aware ISO-8601 timestamp."})
            if not isinstance(review.get("revision_reason"), str) or not review["revision_reason"].strip():
                errors.append({"path": review_path + ".revision_reason", "error": "A non-empty reason is required for every revision."})
        normalized_history.append(dict(review))

    if errors:
        return None, errors
    normalized = dict(current)
    normalized["audit_history"] = normalized_history
    return normalized, []


def load_manifest(path: Path, existing_session_ids: set[str]) -> AnnotationValidation:
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        return AnnotationValidation([], [{"path": str(path), "error": f"Cannot read manifest: {exc}"}], sorted(existing_session_ids))
    if not isinstance(document, dict):
        return AnnotationValidation([], [{"path": str(path), "error": "Manifest root must be an object."}], sorted(existing_session_ids))
    return validate_manifest(document, existing_session_ids)
