"""Tests for privacy-safe sensor dataset package (ZIP) validation and ingestion."""

from __future__ import annotations

import csv
import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
import uuid

from ml_dataset.core import EXPECTED_COLUMNS
from ml_dataset.packages import validate_package


def create_synthetic_package(
    dest_path: Path,
    dataset_session_id: str | None = None,
    csv_rows: list[list[str]] | None = None,
    schema_version: int = 1,
    provenance: str = "REAL",
    app_version: str = "1.0.0",
    manifest_name: str = "dataset_manifest.json",
    csv_name: str = "recording.csv",
    extra_manifest_fields: dict | None = None,
    omit_manifest: bool = False,
    omit_csv: bool = False,
    corrupt_manifest: bool = False,
) -> str:
    sid = dataset_session_id or str(uuid.uuid4())
    if csv_rows is None:
        csv_rows = [
            [sid, "ACCELEROMETER", "1000000000", "1700000000000", "0.1", "9.8", "0.2", "3"],
            [sid, "GYROSCOPE", "1000000000", "1700000000000", "0.01", "0.02", "0.03", "3"]
        ]

    manifest_data = {
        "package_schema_version": schema_version,
        "dataset_session_id": sid,
        "app_version": app_version,
        "device_model": "TestDevice",
        "recording_start_epoch_millis": 1700000000000,
        "recording_end_epoch_millis": 1700000010000,
        "recording_provenance": provenance,
        "sensor_types": ["ACCELEROMETER", "GYROSCOPE"],
        "sampling": {
            "ACCELEROMETER": {"event_count": 1, "mean_sampling_frequency_hz": 50.0},
            "GYROSCOPE": {"event_count": 1, "mean_sampling_frequency_hz": 50.0}
        },
        "recording_quality": {
            "event_count": len(csv_rows),
            "duplicate_timestamp_events": 0,
            "non_monotonic_timestamps": 0,
            "dropped_samples": None,
            "dropped_samples_unavailable_reason": "per-ride dropped counts are not persisted"
        },
        "consent": {
            "explicit": True,
            "consented_at_epoch_millis": 1699999900000,
            "exported_at_epoch_millis": 1700000020000,
            "export_is_user_initiated": True
        },
        "files": [csv_name]
    }
    if extra_manifest_fields:
        manifest_data.update(extra_manifest_fields)

    with zipfile.ZipFile(dest_path, "w") as zf:
        if not omit_manifest:
            if corrupt_manifest:
                zf.writestr(manifest_name, "{ corrupt json")
            else:
                zf.writestr(manifest_name, json.dumps(manifest_data, indent=2))
        if not omit_csv:
            csv_buf = io.StringIO()
            writer = csv.writer(csv_buf, lineterminator="\n")
            writer.writerow(EXPECTED_COLUMNS)
            writer.writerows(csv_rows)
            zf.writestr(csv_name, csv_buf.getvalue())

    return sid


class PackageValidationTests(unittest.TestCase):
    def test_valid_package_ingestion(self):
        with tempfile.TemporaryDirectory() as tmp:
            pkg = Path(tmp) / "valid.zip"
            expected_id = create_synthetic_package(pkg)
            result = validate_package(pkg)
            self.assertTrue(result.is_valid)
            self.assertEqual(result.dataset_session_id, expected_id)
            self.assertEqual(result.manifest["recording_provenance"], "REAL")
            self.assertEqual(len(result.errors), 0)

    def test_missing_manifest_or_csv_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            pkg_no_manifest = Path(tmp) / "no_manifest.zip"
            create_synthetic_package(pkg_no_manifest, omit_manifest=True)
            res1 = validate_package(pkg_no_manifest)
            self.assertFalse(res1.is_valid)
            self.assertTrue(any("missing required manifest" in e for e in res1.errors))

            pkg_no_csv = Path(tmp) / "no_csv.zip"
            create_synthetic_package(pkg_no_csv, omit_csv=True)
            res2 = validate_package(pkg_no_csv)
            self.assertFalse(res2.is_valid)
            self.assertTrue(any("missing required sensor data CSV" in e for e in res2.errors))

    def test_corrupt_manifest_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            pkg = Path(tmp) / "corrupt.zip"
            create_synthetic_package(pkg, corrupt_manifest=True)
            result = validate_package(pkg)
            self.assertFalse(result.is_valid)
            self.assertTrue(any("Malformed manifest JSON" in e for e in result.errors))

    def test_unsupported_schema_version_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            pkg = Path(tmp) / "bad_ver.zip"
            create_synthetic_package(pkg, schema_version=99)
            result = validate_package(pkg)
            self.assertFalse(result.is_valid)
            self.assertTrue(any("Unsupported package_schema_version" in e for e in result.errors))

    def test_invalid_dataset_id_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            pkg = Path(tmp) / "bad_id.zip"
            create_synthetic_package(pkg, dataset_session_id="not-a-valid-uuid")
            result = validate_package(pkg)
            self.assertFalse(result.is_valid)
            self.assertTrue(any("Invalid dataset_session_id" in e for e in result.errors))

    def test_duplicate_dataset_id_detected(self):
        with tempfile.TemporaryDirectory() as tmp:
            fixed_id = str(uuid.uuid4())
            pkg1 = Path(tmp) / "pkg1.zip"
            pkg2 = Path(tmp) / "pkg2.zip"
            create_synthetic_package(pkg1, dataset_session_id=fixed_id)
            create_synthetic_package(pkg2, dataset_session_id=fixed_id)

            known = set()
            r1 = validate_package(pkg1, known)
            self.assertTrue(r1.is_valid)
            self.assertIn(fixed_id, known)

            r2 = validate_package(pkg2, known)
            self.assertFalse(r2.is_valid)
            self.assertTrue(any("Duplicate dataset_session_id" in e for e in r2.errors))

    def test_invalid_recording_origin_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            pkg = Path(tmp) / "bad_origin.zip"
            create_synthetic_package(pkg, provenance="INVALID_ORIGIN")
            result = validate_package(pkg)
            self.assertFalse(result.is_valid)
            self.assertTrue(any("Invalid recording_provenance" in e for e in result.errors))

    def test_forbidden_pii_fields_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            pkg_gps = Path(tmp) / "gps_leak.zip"
            create_synthetic_package(pkg_gps, extra_manifest_fields={"gps": [12.34, 56.78]})
            res1 = validate_package(pkg_gps)
            self.assertFalse(res1.is_valid)
            self.assertTrue(any("forbidden personally identifying" in e for e in res1.errors))

            pkg_name = Path(tmp) / "name_leak.zip"
            create_synthetic_package(pkg_name, extra_manifest_fields={"rider_name": "Alice"})
            res2 = validate_package(pkg_name)
            self.assertFalse(res2.is_valid)
            self.assertTrue(any("forbidden personally identifying" in e for e in res2.errors))

    def test_csv_and_manifest_id_mismatch_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            pkg = Path(tmp) / "mismatch.zip"
            other_id = str(uuid.uuid4())
            create_synthetic_package(
                pkg,
                csv_rows=[[other_id, "ACCELEROMETER", "1000", "1700", "1", "2", "3", "3"]]
            )
            result = validate_package(pkg)
            self.assertFalse(result.is_valid)
            self.assertTrue(any("does not match manifest" in e for e in result.errors))

    def test_recording_quality_metadata_preserved(self):
        with tempfile.TemporaryDirectory() as tmp:
            pkg = Path(tmp) / "quality.zip"
            create_synthetic_package(pkg)
            result = validate_package(pkg)
            self.assertTrue(result.is_valid)
            self.assertIn("recording_quality", result.manifest)
            q = result.manifest["recording_quality"]
            self.assertEqual(q["dropped_samples_unavailable_reason"], "per-ride dropped counts are not persisted")
            self.assertIn("sampling", result.manifest)

    def test_duplicate_recording_content_detected_across_distinct_dataset_ids(self):
        with tempfile.TemporaryDirectory() as tmp:
            pkg1 = Path(tmp) / "export_1.zip"
            pkg2 = Path(tmp) / "export_2.zip"
            # Two distinct UUIDs simulating repeated exports of the same ride
            id1 = str(uuid.uuid4())
            id2 = str(uuid.uuid4())
            self.assertNotEqual(id1, id2)

            rows1 = [
                [id1, "ACCELEROMETER", "1000", "1700", "0.5", "9.8", "-0.1", "3"],
                [id1, "GYROSCOPE", "1000", "1700", "0.01", "0.02", "0.03", "3"],
            ]
            rows2 = [
                [id2, "ACCELEROMETER", "1000", "1700", "0.5", "9.8", "-0.1", "3"],
                [id2, "GYROSCOPE", "1000", "1700", "0.01", "0.02", "0.03", "3"],
            ]
            create_synthetic_package(pkg1, dataset_session_id=id1, csv_rows=rows1)
            create_synthetic_package(pkg2, dataset_session_id=id2, csv_rows=rows2)

            known_ids = set()
            known_hashes = set()

            res1 = validate_package(pkg1, known_dataset_ids=known_ids, known_content_hashes=known_hashes)
            self.assertTrue(res1.is_valid)
            self.assertIsNotNone(res1.sensor_content_hash)

            # Second package has a unique dataset ID, but identical sensor content
            res2 = validate_package(pkg2, known_dataset_ids=known_ids, known_content_hashes=known_hashes)
            self.assertFalse(res2.is_valid)
            self.assertTrue(any("Duplicate recording content detected" in e for e in res2.errors))

    def test_genuinely_different_recordings_pass_content_hash_check(self):
        with tempfile.TemporaryDirectory() as tmp:
            pkg1 = Path(tmp) / "ride_a.zip"
            pkg2 = Path(tmp) / "ride_b.zip"
            id1 = str(uuid.uuid4())
            id2 = str(uuid.uuid4())

            rows1 = [[id1, "ACCELEROMETER", "1000", "1700", "0.5", "9.8", "-0.1", "3"]]
            rows2 = [[id2, "ACCELEROMETER", "2000", "1800", "1.2", "8.5", "0.4", "3"]]

            create_synthetic_package(pkg1, dataset_session_id=id1, csv_rows=rows1)
            create_synthetic_package(pkg2, dataset_session_id=id2, csv_rows=rows2)

            known_ids = set()
            known_hashes = set()

            res1 = validate_package(pkg1, known_dataset_ids=known_ids, known_content_hashes=known_hashes)
            res2 = validate_package(pkg2, known_dataset_ids=known_ids, known_content_hashes=known_hashes)

            self.assertTrue(res1.is_valid)
            self.assertTrue(res2.is_valid)
            self.assertNotEqual(res1.sensor_content_hash, res2.sensor_content_hash)

    def test_manifest_sensor_content_hash_mismatch_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            pkg = Path(tmp) / "tampered.zip"
            create_synthetic_package(pkg, extra_manifest_fields={"sensor_content_hash": "sha256:badhash12345"})
            res = validate_package(pkg)
            self.assertFalse(res.is_valid)
            self.assertTrue(any("Manifest sensor_content_hash" in e and "does not match" in e for e in res.errors))


if __name__ == "__main__":
    unittest.main()
