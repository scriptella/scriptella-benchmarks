#!/usr/bin/env python3
"""Validate and summarize only the separately identified rewrite follow-up."""
import csv
import statistics
import sys
from pathlib import Path

root = Path(sys.argv[1])
rows = list(csv.DictReader((root / 'measurements.csv').open()))
metadata = (root / 'preparation.properties').read_text()
assert 'benchmark_version=v3-pg-rewrite-followup-1' in metadata
assert 'status=success' in metadata
assert len(rows) == 18, f'Expected 18 verified measurements, got {len(rows)}'
variants = [('postgres', 'scriptella', 'false'), ('postgres', 'scriptella', 'true'),
            ('postgres', 'jdbc', 'false'), ('postgres', 'jdbc', 'true'),
            ('mariadb', 'scriptella', 'default'), ('mariadb', 'jdbc', 'default')]
for round_id in range(1, 4):
    actual = [r for r in rows if int(r['round']) == round_id]
    expected = variants if round_id % 2 else variants[::-1]
    assert [(r['database'], r['implementation'], r['rewrite']) for r in actual] == expected
    assert [int(r['position']) for r in actual] == list(range(1, 7))
for r in rows:
    assert r['verified'] == 'true' and r['rows'] == '1000000' and r['batch_size'] == '1000'
    assert float(r['duration_ms']) > 0
    assert abs(float(r['rows_per_second']) - 1e9 / float(r['duration_ms'])) < 0.1
print('| Database | Implementation | Rewrite | Round 1 (s) | Round 2 (s) | Round 3 (s) | Median (s) |')
print('|---|---|---|---:|---:|---:|---:|')
medians = {}
for variant in variants:
    times = [float(r['duration_ms']) / 1000 for r in rows
             if (r['database'], r['implementation'], r['rewrite']) == variant]
    assert len(times) == 3
    medians[variant] = statistics.median(times)
    print('| ' + ' | '.join(variant) + ' | ' + ' | '.join(f'{t:.3f}' for t in times)
          + f' | {statistics.median(times):.3f} |')
print()
for impl in ('scriptella', 'jdbc'):
    baseline = medians['postgres', impl, 'false']
    rewritten = medians['postgres', impl, 'true']
    maria = medians['mariadb', impl, 'default']
    print(f'{impl}: rewriting speedup {baseline/rewritten:.3f}x; elapsed reduction '
          f'{100*(1-rewritten/baseline):.1f}%; PG/MariaDB duration ratio '
          f'{baseline/maria:.3f}x -> {rewritten/maria:.3f}x; '
          f'PG minus MariaDB {baseline-maria:.3f}s -> {rewritten-maria:.3f}s.')
