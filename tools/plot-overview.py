#!/usr/bin/env python3
"""Render the README chart from one complete published run (requires matplotlib).

Usage: python3 tools/plot-overview.py docs/results/2026-10-14-ci-v3
"""
import csv
import sys
from pathlib import Path

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.ticker import FixedLocator, FixedFormatter

root = Path(sys.argv[1])
sources = list(root.glob('*/measurements.csv'))
if len(sources) != 1:
    raise ValueError('Overview requires one complete published run')
with sources[0].open() as stream:
    rows = list(csv.DictReader(stream))
if len(rows) != 11 or len({row['run_id'] for row in rows}) != 1:
    raise ValueError('Expected eleven cases from one run')
values = {(r['database'], r['implementation'], r['mode']):
          float(r['duration_ms']) / 1000 for r in rows}
meta = rows[0]
cases = [('scriptella', 'batch_1000', 'Scriptella · batch 1000'),
         ('jdbc', 'batch_1000', 'JDBC · batch 1000'),
         ('csvsql', 'chunk_1000', 'csvsql · chunk 1000'),
         ('copy', 'native_reference', 'COPY · native reference')]
colors = ['#197a43', '#526d82', '#94651b', '#656565']
plt.rcParams.update({'font.family': 'DejaVu Sans', 'font.size': 10,
                     'svg.fonttype': 'none'})
fig, axes = plt.subplots(2, 1, figsize=(10, 5.5))
fig.subplots_adjust(left=.28, right=.89, top=.78, bottom=.16, hspace=.65)
fig.suptitle('End-to-end CSV import times', x=.04, y=.97,
             ha='left', fontsize=19, weight='bold')
fig.text(.04, .875,
         f"One million rows · one GitHub Actions run · benchmark {meta['benchmark_version']}"
         f" · Scriptella {meta['scriptella_version']}", color='#444444', fontsize=10)
for ax, db, title in zip(axes, ['postgres', 'mariadb'], ['PostgreSQL', 'MariaDB']):
    selected = cases if db == 'postgres' else cases[:3]
    ax.set_title(title, loc='left', weight='bold', pad=10)
    ax.set_xscale('log')
    ax.set_xlim(1, 1000)
    ax.set_ylim(len(selected)-.5, -.5)
    ax.xaxis.set_major_locator(FixedLocator([1, 10, 100, 1000]))
    ax.xaxis.set_major_formatter(FixedFormatter(['1 s', '10 s', '100 s', '1,000 s']))
    ax.minorticks_off()
    ax.grid(axis='x', color='#dddddd', linewidth=.8)
    ax.set_yticks(range(len(selected)), [c[2] for c in selected])
    ax.tick_params(axis='both', length=0, pad=8)
    for spine in ax.spines.values():
        spine.set_visible(False)
    for y, (impl, mode, _) in enumerate(selected):
        value = values[(db, impl, mode)]
        ax.scatter(value, y, s=75, color=colors[y], zorder=3)
        ax.annotate(f'{value:.3f} s', (value, y), xytext=(9, 0),
                    textcoords='offset points', va='center', fontsize=10)
fig.text(.28, .065, 'Duration · logarithmic scale · lower is faster', fontsize=10)
fig.text(.04, .015, 'COPY: PostgreSQL only. Chunking and JDBC batching differ.'
         ' Single observations; see report for methodology.', fontsize=9, color='#444444')
fig.savefig(root / 'overview.svg', metadata={'Date': None})
plt.close(fig)
