"""Unit tests use deliberately synthetic CSV rows and are not ride recordings."""

from __future__ import annotations

import csv
import json
import tempfile
import unittest
from types import SimpleNamespace
from pathlib import Path

from ml_dataset.annotations import empty_manifest, validate_manifest
from ml_dataset.cli import command_prepare, command_review_outcome
from ml_dataset.core import EXPECTED_COLUMNS, inspect_files
from ml_dataset.splits import assign_session_splits
from ml_dataset.ground_truth import evaluate_dataset


def row(session="session-a", sensor="ACCELEROMETER", nanos=1_000_000_000, epoch=1_700_000_000_000, x="1.0", y="2", z="3", accuracy="3"):
    return [session, sensor, str(nanos), str(epoch), str(x), str(y), str(z), str(accuracy)]


def write_csv(path: Path, rows, header=EXPECTED_COLUMNS):
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, lineterminator="\n")
        writer.writerow(header)
        writer.writerows(rows)


class IngestionTests(unittest.TestCase):
    def test_valid_multiple_sessions_and_source_filename(self):
        with tempfile.TemporaryDirectory() as tmp:
            p1, p2 = Path(tmp) / "ride1.csv", Path(tmp) / "ride2.csv"
            write_csv(p1, [row()])
            write_csv(p2, [row("session-b", "GYROSCOPE", 2_000_000_000)])
            result = inspect_files([p1, p2])
            self.assertEqual(result.sessions, {"session-a", "session-b"})
            self.assertEqual(result.valid_rows, 2)
            self.assertEqual([f["source_file"] for f in result.files], ["ride1.csv", "ride2.csv"])
            self.assertEqual({key[1] for key in result.streams}, {"ACCELEROMETER", "GYROSCOPE"})

    def test_missing_columns_extra_columns_empty_and_header_only(self):
        with tempfile.TemporaryDirectory() as tmp:
            missing, extra, empty, header_only = (Path(tmp) / name for name in ("missing.csv", "extra.csv", "empty.csv", "header.csv"))
            write_csv(missing, [row()], EXPECTED_COLUMNS[:-1])
            write_csv(extra, [row() + ["unexpected"]], EXPECTED_COLUMNS + ("extra",))
            empty.write_text("", encoding="utf-8")
            write_csv(header_only, [])
            result = inspect_files([missing, extra, empty, header_only])
            codes = {issue.code for issue in result.issues}
            self.assertIn("missing_column", codes)
            self.assertIn("extra_column", codes)
            self.assertIn("empty_file", codes)
            self.assertIn("no_data_rows", codes)
            self.assertEqual(result.valid_rows, 1)

    def test_invalid_sensor_numeric_nan_infinity_and_accuracy_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "bad.csv"
            write_csv(path, [row(sensor="MAGNETOMETER"), row("s", x="not-a-number"), row("s", x="NaN"), row("s", y="Infinity"), row("s", accuracy="-1")])
            result = inspect_files([path])
            codes = {issue.code for issue in result.issues}
            self.assertIn("unsupported_sensor_type", codes)
            self.assertIn("invalid_x", codes)
            self.assertIn("invalid_y", codes)
            self.assertIn("invalid_accuracy", codes)
            self.assertEqual(result.valid_rows, 0)
            self.assertEqual(sum(s.invalid_rows for s in result.streams.values()), 4)

    def test_duplicate_and_non_monotonic_timestamps_are_per_sensor(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "streams.csv"
            write_csv(path, [
                row("s", "ACCELEROMETER", 10), row("s", "GYROSCOPE", 10),
                row("s", "ACCELEROMETER", 10), row("s", "ACCELEROMETER", 9),
            ])
            result = inspect_files([path])
            accel = result.streams[("s", "ACCELEROMETER")]
            gyro = result.streams[("s", "GYROSCOPE")]
            self.assertEqual(accel.duplicate_timestamps, 1)
            self.assertEqual(accel.non_monotonic_timestamps, 2)
            self.assertEqual(gyro.duplicate_timestamps, 0)
            self.assertEqual(gyro.non_monotonic_timestamps, 0)

    def test_sampling_frequency_gaps_epoch_relationship_and_accuracy_stats(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "timed.csv"
            write_csv(path, [
                row("s", "ACCELEROMETER", 0, 1000, accuracy=""),
                row("s", "ACCELEROMETER", 10_000_000, 1010),
                row("s", "ACCELEROMETER", 20_000_000, 1020),
                row("s", "ACCELEROMETER", 100_000_000, 900),
            ])
            result = inspect_files([path], gap_multiplier=3)
            summary = result.streams[("s", "ACCELEROMETER")].summary(3)
            self.assertAlmostEqual(summary["average_sampling_frequency_hz"], 30)
            self.assertAlmostEqual(summary["median_sampling_interval_ms"], 10)
            self.assertEqual(summary["timestamp_gaps"], 1)
            self.assertEqual(summary["missing_accuracy"], 1)
            self.assertTrue(any(issue.code == "epoch_timestamp_non_monotonic" for issue in result.issues))

    def test_suspicious_bound_only_flags_and_retains_record(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "large.csv"
            write_csv(path, [row(x="999")])
            result = inspect_files([path], suspicious_abs_limit=100)
            self.assertEqual(result.valid_rows, 1)
            self.assertTrue(any(issue.code == "suspicious_numeric_magnitude" for issue in result.issues))


class AnnotationAndSplitTests(unittest.TestCase):
    def test_empty_manifest_keeps_sessions_unreviewed_not_normal(self):
        manifest = empty_manifest({"s1", "s2"})
        result = validate_manifest(manifest, {"s1", "s2"})
        self.assertEqual(result.unreviewed_sessions, ["s1", "s2"])
        self.assertEqual(result.valid[0]["labels"], [])

    def test_explicit_outcomes_require_provenance_and_simulation_cannot_be_crash(self):
        from ml_dataset.annotations import validate_binary_outcome
        base = {"session_id": "s1", "review_id": "r1", "reviewer_id": "reviewer", "reviewed_at_utc": "2025-01-01T00:00:00Z", "evidence_provenance": "reviewed incident report", "recording_origin": "REAL"}
        for outcome in ("CRASH_CONFIRMED", "NO_CRASH_CONFIRMED", "INDETERMINATE"):
            normalized, errors = validate_binary_outcome({**base, "outcome": outcome}, "s1")
            self.assertFalse(errors)
            self.assertEqual(normalized["outcome"], outcome)
        bad, errors = validate_binary_outcome({**base, "outcome": "CRASH_CONFIRMED", "reviewer_id": "", "recording_origin": "SIMULATED"}, "s1")
        self.assertIsNone(bad)
        self.assertGreaterEqual(len(errors), 2)

    def test_duplicate_session_reviews_are_excluded_and_context_labels_do_not_infer_outcome(self):
        manifest = {"schema_version": 2, "sessions": [
            {"session_id": "s1", "review_status": "REVIEWED", "labels": [{"label": "SIMULATED_IMPACT", "level": "SESSION", "annotation_source": "test", "event_origin": "SIMULATED"}], "binary_outcome": None},
            {"session_id": "s1", "review_status": "REVIEWED", "labels": [], "binary_outcome": None},
        ]}
        result = validate_manifest(manifest, {"s1"})
        self.assertIn("s1", result.invalid_session_ids)
        self.assertEqual(result.valid, [])
        self.assertIn("s1", result.unreviewed_sessions)

    def test_superseded_review_history_must_be_linked(self):
        from ml_dataset.annotations import validate_binary_outcome
        old = {"session_id": "s1", "review_id": "old", "outcome": "NO_CRASH_CONFIRMED", "reviewer_id": "r", "reviewed_at_utc": "2025-01-01T00:00:00Z", "evidence_provenance": "report", "recording_origin": "REAL", "superseded_at_utc": "2025-01-02T00:00:00Z", "superseded_by_review_id": "wrong", "revision_reason": "new evidence"}
        current = {"session_id": "s1", "review_id": "new", "outcome": "CRASH_CONFIRMED", "reviewer_id": "r", "reviewed_at_utc": "2025-01-02T00:00:00Z", "evidence_provenance": "report", "recording_origin": "REAL", "audit_history": [old]}
        normalized, errors = validate_binary_outcome(current, "s1")
        self.assertIsNone(normalized)
        self.assertTrue(any("linked" in item["error"] for item in errors))

    def test_unknown_label_and_multiple_event_labels_are_valid(self):
        document = {"schema_version": 1, "sessions": [{
            "session_id": "s1", "review_status": "REVIEWED", "labels": [
                {"label": "UNKNOWN", "level": "EVENT", "event_start_timestamp_nanos": 1, "event_end_timestamp_nanos": 2, "event_origin": "UNKNOWN", "annotation_source": "manual-review", "review_status": "REVIEWED"},
                {"label": "BRAKING", "level": "EVENT", "event_start_timestamp_nanos": 3, "event_end_timestamp_nanos": 4, "event_origin": "REAL", "annotation_source": "rider-report", "confidence": 0.9, "review_status": "REVIEWED"},
            ],
        }]}
        result = validate_manifest(document, {"s1"})
        self.assertEqual(result.errors, [])
        self.assertEqual(len(result.valid[0]["labels"]), 2)

    def test_invalid_ranges_labels_and_unknown_session_are_rejected(self):
        document = {"schema_version": 1, "sessions": [{"session_id": "missing", "review_status": "REVIEWED", "labels": []}, {
            "session_id": "s1", "review_status": "REVIEWED", "labels": [{"label": "NOT_A_LABEL", "level": "EVENT", "event_start_timestamp_nanos": 20, "event_end_timestamp_nanos": 10, "event_origin": "REAL", "annotation_source": "manual"}],
        }]}
        result = validate_manifest(document, {"s1"})
        self.assertGreaterEqual(len(result.errors), 3)
        self.assertEqual(result.valid[0]["labels"], [])

    def test_session_split_isolation_and_small_dataset_refusal(self):
        small = assign_session_splits({"s1", "s2"})
        self.assertFalse(small["created"])
        result = assign_session_splits({"s1", "s2", "s3", "s4", "s5", "s6"})
        self.assertTrue(result["created"])
        sets = [set(result[key]) for key in ("training", "validation", "test")]
        self.assertEqual(sum(map(len, sets)), 6)
        self.assertFalse(sets[0] & sets[1] or sets[0] & sets[2] or sets[1] & sets[2])


class PreparationTests(unittest.TestCase):
    def test_prepare_preserves_raw_and_reports_rejected_rows_and_unreviewed_sessions(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            raw_dir, output = root / "raw", root / "prepared"
            raw_dir.mkdir()
            raw = raw_dir / "ride.csv"
            write_csv(raw, [row(), row("s2", x="bad")])
            original = raw.read_bytes()
            args = SimpleNamespace(inputs=[str(raw)], manifest=None, output=str(output), gap_multiplier=3.0, minimum_sessions_per_split=1)
            self.assertEqual(command_prepare(args), 1)
            self.assertEqual(raw.read_bytes(), original)
            event_rows = (output / "validated_events.jsonl").read_text(encoding="utf-8").splitlines()
            rejected = (output / "rejected_rows.jsonl").read_text(encoding="utf-8").splitlines()
            self.assertEqual(len(event_rows), 1)
            self.assertEqual(len(rejected), 1)
            metadata = json.loads((output / "session_metadata.json").read_text(encoding="utf-8"))
            self.assertEqual(metadata[0]["annotation"]["review_status"], "UNREVIEWED")
            self.assertFalse(json.loads((output / "session_splits.json").read_text(encoding="utf-8"))["created"])

    def test_prepare_refuses_output_inside_raw_directory_and_nonempty_destination(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            rawdir = root / "raw"
            rawdir.mkdir()
            source = rawdir / "one.csv"
            write_csv(source, [row()])
            args = SimpleNamespace(inputs=[str(rawdir)], manifest=None, output=str(rawdir / "prepared"), gap_multiplier=3.0, minimum_sessions_per_split=1)
            with self.assertRaises(ValueError):
                command_prepare(args)


class EvaluationTests(unittest.TestCase):
    def test_review_command_writes_new_manifest_and_preserves_revision_audit(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            raw_dir = root / "raw"
            raw_dir.mkdir()
            raw = raw_dir / "ride.csv"
            manifest = root / "annotations.json"
            first = root / "reviewed-1.json"
            second = root / "reviewed-2.json"
            write_csv(raw, [row("session-a")])
            manifest.write_text(json.dumps(empty_manifest({"session-a"})), encoding="utf-8")
            common = dict(inputs=[str(raw)], manifest=str(manifest), session_id="session-a", outcome="NO_CRASH_CONFIRMED",
                          reviewer_id="pseudo", reviewed_at="2025-01-01T00:00:00Z", evidence_provenance="reviewed report",
                          recording_origin="REAL", notes=None, supersede=False, revision_reason=None)
            self.assertEqual(command_review_outcome(SimpleNamespace(**common, output=str(first))), 0)
            first_doc = json.loads(first.read_text(encoding="utf-8"))
            self.assertEqual(first_doc["sessions"][0]["binary_outcome"]["outcome"], "NO_CRASH_CONFIRMED")
            common["outcome"] = "INDETERMINATE"
            common["supersede"] = True
            common["revision_reason"] = "new reviewed evidence"
            common["reviewed_at"] = "2025-01-02T00:00:00Z"
            revised_args = {**common, "manifest": str(first), "output": str(second)}
            self.assertEqual(command_review_outcome(SimpleNamespace(**revised_args)), 0)
            revised = json.loads(second.read_text(encoding="utf-8"))["sessions"][0]["binary_outcome"]
            self.assertEqual(len(revised["audit_history"]), 1)
            self.assertEqual(revised["audit_history"][0]["outcome"], "NO_CRASH_CONFIRMED")
            self.assertEqual(revised["audit_history"][0]["superseded_by_review_id"], revised["review_id"])

    def test_metrics_require_reviewed_real_annotation_and_matching_observed_report(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "observed.csv"
            write_csv(path, [row("s1")])
            inspection = inspect_files([path])
            truth = {"session_id": "s1", "review_id": "r1", "outcome": "CRASH_CONFIRMED", "reviewer_id": "pseudonym", "reviewed_at_utc": "2025-01-01T00:00:00Z", "evidence_provenance": "reviewed report", "recording_origin": "REAL", "audit_history": []}
            manifest = {"schema_version": 2, "sessions": [{"session_id": "s1", "review_status": "REVIEWED", "labels": [], "binary_outcome": truth}]}
            annotations = validate_manifest(manifest, inspection.sessions)
            report = {"schema_version": 1, "session_id": "s1", "provenance": "OBSERVED", "outcome": "EMERGENCY_CANDIDATE", "final_countdown_state": "IDLE", "ground_truth": {**truth, "review_status": "REVIEWED"}, "detection_latency_millis": 250}
            result = evaluate_dataset(inspection, annotations, {"s1": report})
            self.assertEqual(result["confusion_counts"]["true_positives"], 1)
            self.assertEqual(result["precision"], 1.0)
            self.assertEqual(result["recall"], 1.0)
            self.assertEqual(result["mean_detection_latency_millis"], 250)
            self.assertEqual(result["eligible_sessions"][0]["session_id"], "s1")

    def test_indeterminate_and_simulated_sessions_are_excluded(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "observed.csv"
            write_csv(path, [row("indeterminate"), row("simulated", "GYROSCOPE", 2_000_000_000)])
            inspection = inspect_files([path])
            def truth(sid, outcome, origin):
                return {"session_id": sid, "review_id": sid + "-r", "outcome": outcome, "reviewer_id": "p", "reviewed_at_utc": "2025-01-01T00:00:00Z", "evidence_provenance": "review", "recording_origin": origin, "audit_history": []}
            manifest = {"schema_version": 2, "sessions": [
                {"session_id": "indeterminate", "review_status": "REVIEWED", "labels": [], "binary_outcome": truth("indeterminate", "INDETERMINATE", "REAL")},
                {"session_id": "simulated", "review_status": "REVIEWED", "labels": [], "binary_outcome": truth("simulated", "NO_CRASH_CONFIRMED", "SIMULATED")},
            ]}
            annotations = validate_manifest(manifest, inspection.sessions)
            reports = {sid: {"schema_version": 1, "session_id": sid, "provenance": "OBSERVED", "outcome": "NO_CANDIDATE", "final_countdown_state": "IDLE", "ground_truth": {**entry["binary_outcome"], "review_status": "REVIEWED"}} for sid, entry in [(x["session_id"], x) for x in annotations.valid]}
            result = evaluate_dataset(inspection, annotations, reports)
            self.assertEqual(result["eligible_sessions"], [])
            self.assertEqual(len(result["excluded_sessions"]), 2)

    def test_session_splits_never_split_rows_and_invalid_file_is_excluded(self):
        splits = assign_session_splits({"a", "b", "c", "d", "e", "f"})
        groups = [set(splits[key]) for key in ("training", "validation", "test")]
        self.assertFalse(groups[0] & groups[1] or groups[0] & groups[2] or groups[1] & groups[2])


if __name__ == "__main__":
    unittest.main()
