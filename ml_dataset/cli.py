"""Command-line entry points for local VahakVigil dataset preparation."""

from __future__ import annotations

import argparse
import json
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable

from .annotations import BINARY_OUTCOMES, empty_manifest, load_manifest, validate_manifest
from .core import EXPECTED_COLUMNS, Inspection, Issue, inspect_files, iter_csv, write_markdown
from .packages import validate_package
from .splits import assign_session_splits


def collect_csv_paths(inputs: Iterable[str]) -> list[Path]:
    paths: list[Path] = []
    for item in inputs:
        candidate = Path(item)
        if candidate.is_file():
            paths.append(candidate)
        elif candidate.is_dir():
            paths.extend(sorted(candidate.rglob("*.csv")))
        else:
            raise FileNotFoundError(f"Input does not exist: {candidate}")
    # Dedupe without changing stable order.
    unique = list(dict.fromkeys(path.resolve() for path in paths))
    if not unique:
        raise ValueError("No CSV files found in the supplied input paths.")
    return unique


def collect_package_paths(inputs: Iterable[str]) -> list[Path]:
    paths: list[Path] = []
    for item in inputs:
        candidate = Path(item)
        if candidate.is_file():
            if candidate.suffix.lower() == ".zip":
                paths.append(candidate)
        elif candidate.is_dir():
            paths.extend(sorted(candidate.rglob("*.zip")))
        else:
            raise FileNotFoundError(f"Input does not exist: {candidate}")
    unique = list(dict.fromkeys(path.resolve() for path in paths))
    if not unique:
        raise ValueError("No ZIP package files found in the supplied input paths.")
    return unique


def _write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + "\n", encoding="utf-8", newline="\n")


def _report_paths(paths: list[Path], gap_multiplier: float, suspicious_abs_limit: float | None):
    inspection = inspect_files(paths, gap_multiplier=gap_multiplier, suspicious_abs_limit=suspicious_abs_limit)
    return inspection, inspection.to_json(gap_multiplier)


def command_inspect(args: argparse.Namespace) -> int:
    paths = collect_csv_paths(args.inputs)
    inspection, report = _report_paths(paths, args.gap_multiplier, args.suspicious_abs_limit)
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    _write_json(output / "quality_report.json", report)
    write_markdown(report, output / "quality_report.md")
    print(f"Inspected {len(paths)} file(s): {report['valid_event_count']} valid events, {report['session_count']} sessions, {len(report['issues'])} issues.")
    print(f"Reports: {output / 'quality_report.json'} and {output / 'quality_report.md'}")
    return 1 if any(issue["severity"] == "error" for issue in report["issues"]) else 0


def command_init_manifest(args: argparse.Namespace) -> int:
    paths = collect_csv_paths(args.inputs)
    inspection, _ = _report_paths(paths, args.gap_multiplier, None)
    manifest_path = Path(args.manifest)
    if manifest_path.exists():
        raise FileExistsError(f"Refusing to overwrite existing manifest: {manifest_path}")
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    _write_json(manifest_path, empty_manifest(inspection.sessions))
    print(f"Created unreviewed manifest for {len(inspection.sessions)} observed session(s): {manifest_path}")
    return 0


def _load_document(path: Path) -> dict:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError("Manifest root must be an object")
    return value


def command_review_outcome(args: argparse.Namespace) -> int:
    inputs = collect_csv_paths(args.inputs)
    inspection = inspect_files(inputs)
    manifest_path, output_path = Path(args.manifest), Path(args.output)
    if output_path.exists():
        raise FileExistsError(f"Refusing to overwrite existing manifest: {output_path}")
    _ensure_safe_output(output_path, inputs, manifest_path)
    if output_path.resolve() == manifest_path.resolve():
        raise ValueError("Output manifest must be a new path")
    if args.outcome not in BINARY_OUTCOMES:
        raise ValueError(f"Outcome must be one of {sorted(BINARY_OUTCOMES)}")
    document = _load_document(manifest_path)
    validation = validate_manifest(document, inspection.sessions)
    if validation.errors:
        raise ValueError("Existing manifest has validation errors; resolve them before recording a review")
    sessions = document.get("sessions", [])
    existing = next((item for item in sessions if item.get("session_id") == args.session_id), None)
    if existing is None or args.session_id not in inspection.sessions:
        raise ValueError("Session ID must exist in both the manifest and validated recordings")
    if args.outcome == "CRASH_CONFIRMED" and args.recording_origin != "REAL":
        raise ValueError("CRASH_CONFIRMED requires --recording-origin REAL")
    onset = getattr(args, "crash_onset_timestamp_nanos", None)
    if onset is not None and (onset < 0 or args.outcome != "CRASH_CONFIRMED"):
        raise ValueError("Crash onset must be non-negative and only applies to CRASH_CONFIRMED")
    old = existing.get("binary_outcome")
    if old is not None and not args.supersede:
        raise ValueError("A binary review already exists; use --supersede with --revision-reason to revise it")
    if args.supersede and (old is None or not args.revision_reason or not args.revision_reason.strip()):
        raise ValueError("--supersede requires an existing review and a non-empty --revision-reason")
    if int(document.get("schema_version", 1)) == 1:
        document["schema_version"] = 2
        for item in sessions:
            item.setdefault("binary_outcome", None)
    review_id = str(uuid.uuid4())
    reviewed_at = args.reviewed_at or datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    history = list(old.get("audit_history", [])) if old else []
    if old:
        prior = {key: value for key, value in old.items() if key != "audit_history"}
        prior.update({"superseded_at_utc": reviewed_at, "superseded_by_review_id": review_id, "revision_reason": args.revision_reason.strip()})
        history.append(prior)
    existing["binary_outcome"] = {
        "session_id": args.session_id, "review_id": review_id, "outcome": args.outcome,
        "reviewer_id": args.reviewer_id, "reviewed_at_utc": reviewed_at,
        "evidence_provenance": args.evidence_provenance, "recording_origin": args.recording_origin,
        "notes": args.notes, "audit_history": history,
        "crash_onset_timestamp_nanos": onset,
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("x", encoding="utf-8", newline="\n") as handle:
        json.dump(document, handle, ensure_ascii=False, indent=2, allow_nan=False)
        handle.write("\n")
    print(f"Wrote reviewed outcome {args.outcome} for {args.session_id} to {output_path}")
    return 0


def command_evaluate(args: argparse.Namespace) -> int:
    from .ground_truth import evaluate_dataset
    inputs = collect_csv_paths(args.inputs)
    output = Path(args.output)
    manifest = Path(args.manifest)
    report_paths = [Path(p) for p in args.reports]
    _ensure_safe_output(output, inputs + report_paths, manifest)
    if output.exists():
        raise FileExistsError(f"Refusing to overwrite evaluation report: {output}")
    inspection = inspect_files(inputs)
    annotations = load_manifest(manifest, inspection.sessions)
    pipeline_reports = {}
    for path in report_paths:
        payload = json.loads(path.read_text(encoding="utf-8"))
        sid = payload.get("session_id")
        if not isinstance(sid, str) or sid in pipeline_reports:
            raise ValueError(f"Missing or duplicate session_id in Kotlin report: {path}")
        pipeline_reports[sid] = payload
    result = evaluate_dataset(inspection, annotations, pipeline_reports)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("x", encoding="utf-8", newline="\n") as handle:
        json.dump(result, handle, ensure_ascii=False, indent=2, allow_nan=False)
        handle.write("\n")
    print(f"Evaluation report written to {output}: {len(result['eligible_sessions'])} eligible session(s)")
    return 0


def command_validate_package(args: argparse.Namespace) -> int:
    paths = collect_package_paths(args.inputs)
    results = []
    known_ids: set[str] = set()
    known_content_hashes: set[str] = set()
    has_errors = False
    for path in paths:
        res = validate_package(path, known_ids, known_content_hashes=known_content_hashes)
        results.append({
            "package_path": str(path.resolve()),
            "is_valid": res.is_valid,
            "dataset_session_id": res.dataset_session_id,
            "sensor_content_hash": res.sensor_content_hash,
            "provenance": res.manifest.get("recording_provenance") if res.manifest else None,
            "app_version": res.manifest.get("app_version") if res.manifest else None,
            "errors": res.errors,
            "warnings": res.warnings,
        })
        if not res.is_valid:
            has_errors = True
            print(f"FAIL: {path.name}: {res.errors}", file=sys.stderr)
        else:
            print(f"OK:   {path.name} (id: {res.dataset_session_id}, origin: {res.manifest.get('recording_provenance')})")

    report = {
        "tool": "vahakvigil-ml-dataset",
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "total_packages": len(paths),
        "valid_packages": sum(1 for r in results if r["is_valid"]),
        "results": results
    }
    if args.output:
        out_dir = Path(args.output)
        out_dir.mkdir(parents=True, exist_ok=True)
        _write_json(out_dir / "package_validation_report.json", report)
        print(f"Validation report saved to: {out_dir / 'package_validation_report.json'}")

    return 1 if has_errors else 0


def _ensure_safe_output(output: Path, inputs: list[Path], manifest: Path | None = None) -> None:
    output_resolved = output.resolve()
    protected = inputs + ([manifest.resolve()] if manifest and manifest.exists() else [])
    for source in protected:
        try:
            source.resolve().relative_to(output_resolved)
        except ValueError:
            pass
        else:
            raise ValueError(f"Output directory would contain/overwrite an input source: {source}")
        if source.suffix.lower() == ".csv":
            try:
                output_resolved.relative_to(source.resolve().parent)
            except ValueError:
                pass
            else:
                raise ValueError(f"Output directory must be separate from the raw input directory: {source.resolve().parent}")
    if output.exists() and any(output.iterdir()):
        raise FileExistsError(f"Output directory is not empty; refusing to overwrite existing files: {output}")


def command_prepare(args: argparse.Namespace) -> int:
    inputs = collect_csv_paths(args.inputs)
    output = Path(args.output)
    manifest_path = Path(args.manifest) if args.manifest else None
    _ensure_safe_output(output, inputs, manifest_path)
    inspection = Inspection()
    output.mkdir(parents=True, exist_ok=True)
    events_path = output / "validated_events.jsonl"
    try:
        with events_path.open("x", encoding="utf-8", newline="\n") as event_file:
            def write_event(event):
                event_file.write(json.dumps(event.to_dict(), ensure_ascii=False, allow_nan=False) + "\n")
            for csv_path in inputs:
                for _ in iter_csv(csv_path, inspection, valid_sink=write_event):
                    pass
        for info in inspection.files:
            if not info.get("empty") and info["rows"] == 0:
                inspection.issues.append(Issue("warning", "no_data_rows", "CSV contains a header but no data rows.", info["source_file"]))
        report = inspection.to_json(args.gap_multiplier)
        annotations = load_manifest(manifest_path, inspection.sessions) if manifest_path else validate_manifest(empty_manifest(inspection.sessions), inspection.sessions)
        split_eligible: set[str] = set()
        for session in annotations.valid:
            if session["review_status"] != "REVIEWED":
                continue
            if any(label["label"] != "UNKNOWN" and label["review_status"] == "REVIEWED" for label in session["labels"]):
                split_eligible.add(session["session_id"])
        split_result = assign_session_splits(split_eligible, minimum_per_split=args.minimum_sessions_per_split)
        session_metadata = []
        labels_by_session = {entry["session_id"]: entry for entry in annotations.valid}
        for session_id in sorted(inspection.sessions):
            annotation = labels_by_session.get(session_id, {"session_id": session_id, "review_status": "UNREVIEWED", "labels": []})
            streams = [s.summary(args.gap_multiplier) for (sid, _), s in sorted(inspection.streams.items()) if sid == session_id]
            session_metadata.append({"session_id": session_id, "annotation": annotation, "sensor_streams": streams})
        _write_json(output / "quality_report.json", report)
        write_markdown(report, output / "quality_report.md")
        _write_json(output / "annotation_validation.json", {"valid_sessions": annotations.valid, "errors": annotations.errors, "unreviewed_sessions": annotations.unreviewed_sessions})
        _write_json(output / "session_metadata.json", session_metadata)
        _write_json(output / "session_splits.json", split_result)
        rejected_by_row: dict[tuple[str, int], list[Issue]] = {}
        for issue in inspection.issues:
            if issue.row_number is not None and issue.severity == "error":
                rejected_by_row.setdefault((issue.source_file, issue.row_number), []).append(issue)
        with (output / "rejected_rows.jsonl").open("x", encoding="utf-8", newline="\n") as rejected:
            for (source_file, row_number), issues in sorted(rejected_by_row.items()):
                rejected.write(json.dumps({"source_file": source_file, "row_number": row_number, "reasons": [{"code": issue.code, "message": issue.message} for issue in issues], "session_id": next((issue.session_id for issue in issues if issue.session_id), None), "sensor_type": next((issue.sensor_type for issue in issues if issue.sensor_type), None), "record": next((issue.record for issue in issues if issue.record), None)}, ensure_ascii=False, allow_nan=False) + "\n")
        _write_json(output / "preparation_manifest.json", {
            "tool": "vahakvigil-ml-dataset", "version": "0.1.0", "raw_inputs": [str(p.resolve()) for p in inputs],
            "expected_columns": list(EXPECTED_COLUMNS), "raw_files_modified": False,
            "valid_event_count": inspection.valid_rows, "session_count": len(inspection.sessions),
            "annotation_error_count": len(annotations.errors), "split_created": split_result["created"],
            "model_training_performed": False, "resampling_performed": False, "normalization_performed": False,
        })
    except Exception:
        # A partial output is not treated as successful; retain it for diagnosis.
        raise
    print(f"Prepared {inspection.valid_rows} validated event(s) from {len(inspection.sessions)} session(s) at {output}")
    if annotations.errors:
        print(f"Annotation validation errors: {len(annotations.errors)} (see annotation_validation.json)")
    if not split_result["created"]:
        print("Session splits not created: " + split_result["reason"])
    return 1 if any(issue.severity == "error" for issue in inspection.issues) or annotations.errors else 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="vahakvigil-dataset", description="Offline tools for inspecting VahakVigil sensor CSV exports.")
    subparsers = parser.add_subparsers(required=True)
    inspect = subparsers.add_parser("inspect", help="Validate CSV files and write JSON/Markdown quality reports.")
    inspect.add_argument("--inputs", nargs="+", required=True, help="CSV files or directories (recursively searched).")
    inspect.add_argument("--output", required=True, help="Report directory.")
    inspect.add_argument("--gap-multiplier", type=float, default=3.0, help="Flag intervals over this multiple of each stream's median positive interval (default: 3).")
    inspect.add_argument("--suspicious-abs-limit", type=float, help="Optional user-chosen absolute XYZ review bound; never filters rows.")
    inspect.set_defaults(func=command_inspect)
    init = subparsers.add_parser("init-manifest", help="Create a human-editable manifest with observed sessions marked UNREVIEWED.")
    init.add_argument("--inputs", nargs="+", required=True)
    init.add_argument("--manifest", required=True)
    init.add_argument("--gap-multiplier", type=float, default=3.0)
    init.set_defaults(func=command_init_manifest)
    prepare = subparsers.add_parser("prepare", help="Validate and prepare a separate local dataset output directory.")
    prepare.add_argument("--inputs", nargs="+", required=True)
    prepare.add_argument("--manifest", help="Optional JSON annotation manifest.")
    prepare.add_argument("--output", required=True)
    prepare.add_argument("--gap-multiplier", type=float, default=3.0)
    prepare.add_argument("--minimum-sessions-per-split", type=int, default=1)
    prepare.set_defaults(func=command_prepare)
    review = subparsers.add_parser("review-outcome", help="Append a reviewed binary outcome into a new manifest version.")
    review.add_argument("--inputs", nargs="+", required=True); review.add_argument("--manifest", required=True); review.add_argument("--output", required=True)
    review.add_argument("--session-id", required=True); review.add_argument("--outcome", required=True, choices=sorted(BINARY_OUTCOMES))
    review.add_argument("--reviewer-id", required=True); review.add_argument("--reviewed-at", help="ISO-8601 timestamp with timezone; defaults to current UTC.")
    review.add_argument("--evidence-provenance", required=True); review.add_argument("--recording-origin", required=True, choices=["REAL", "SIMULATED", "MIXED", "UNKNOWN"])
    review.add_argument("--notes"); review.add_argument("--supersede", action="store_true"); review.add_argument("--revision-reason")
    review.add_argument("--crash-onset-timestamp-nanos", type=int, help="Optional reviewed monotonic onset timestamp for latency measurement.")
    review.set_defaults(func=command_review_outcome)
    evaluate = subparsers.add_parser("evaluate", help="Join reviewed annotations with per-session Kotlin evaluation JSON reports.")
    evaluate.add_argument("--inputs", nargs="+", required=True); evaluate.add_argument("--manifest", required=True)
    evaluate.add_argument("--reports", nargs="+", required=True, help="Kotlin toDatasetEvaluationJson report files."); evaluate.add_argument("--output", required=True)
    evaluate.set_defaults(func=command_evaluate)

    validate_pkg = subparsers.add_parser("validate-package", help="Validate privacy-safe ZIP package(s) and manifest consistency.")
    validate_pkg.add_argument("--inputs", nargs="+", required=True, help="ZIP file(s) or directory containing packages.")
    validate_pkg.add_argument("--output", help="Optional report directory to save validation report JSON.")
    validate_pkg.set_defaults(func=command_validate_package)
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        return args.func(args)
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
