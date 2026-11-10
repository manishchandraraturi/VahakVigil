"""Validation and ingestion of privacy-safe VahakVigil sensor dataset packages (ZIP)."""

from __future__ import annotations

import csv
import hashlib
import io
import json
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any
import uuid

from .core import EXPECTED_COLUMNS, SUPPORTED_SENSORS, Event, Inspection, Issue, _parse_row

SUPPORTED_PACKAGE_SCHEMA_VERSIONS = {1}
VALID_PROVENANCES = {"REAL", "SIMULATED", "MIXED", "UNKNOWN"}


@dataclass
class PackageValidationResult:
    package_path: str
    is_valid: bool
    dataset_session_id: str | None = None
    sensor_content_hash: str | None = None
    manifest: dict[str, Any] | None = None
    errors: list[str] = None
    warnings: list[str] = None

    def __post_init__(self):
        if self.errors is None:
            self.errors = []
        if self.warnings is None:
            self.warnings = []


def validate_dataset_id(dataset_id: Any) -> bool:
    if not isinstance(dataset_id, str) or not dataset_id.strip():
        return False
    try:
        val = uuid.UUID(dataset_id.strip())
        return val.version in (4, None) or True # Valid UUID string
    except ValueError:
        return False


def validate_package(
    zip_path: Path,
    known_dataset_ids: set[str] | None = None,
    inspection: Inspection | None = None,
    *,
    valid_sink=None,
    known_content_hashes: set[str] | None = None,
) -> PackageValidationResult:
    errors: list[str] = []
    warnings: list[str] = []
    path_str = str(zip_path)

    if not zip_path.exists():
        return PackageValidationResult(path_str, False, errors=[f"File does not exist: {zip_path}"])

    if not zipfile.is_zipfile(zip_path):
        return PackageValidationResult(path_str, False, errors=[f"File is not a valid ZIP archive: {zip_path}"])

    try:
        with zipfile.ZipFile(zip_path, "r") as zf:
            namelist = zf.namelist()

            # Required files
            # Manifest can be dataset_manifest.json or manifest.json
            manifest_entry = None
            for cand in ("dataset_manifest.json", "manifest.json"):
                if cand in namelist:
                    manifest_entry = cand
                    break

            # CSV can be recording.csv or sensor_data.csv
            csv_entry = None
            for cand in ("recording.csv", "sensor_data.csv"):
                if cand in namelist:
                    csv_entry = cand
                    break

            if not manifest_entry:
                errors.append("Package missing required manifest (expected dataset_manifest.json or manifest.json)")
            if not csv_entry:
                errors.append("Package missing required sensor data CSV (expected recording.csv or sensor_data.csv)")

            if errors:
                return PackageValidationResult(path_str, False, errors=errors, warnings=warnings)

            # Read & validate manifest
            try:
                manifest_bytes = zf.read(manifest_entry)
                manifest = json.loads(manifest_bytes.decode("utf-8"))
            except Exception as exc:
                return PackageValidationResult(path_str, False, errors=[f"Malformed manifest JSON: {exc}"])

            if not isinstance(manifest, dict):
                return PackageValidationResult(path_str, False, errors=["Manifest root must be a JSON object"])

            # 1. Package schema version
            schema_ver = manifest.get("package_schema_version")
            if schema_ver not in SUPPORTED_PACKAGE_SCHEMA_VERSIONS:
                errors.append(f"Unsupported package_schema_version: {schema_ver}. Expected one of {sorted(SUPPORTED_PACKAGE_SCHEMA_VERSIONS)}")

            # 2. Dataset Session ID
            dataset_id = manifest.get("dataset_session_id")
            if not validate_dataset_id(dataset_id):
                errors.append(f"Invalid dataset_session_id: {dataset_id!r}. Expected a valid UUID.")
            else:
                if known_dataset_ids is not None:
                    if dataset_id in known_dataset_ids:
                        errors.append(f"Duplicate dataset_session_id detected: {dataset_id}")
                    else:
                        known_dataset_ids.add(dataset_id)

            # 3. Recording origin / provenance
            provenance = manifest.get("recording_provenance")
            if provenance not in VALID_PROVENANCES:
                errors.append(f"Invalid recording_provenance: {provenance!r}. Expected one of {sorted(VALID_PROVENANCES)}")

            # 4. App version
            if not isinstance(manifest.get("app_version"), str) or not manifest.get("app_version"):
                errors.append("app_version must be a non-empty string")

            # 5. Timestamps
            start_epoch = manifest.get("recording_start_epoch_millis")
            if not isinstance(start_epoch, (int, float)) or start_epoch <= 0:
                errors.append("recording_start_epoch_millis must be a positive number")

            end_epoch = manifest.get("recording_end_epoch_millis")
            if end_epoch is not None and (not isinstance(end_epoch, (int, float)) or end_epoch < (start_epoch or 0)):
                errors.append("recording_end_epoch_millis must be >= recording_start_epoch_millis when present")

            # 6. Verify privacy allowlist - strictly no forbidden fields in manifest
            forbidden_keys = {
                "user_id", "rider_name", "name", "phone", "phone_number", "contacts",
                "emergency_contacts", "medical", "medical_profile", "location", "gps",
                "latitude", "longitude", "route", "internal_session_id", "firebase_uid",
                "android_id", "device_id", "imei"
            }
            found_forbidden = forbidden_keys.intersection(k.lower() for k in manifest.keys())
            if found_forbidden:
                errors.append(f"Manifest contains forbidden personally identifying or location fields: {sorted(found_forbidden)}")

            # Read & validate CSV
            csv_session_ids = set()
            csv_row_count = 0
            hasher = hashlib.sha256()
            file_info = {
                "source_file": f"{zip_path.name}:{csv_entry}",
                "rows": 0, "valid_rows": 0, "invalid_rows": 0,
                "extra_columns": [], "missing_columns": []
            }
            if inspection is not None:
                inspection.files.append(file_info)

            try:
                csv_bytes = zf.read(csv_entry)
                csv_text = csv_bytes.decode("utf-8-sig")
                reader = csv.DictReader(io.StringIO(csv_text))
                if reader.fieldnames is None:
                    errors.append(f"{csv_entry} is empty or has no header")
                else:
                    names = reader.fieldnames
                    missing_cols = [c for c in EXPECTED_COLUMNS if c not in names]
                    extra_cols = [c for c in names if c not in EXPECTED_COLUMNS]
                    if missing_cols:
                        errors.append(f"{csv_entry} missing required columns: {missing_cols}")
                    if extra_cols:
                        warnings.append(f"{csv_entry} contains extra columns: {extra_cols}")

                    for row_idx, r in enumerate(reader, start=2):
                        csv_row_count += 1
                        file_info["rows"] += 1
                        sid = (r.get("session_id") or "").strip()
                        if sid:
                            csv_session_ids.add(sid)

                        stype = (r.get("sensor_type") or "").strip()
                        ts_nanos = (r.get("timestamp_nanos") or "").strip()
                        ts_millis = (r.get("timestamp_epoch_millis") or "").strip()
                        x_val = (r.get("x") or "").strip()
                        y_val = (r.get("y") or "").strip()
                        z_val = (r.get("z") or "").strip()
                        acc_val = (r.get("accuracy") or "").strip()
                        canonical_line = f"{stype},{ts_nanos},{ts_millis},{x_val},{y_val},{z_val},{acc_val}\n"
                        hasher.update(canonical_line.encode("utf-8"))

                        if inspection is not None:
                            event, issues = _parse_row(r, f"{zip_path.name}:{csv_entry}", row_idx)
                            inspection.issues.extend(issues)
                            if event is None:
                                file_info["invalid_rows"] += 1
                            else:
                                file_info["valid_rows"] += 1
                                inspection.add(event)
                                if valid_sink is not None:
                                    valid_sink(event)

            except Exception as exc:
                errors.append(f"Failed to read CSV entry {csv_entry}: {exc}")

            computed_content_hash = f"sha256:{hasher.hexdigest()}"

            # Validate against manifest sensor_content_hash if present
            manifest_content_hash = manifest.get("sensor_content_hash")
            if manifest_content_hash is not None:
                if manifest_content_hash != computed_content_hash:
                    errors.append(f"Manifest sensor_content_hash {manifest_content_hash} does not match computed sensor data hash {computed_content_hash}")

            # Duplicate recording content detection across packages
            if known_content_hashes is not None:
                if computed_content_hash in known_content_hashes:
                    errors.append(f"Duplicate recording content detected: identical sensor content hash {computed_content_hash} has already been ingested (dataset_session_id: {dataset_id})")
                else:
                    known_content_hashes.add(computed_content_hash)

            # Manifest vs CSV consistency
            if dataset_id and csv_session_ids:
                if len(csv_session_ids) > 1:
                    errors.append(f"CSV contains multiple session IDs: {sorted(csv_session_ids)}")
                elif dataset_id not in csv_session_ids:
                    errors.append(f"CSV session_id ({sorted(csv_session_ids)}) does not match manifest dataset_session_id ({dataset_id})")

            # Check quality block consistency if present
            quality_block = manifest.get("recording_quality")
            if isinstance(quality_block, dict):
                declared_events = quality_block.get("event_count")
                if isinstance(declared_events, int) and declared_events != csv_row_count:
                    warnings.append(f"Manifest declared event_count {declared_events} differs from CSV rows {csv_row_count}")

            is_valid = len(errors) == 0
            return PackageValidationResult(
                package_path=path_str,
                is_valid=is_valid,
                dataset_session_id=dataset_id,
                sensor_content_hash=computed_content_hash,
                manifest=manifest,
                errors=errors,
                warnings=warnings
            )

    except Exception as exc:
        return PackageValidationResult(path_str, False, errors=[f"Unexpected error inspecting ZIP: {exc}"])
