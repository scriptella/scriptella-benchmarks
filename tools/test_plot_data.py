"""Reporting regressions; standard library only, no matplotlib or database needed."""
import csv
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from plot_data import FINAL_CASE_DEFINITION, compatibility_fields, load_measurements


class PlotDataTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def write_run(self, name, **overrides):
        metadata = {"benchmark_version": "v5", "case_definition": FINAL_CASE_DEFINITION}
        metadata.update({f: "same" for f in compatibility_fields(metadata) if f not in metadata})
        metadata.update(rows="1000000", scriptella_version="1.6", core_artifact_version="1.6",
                        drivers_artifact_version="1.6", run_id=name, duration_ms="1000",
                        rows_per_second="1000000")
        metadata.update(overrides)
        rows = []
        for db in ("postgres", "mariadb"):
            for impl, mode in (("scriptella", "batch_1000"), ("jdbc", "batch_1000"),
                               ("pandas", "chunk_1000"),
                               ("copy" if db == "postgres" else "load_data", "native_reference")):
                rows.append(dict(metadata, database=db, implementation=impl, mode=mode))
        path = self.root / name / "measurements.csv"
        path.parent.mkdir(exist_ok=True)
        with path.open("w", encoding="utf-8", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=rows[0].keys())
            writer.writeheader()
            writer.writerows(rows)
        return path

    def test_two_compatible_eight_case_runs(self):
        self.write_run("a", benchmark_revision="original")
        self.write_run("b", benchmark_revision="dirty", duration_ms="2000", rows_per_second="500000")
        groups, runs, _ = load_measurements(self.root)
        self.assertEqual({"a", "b"}, runs)
        self.assertEqual(8, len(groups))
        self.assertTrue(all(values == [1.0, 2.0] for values in groups.values()))

    def test_two_eight_case_runs_with_different_case_definitions_fail_before_plotting(self):
        self.write_run("a")
        self.write_run("b", case_definition="v5-different-eight-case-method")
        with self.assertRaisesRegex(ValueError, "incompatible case_definition"):
            load_measurements(self.root)
        process = subprocess.run([sys.executable, str(Path(__file__).with_name("plot-results.py")), str(self.root)],
                                 capture_output=True, text=True)
        self.assertNotEqual(0, process.returncode)
        self.assertIn("incompatible case_definition", process.stderr)
        self.assertEqual([], list(self.root.glob("*.svg")))
        self.assertEqual([], list(self.root.glob("*.png")))

    def test_every_compatibility_field_is_checked(self):
        self.write_run("a")
        for field in compatibility_fields({"benchmark_version": "v5", "case_definition": FINAL_CASE_DEFINITION}):
            with self.subTest(field=field):
                self.write_run("b", **{field: "different"})
                with self.assertRaises(ValueError):
                    load_measurements(self.root)

    def test_missing_dependency_metadata_fails(self):
        self.write_run("a")
        self.write_run("b", numpy_version="")
        with self.assertRaisesRegex(ValueError, "missing compatibility field numpy_version"):
            load_measurements(self.root)

    def test_duplicate_run_case_is_rejected(self):
        self.write_run("a")
        self.write_run("b", run_id="a")
        with self.assertRaisesRegex(ValueError, "duplicate run/case"):
            load_measurements(self.root)

    def test_invalid_timings_fail(self):
        self.write_run("a")
        for value in ("nan", "inf", "0", "-1"):
            with self.subTest(value=value):
                self.write_run("b", duration_ms=value)
                with self.assertRaisesRegex(ValueError, "invalid measurement"):
                    load_measurements(self.root)


if __name__ == "__main__":
    unittest.main()
