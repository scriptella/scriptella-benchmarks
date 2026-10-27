"""Validate plot inputs before computing medians or importing matplotlib.

Compatibility fields mirror Aggregate.java; PlotCompatibilityTest checks parity.
Plots require complete runs of one Scriptella release (the Java report can group
multiple releases and show partial runs).
"""
import csv
import math
from pathlib import Path

BASE_COMPATIBILITY = (
    "benchmark_version", "environment_id", "architecture", "fixture_sha256", "rows",
    "java_version", "postgres_image", "mariadb_image", "postgres_jdbc_version",
    "mariadb_jdbc_version", "commons_csv_version", "python_version", "agate_version",
    "agate_sql_version", "csvkit_version", "sqlalchemy_version", "postgres_dbapi_driver",
    "postgres_dbapi_driver_version", "mariadb_dbapi_driver", "mariadb_dbapi_driver_version",
    "postgres_synchronous_commit", "postgres_fsync", "mariadb_innodb_flush_log_at_trx_commit",
    "timing_boundary", "case_definition",
)
FINAL_CASE_DEFINITION = "v5-final-eight-pg-rewrite-true-warmup20000-pandas-read10000-write1000-local-infile-v1"
LOAD_METADATA = (
    "mariadb_local_infile", "mariadb_sql_mode", "mariadb_log_bin", "mariadb_binlog_format",
    "mariadb_sync_binlog", "mariadb_load_client_settings", "mariadb_load_sql",
    "mariadb_load_transaction_probe",
)


def compatibility_fields(values):
    fields = list(BASE_COMPATIBILITY)
    if values.get("benchmark_version") == "v5":
        fields = [f for f in fields if f not in ("agate_version", "agate_sql_version", "csvkit_version")]
        fields += ["pandas_version", "numpy_version"]
        if values.get("case_definition") == FINAL_CASE_DEFINITION:
            fields += LOAD_METADATA
    return fields


def load_measurements(root):
    groups, cases_by_run = {}, {}
    metadata = None
    for source in sorted(Path(root).glob("*/measurements.csv")):
        with source.open(encoding="utf-8", newline="") as stream:
            for row in csv.DictReader(stream):
                for field in compatibility_fields(row):
                    if not (row.get(field) or "").strip():
                        raise ValueError(f"{source}: missing compatibility field {field}")
                    if metadata is not None and row[field] != metadata.get(field):
                        raise ValueError(f"{source}: incompatible {field}")
                if row["benchmark_version"] == "n/a":
                    raise ValueError(f"{source}: unversioned run")
                if metadata is not None and row["scriptella_version"] != metadata["scriptella_version"]:
                    raise ValueError("Charts require one Scriptella release")
                if row["implementation"] == "scriptella" and any(
                    row.get(f) != row["scriptella_version"]
                    for f in ("core_artifact_version", "drivers_artifact_version")
                ):
                    raise ValueError(f"{source}: mismatched Scriptella artifacts")
                key = row["database"], row["implementation"], row["mode"]
                run = row["run_id"]
                cases = cases_by_run.setdefault(run, set())
                if key in cases:
                    raise ValueError(f"{source}: duplicate run/case {run} {key}")
                duration = float(row["duration_ms"])
                throughput = float(row["rows_per_second"])
                if not all(math.isfinite(n) and n > 0 for n in (duration, throughput)):
                    raise ValueError(f"{source}: invalid measurement")
                expected = float(row["rows"]) * 1000 / duration
                if abs(expected - throughput) > max(1, expected * 0.00001):
                    raise ValueError(f"{source}: inconsistent throughput")
                cases.add(key)
                groups.setdefault(key, []).append(duration / 1000)
                if metadata is None:
                    metadata = row
    expected_cases = set(groups)
    if (not cases_by_run or len(groups) not in (7, 8, 11)
            or any(cases != expected_cases for cases in cases_by_run.values())):
        raise ValueError("Charts require complete, compatible final eight-case or historical seven/eleven-case runs")
    if metadata["case_definition"] == FINAL_CASE_DEFINITION:
        final_cases = {(db, impl, "batch_1000") for db in ("postgres", "mariadb")
                       for impl in ("scriptella", "jdbc")}
        final_cases |= {(db, "pandas", "chunk_1000") for db in ("postgres", "mariadb")}
        final_cases |= {("postgres", "copy", "native_reference"), ("mariadb", "load_data", "native_reference")}
        if expected_cases != final_cases:
            raise ValueError("Final v5 charts require exactly the eight active cases")
    return groups, set(cases_by_run), metadata
