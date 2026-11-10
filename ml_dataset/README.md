# VahakVigil offline sensor dataset toolkit

This Python standard-library toolkit inspects CSV files exported by the Android app. It is deliberately separate from the Android runtime. It validates data and supports human-entered ground truth; it does not classify crashes, train models, resample, interpolate, or normalize readings.

## Requirements

- Python 3.10 or newer
- No third-party packages
- Local filesystem access to exported CSVs (nothing is uploaded)

Run commands from the repository root with `python -m ml_dataset ...`.

## Commands

Inspect one or several CSV files or directories (directories are searched recursively):

```powershell
python -m ml_dataset inspect --inputs C:\data\ride-a.csv C:\data\exports --output C:\data\inspection
```

Validate exported privacy-safe dataset packages (ZIP files containing `recording.csv` and `dataset_manifest.json`):

```powershell
python -m ml_dataset validate-package --inputs C:\data\vahakvigil-dataset-pkg1.zip C:\data\packages --output C:\data\validation-reports
```

The optional `--gap-multiplier` defaults to `3.0`. A gap is a positive adjacent interval exceeding that multiplier times the median positive interval in the same session/sensor stream. This is a configurable descriptive heuristic, not a universal sensor threshold. `--suspicious-abs-limit N` optionally flags XYZ values whose magnitude exceeds a user-supplied bound for review; choose a bound appropriate for the sensor units being reviewed. It never rejects or edits those readings.

Create an annotation manifest with observed session IDs, all initially unreviewed:

```powershell
python -m ml_dataset init-manifest --inputs C:\data\exports --manifest C:\data\annotations.json
```

Manifest version 2 keeps all existing contextual labels and adds a separate `binary_outcome` field. Version 1 manifests remain readable. A missing outcome stays unreviewed; contextual labels (including `SIMULATED_IMPACT`) never imply a binary crash/no-crash outcome.

Record a review as a new manifest file (the input is never overwritten):

```powershell
python -m ml_dataset review-outcome --inputs C:\data\exports --manifest C:\data\annotations.json --output C:\data\annotations-reviewed.json --session-id SESSION_ID --outcome CRASH_CONFIRMED --reviewer-id reviewer-pseudonym --evidence-provenance "reviewed incident report" --recording-origin REAL
```

Allowed outcomes are `CRASH_CONFIRMED`, `NO_CRASH_CONFIRMED`, and `INDETERMINATE`. A confirmed crash requires recording origin `REAL`. To revise a review, supply `--supersede --revision-reason "..."`; the earlier review is retained in linked audit history. The command refuses existing output paths and invalid/conflicting manifests.

Replay each validated CSV through the Android evaluation harness and save its `toDatasetEvaluationJson()` output as one JSON file per session. Then join those reports with reviewed annotations:

```powershell
python -m ml_dataset evaluate --inputs C:\data\exports --manifest C:\data\annotations-reviewed.json --reports C:\data\replays\session-a.json C:\data\replays\session-b.json --output C:\data\evaluation-report.json
```

Only valid recordings with matching Kotlin reports, `OBSERVED` provenance, an explicit reviewed outcome, and `REAL` recording origin enter binary counts. Indeterminate, simulated, mixed, unknown-origin, conflicting, unreviewed, missing, and synthetic sessions are listed as excluded. Reports include precision/recall only when denominators are non-zero, measurable detection latency, and deterministic session-level splits. These splits and counts do not establish generalization.

Edit the JSON by hand. Then prepare separate outputs:

```powershell
python -m ml_dataset prepare --inputs C:\data\exports --manifest C:\data\annotations.json --output C:\data\prepared
```

The `--output` directory must be empty or absent and must not contain any raw input or manifest. The tool refuses to overwrite output files. It never changes source CSVs.

## CSV contract and validation

Required ordered fields emitted by the app are `session_id,sensor_type,timestamp_nanos,timestamp_epoch_millis,x,y,z,accuracy`. Extra columns are reported but ignored. The loader accepts known Android export values `ACCELEROMETER` and `GYROSCOPE` (case-insensitive for validation) and preserves the original field in prepared event records. Missing accuracy is represented as `null` in output and reported. Invalid rows are rejected with source filename, row number, reason, and available original field values in `rejected_rows.jsonl`.

Files are read incrementally. Valid rows are written as JSON Lines with original XYZ and timestamps, source filename, and source row. No GPS, contact, or medical fields are added. Statistics are isolated by session and sensor. Duplicate timestamps across different sensor types are not duplicates. Sampling frequency is `1 / mean(positive adjacent hardware timestamp interval)`; interval and rate summaries use actual observed nanosecond values. A report also records the range of `(epoch_ms - floor(hardware_ns / 1,000,000))` offsets for investigating wall-clock/monotonic clock consistency. A negative epoch progression while hardware timestamps progress is a warning. Suspicious values are retained.

Outputs from `prepare`:

- `quality_report.json` and `quality_report.md`: file validation, issues, stream statistics.
- `validated_events.jsonl`: valid rows only, retaining source provenance and original values.
- `rejected_rows.jsonl`: invalid data rows and reasons; file-level schema errors stay in the quality report.
- `annotation_validation.json`: accepted annotations, rejected annotation errors, and unreviewed session IDs.
- `session_metadata.json`: per-session sensor summaries plus valid annotation metadata.
- `session_splits.json`: deterministic session-only split assignment, or an explicit refusal when there are too few reviewed labeled sessions.
- `preparation_manifest.json`: inputs and processing limits (no training, resampling, or normalization).

Preparation may emit valid sensor events even if annotations are absent or invalid, but those events are explicitly unreviewed/unlabeled and are not admitted to split assignments. An absent label is never treated as `NORMAL_RIDE`. Split generation is structural only; even one session per split is far too little to support a generalization claim. Current assignment uses stable sorted session IDs round-robin among training, validation, and test. It is not stratified and should not be treated as an evaluation plan for an imbalanced or small dataset.

## Annotation schema (versions 1 and 2)

The JSON root contains `schema_version: 1` (context-only legacy) or `schema_version: 2`, and a `sessions` array. Each session has an existing `session_id`, `review_status` (`UNREVIEWED`, `REVIEWED`, or `NEEDS_REVIEW`) and a `labels` array. Version 2 additionally accepts nullable `binary_outcome`, separate from `labels`. A label has:

- `label`: `NORMAL_RIDE`, `BRAKING`, `POTHOLE_OR_ROUGH_ROAD`, `SUDDEN_MANEUVER`, `PHONE_DROP`, `MOUNTED_PHONE_MOVEMENT`, `SIMULATED_IMPACT`, or `UNKNOWN`.
- `level`: `SESSION` or `EVENT`. Event labels require nonnegative `event_start_timestamp_nanos` and `event_end_timestamp_nanos`, in hardware monotonic nanoseconds; start must not exceed end. Session labels omit event times.
- `event_origin`: `REAL`, `SIMULATED`, or `UNKNOWN`. `SIMULATED_IMPACT` should identify controlled simulation; no dangerous riding experiment should be performed.
- `annotation_source`: required human provenance text (for example, rider report or reviewed video, if lawfully available).
- `confidence`: optional value from 0 to 1; it is the annotator's confidence, not a model score.
- `review_status`: optional review state, defaulting to its session's status.
- `notes`: optional human notes.

Example (illustrative schema only, not a real annotation):

```json
{
  "schema_version": 1,
  "sessions": [{
    "session_id": "copy-an-observed-session-id-here",
    "review_status": "REVIEWED",
    "labels": [{
      "label": "BRAKING",
      "level": "EVENT",
      "event_start_timestamp_nanos": 1000000000,
      "event_end_timestamp_nanos": 2000000000,
      "event_origin": "REAL",
      "annotation_source": "manual review",
      "confidence": 0.8,
      "review_status": "REVIEWED",
      "notes": "Replace with evidence-based notes"
    }]
  }]
}
```

This sample is documentation only. Do not use its placeholder ID or times as data. Do not label an event as an accident based on sensor thresholds alone. Use `UNKNOWN` where evidence is insufficient.

A non-null `binary_outcome` is an independently reviewed object containing `session_id`, `review_id`, `outcome`, `reviewer_id`, `reviewed_at_utc` (timezone-aware ISO-8601), `evidence_provenance`, `recording_origin` (`REAL`, `SIMULATED`, `MIXED`, `UNKNOWN`), optional `notes`, and `audit_history`. Historical reviews are immutable snapshots linked by `superseded_by_review_id`, `superseded_at_utc`, and a required `revision_reason`. Conflicts are rejected, not silently resolved. Session IDs must match observed CSV sessions.

## Tests

All fixtures under `ml_dataset/tests/` are explicitly synthetic unit-test data and are not training examples. Run:

```powershell
python -m unittest discover -s ml_dataset/tests -v
```

## Current repository data availability

The toolkit authoring workspace did not contain exported sensor CSV files when implemented. No real-recording statistics are included. Collecting and manually reviewing safe, consented ride recordings is a prerequisite to meaningful dataset assessment and later labeling/preparation.
