"""Streaming CSV ingestion and quality statistics for VahakVigil exports."""

from __future__ import annotations

import csv
import json
import math
import statistics
from collections import defaultdict
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable, Iterator

EXPECTED_COLUMNS = (
    "session_id", "sensor_type", "timestamp_nanos", "timestamp_epoch_millis",
    "x", "y", "z", "accuracy",
)
SUPPORTED_SENSORS = {"ACCELEROMETER", "GYROSCOPE"}


@dataclass
class Issue:
    severity: str
    code: str
    message: str
    source_file: str
    row_number: int | None = None
    session_id: str | None = None
    sensor_type: str | None = None
    record: dict[str, Any] | None = None


@dataclass
class Event:
    source_file: str
    row_number: int
    session_id: str
    sensor_type: str
    timestamp_nanos: int
    timestamp_epoch_millis: int
    x: float
    y: float
    z: float
    accuracy: int | None

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass
class StreamStats:
    session_id: str
    sensor_type: str
    total_events: int = 0
    invalid_rows: int = 0
    first_timestamp_nanos: int | None = None
    last_timestamp_nanos: int | None = None
    first_epoch_millis: int | None = None
    last_epoch_millis: int | None = None
    min_timestamp_nanos: int | None = None
    max_timestamp_nanos: int | None = None
    duplicate_timestamps: int = 0
    non_monotonic_timestamps: int = 0
    missing_accuracy: int = 0
    intervals_nanos: list[int] = field(default_factory=list, repr=False)
    seen_timestamps: set[int] = field(default_factory=set, repr=False)
    epoch_offsets_millis: list[int] = field(default_factory=list, repr=False)

    def add(self, event: Event) -> list[Issue]:
        issues: list[Issue] = []
        ts = event.timestamp_nanos
        if ts in self.seen_timestamps:
            self.duplicate_timestamps += 1
            issues.append(_issue("warning", "duplicate_timestamp", "Repeated hardware timestamp within this sensor stream.", event))
        self.seen_timestamps.add(ts)
        if self.last_timestamp_nanos is not None:
            delta = ts - self.last_timestamp_nanos
            if delta <= 0:
                self.non_monotonic_timestamps += 1
                issues.append(_issue("warning", "non_monotonic_timestamp", "Timestamp is not greater than the prior row in this sensor stream.", event))
            else:
                self.intervals_nanos.append(delta)
                if event.timestamp_epoch_millis < (self.last_epoch_millis or 0):
                    issues.append(_issue("warning", "epoch_timestamp_non_monotonic", "Epoch timestamp decreased while hardware timestamp increased within this sensor stream.", event))
        if self.first_timestamp_nanos is None:
            self.first_timestamp_nanos = ts
            self.first_epoch_millis = event.timestamp_epoch_millis
        self.last_timestamp_nanos = ts
        self.last_epoch_millis = event.timestamp_epoch_millis
        self.min_timestamp_nanos = ts if self.min_timestamp_nanos is None else min(self.min_timestamp_nanos, ts)
        self.max_timestamp_nanos = ts if self.max_timestamp_nanos is None else max(self.max_timestamp_nanos, ts)
        self.total_events += 1
        if event.accuracy is None:
            self.missing_accuracy += 1
        self.epoch_offsets_millis.append(event.timestamp_epoch_millis - ts // 1_000_000)
        return issues

    def summary(self, gap_multiplier: float) -> dict[str, Any]:
        intervals = self.intervals_nanos
        duration = None
        if self.min_timestamp_nanos is not None and self.max_timestamp_nanos is not None:
            duration = (self.max_timestamp_nanos - self.min_timestamp_nanos) / 1_000_000_000
        median_interval = statistics.median(intervals) if intervals else None
        frequency = (1_000_000_000 / statistics.mean(intervals)) if intervals else None
        gap_threshold = median_interval * gap_multiplier if median_interval is not None else None
        return {
            "session_id": self.session_id,
            "sensor_type": self.sensor_type,
            "total_events": self.total_events,
            "recording_duration_seconds": duration,
            "first_timestamp_nanos": self.first_timestamp_nanos,
            "last_timestamp_nanos": self.last_timestamp_nanos,
            "first_epoch_millis": self.first_epoch_millis,
            "last_epoch_millis": self.last_epoch_millis,
            "average_sampling_frequency_hz": frequency,
            "median_sampling_interval_ms": median_interval / 1_000_000 if median_interval is not None else None,
            "minimum_sampling_interval_ms": min(intervals) / 1_000_000 if intervals else None,
            "maximum_sampling_interval_ms": max(intervals) / 1_000_000 if intervals else None,
            "timestamp_gap_rule": f"interval > {gap_multiplier:g} × this stream's median positive interval" if gap_threshold is not None else None,
            "timestamp_gaps": sum(1 for value in intervals if gap_threshold is not None and value > gap_threshold),
            "duplicate_timestamps": self.duplicate_timestamps,
            "non_monotonic_timestamps": self.non_monotonic_timestamps,
            "invalid_rows": self.invalid_rows,
            "missing_accuracy": self.missing_accuracy,
            "epoch_monotonic_offset_range_ms": (max(self.epoch_offsets_millis) - min(self.epoch_offsets_millis)) if self.epoch_offsets_millis else None,
        }


@dataclass
class Inspection:
    files: list[dict[str, Any]] = field(default_factory=list)
    streams: dict[tuple[str, str], StreamStats] = field(default_factory=dict)
    sessions: set[str] = field(default_factory=set)
    issues: list[Issue] = field(default_factory=list)

    @property
    def valid_rows(self) -> int:
        return sum(stats.total_events for stats in self.streams.values())

    def add(self, event: Event) -> None:
        self.sessions.add(event.session_id)
        key = (event.session_id, event.sensor_type.upper())
        stats = self.streams.setdefault(key, StreamStats(*key))
        self.issues.extend(stats.add(event))

    def to_json(self, gap_multiplier: float) -> dict[str, Any]:
        return {
            "tool": "vahakvigil-ml-dataset",
            "generated_at_utc": datetime.now(timezone.utc).isoformat(),
            "gap_multiplier_of_stream_median": gap_multiplier,
            "file_count": len(self.files),
            "valid_event_count": self.valid_rows,
            "session_count": len(self.sessions),
            "files": self.files,
            "sessions": sorted(self.sessions),
            "streams": [s.summary(gap_multiplier) for _, s in sorted(self.streams.items())],
            "issues": [asdict(issue) for issue in self.issues],
        }


def _issue(severity: str, code: str, message: str, event: Event) -> Issue:
    return Issue(severity, code, message, event.source_file, event.row_number, event.session_id, event.sensor_type)


def _number(raw: str, name: str, *, integer: bool = False) -> int | float:
    value = int(raw) if integer else float(raw)
    if isinstance(value, float) and not math.isfinite(value):
        raise ValueError(f"{name} must be finite")
    return value


def _parse_row(row: dict[str, str | None], source: str, row_number: int) -> tuple[Event | None, list[Issue]]:
    issues: list[Issue] = []
    session_id = row.get("session_id") or ""
    sensor_type = row.get("sensor_type") or ""
    if not session_id.strip() or any(ch in session_id for ch in "\r\n\x00"):
        issues.append(Issue("error", "invalid_session_id", "Session ID is empty or contains a control character.", source, row_number, session_id or None, sensor_type or None, dict(row)))
    if sensor_type.upper() not in SUPPORTED_SENSORS:
        issues.append(Issue("error", "unsupported_sensor_type", f"Unsupported sensor type: {sensor_type or '<empty>'}.", source, row_number, session_id or None, sensor_type or None, dict(row)))
    try:
        ts = int((row.get("timestamp_nanos") or "").strip())
        if ts < 0:
            raise ValueError("timestamp_nanos must be non-negative")
    except ValueError as exc:
        issues.append(Issue("error", "invalid_timestamp_nanos", str(exc), source, row_number, session_id or None, sensor_type or None, dict(row)))
        ts = None
    try:
        epoch = int((row.get("timestamp_epoch_millis") or "").strip())
        if epoch < 0:
            raise ValueError("timestamp_epoch_millis must be non-negative")
    except ValueError as exc:
        issues.append(Issue("error", "invalid_timestamp_epoch_millis", str(exc), source, row_number, session_id or None, sensor_type or None, dict(row)))
        epoch = None
    values: list[float] = []
    for name in ("x", "y", "z"):
        try:
            values.append(float(_number((row.get(name) or "").strip(), name)))
        except ValueError as exc:
            issues.append(Issue("error", f"invalid_{name}", str(exc), source, row_number, session_id or None, sensor_type or None, dict(row)))
            values.append(float("nan"))
    raw_accuracy = (row.get("accuracy") or "").strip()
    accuracy: int | None
    if not raw_accuracy:
        accuracy = None
        issues.append(Issue("warning", "missing_accuracy", "Accuracy is unavailable.", source, row_number, session_id or None, sensor_type or None, dict(row)))
    else:
        try:
            accuracy = int(raw_accuracy)
            if accuracy < 0:
                raise ValueError("accuracy must be non-negative or empty when unavailable")
        except ValueError as exc:
            issues.append(Issue("error", "invalid_accuracy", str(exc), source, row_number, session_id or None, sensor_type or None, dict(row)))
            accuracy = None
    if issues and any(i.severity == "error" for i in issues):
        return None, issues
    assert ts is not None and epoch is not None
    return Event(source, row_number, session_id, sensor_type, ts, epoch, values[0], values[1], values[2], accuracy), issues


def iter_csv(path: Path, inspection: Inspection, *, valid_sink=None) -> Iterator[Event]:
    """Yield valid events one row at a time; malformed rows are retained as issues."""
    source = path.name
    file_info: dict[str, Any] = {"source_file": source, "rows": 0, "valid_rows": 0, "invalid_rows": 0, "extra_columns": [], "missing_columns": []}
    inspection.files.append(file_info)
    try:
        with path.open("r", encoding="utf-8-sig", newline="") as handle:
            reader = csv.DictReader(handle)
            if reader.fieldnames is None:
                inspection.issues.append(Issue("error", "empty_file", "CSV is empty or has no header.", source))
                file_info["empty"] = True
                return
            names = reader.fieldnames
            duplicates = sorted({name for name in names if names.count(name) > 1})
            if duplicates:
                file_info["duplicate_columns"] = duplicates
                for name in duplicates:
                    inspection.issues.append(Issue("error", "duplicate_column", f"Header contains duplicate column: {name}.", source))
                return
            file_info["missing_columns"] = [c for c in EXPECTED_COLUMNS if c not in names]
            file_info["extra_columns"] = [c for c in names if c not in EXPECTED_COLUMNS]
            for col in file_info["missing_columns"]:
                inspection.issues.append(Issue("error", "missing_column", f"Required column missing: {col}.", source))
            for col in file_info["extra_columns"]:
                inspection.issues.append(Issue("warning", "extra_column", f"Unexpected column present: {col}.", source))
            if file_info["missing_columns"]:
                return
            for row_number, row in enumerate(reader, start=2):
                file_info["rows"] += 1
                if None in row:
                    file_info["invalid_rows"] += 1
                    inspection.issues.append(Issue("error", "extra_fields", "Row has more fields than the header.", source, row_number, record={"extra_fields": row[None]}))
                    continue
                event, issues = _parse_row(row, source, row_number)
                inspection.issues.extend(issues)
                if event is None:
                    file_info["invalid_rows"] += 1
                    targets = {(issue.session_id, issue.sensor_type.upper()) for issue in issues if issue.session_id and issue.sensor_type and issue.sensor_type.upper() in SUPPORTED_SENSORS}
                    for target in targets:
                        inspection.streams.setdefault(target, StreamStats(*target)).invalid_rows += 1
                    continue
                file_info["valid_rows"] += 1
                inspection.add(event)
                if valid_sink is not None:
                    valid_sink(event)
                yield event
    except (OSError, UnicodeError, csv.Error) as exc:
        inspection.issues.append(Issue("error", "file_read_error", f"Unable to read CSV: {exc}", source))


def inspect_files(paths: Iterable[Path], *, gap_multiplier: float = 3.0, suspicious_abs_limit: float | None = None, valid_sink=None) -> Inspection:
    if gap_multiplier <= 1:
        raise ValueError("gap_multiplier must be greater than 1")
    paths = list(paths)
    inspection = Inspection()
    for path in paths:
        # Exhaust each streaming iterator while discarding yielded rows.
        for _ in iter_csv(path, inspection, valid_sink=valid_sink):
            pass
    if suspicious_abs_limit is not None:
        if suspicious_abs_limit <= 0:
            raise ValueError("suspicious_abs_limit must be positive")
        # A user-supplied bound is a review flag only. Records remain exported unchanged.
        for path in paths:
            try:
                with path.open("r", encoding="utf-8-sig", newline="") as handle:
                    reader = csv.DictReader(handle)
                    if not reader.fieldnames or any(column not in reader.fieldnames for column in EXPECTED_COLUMNS):
                        continue
                    for row_number, row in enumerate(reader, start=2):
                        try:
                            vals = [float(row[name]) for name in ("x", "y", "z")]
                        except (TypeError, ValueError):
                            continue
                        if any(math.isfinite(value) and abs(value) > suspicious_abs_limit for value in vals):
                            inspection.issues.append(Issue("warning", "suspicious_numeric_magnitude", f"An axis exceeds the user-supplied review bound {suspicious_abs_limit:g}; value retained.", path.name, row_number, row.get("session_id"), row.get("sensor_type"), {name: row[name] for name in ("x", "y", "z")}))
            except (OSError, UnicodeError, csv.Error):
                pass
    # Empty data files (header only) are distinguished from valid zero-event sessions.
    for info in inspection.files:
        if not info.get("empty") and info["rows"] == 0:
            inspection.issues.append(Issue("warning", "no_data_rows", "CSV contains a header but no data rows.", info["source_file"]))
    return inspection


def write_markdown(report: dict[str, Any], destination: Path) -> None:
    lines = ["# VahakVigil Sensor Dataset Inspection", "", f"Generated: {report['generated_at_utc']}", "",
             f"Files: {report['file_count']}  |  Sessions: {report['session_count']}  |  Valid events: {report['valid_event_count']}", "",
             "## Session and sensor stream statistics", "", "| Session | Sensor | Events | Duration (s) | Avg Hz | Median interval (ms) | Gaps | Duplicates | Non-monotonic | Invalid | Missing accuracy |", "|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|"]
    for s in report["streams"]:
        def fmt(value: Any) -> str:
            return "—" if value is None else f"{value:.3f}" if isinstance(value, float) else str(value)
        lines.append("| " + " | ".join(fmt(s[k]) for k in ("session_id", "sensor_type", "total_events", "recording_duration_seconds", "average_sampling_frequency_hz", "median_sampling_interval_ms", "timestamp_gaps", "duplicate_timestamps", "non_monotonic_timestamps", "invalid_rows", "missing_accuracy")) + " |")
    lines.extend(["", "## Validation issues", "", f"Total issues: {len(report['issues'])}", ""])
    for issue in report["issues"]:
        where = issue["source_file"] + (f":{issue['row_number']}" if issue.get("row_number") else "")
        lines.append(f"- **{issue['severity']} {issue['code']}** ({where}): {issue['message']}")
    if not report["issues"]:
        lines.append("No structural or row-level issues detected.")
    lines.extend(["", f"Sampling rates and intervals are derived from observed per-sensor hardware timestamp deltas. They are descriptive, not requested-rate guarantees. Gaps are flagged when an interval exceeds {report['gap_multiplier_of_stream_median']:g} times that stream's median positive interval. Suspicious magnitudes are not filtered or automatically labelled.", ""])
    destination.write_text("\n".join(lines), encoding="utf-8", newline="\n")
