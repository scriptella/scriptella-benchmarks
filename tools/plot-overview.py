#!/usr/bin/env python3
"""Render an overview from a complete published run or the rewrite follow-up (requires matplotlib).

Usage: python3 tools/plot-overview.py docs/results/2026-10-21-ci-v4
"""
import csv
import sys
import statistics
from xml.etree import ElementTree as ET
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
if rows and 'rewrite' in rows[0]:
    groups = {}
    for row in rows:
        if row['verified'] != 'true' or row['rows'] != '1000000' or row['batch_size'] != '1000':
            raise ValueError('Expected verified million-row batch-1000 imports')
        key = (row['database'], row['implementation'], row['rewrite'])
        groups.setdefault(key, []).append(float(row['duration_ms']) / 1000)
    series = [('postgres', 'false', 'PostgreSQL · rewriting off', '#68786c'),
              ('postgres', 'true', 'PostgreSQL · rewriting on', '#197a43'),
              ('mariadb', 'default', 'MariaDB · unchanged', '#526d82')]
    expected = {(db, impl, rewrite) for db, rewrite, _, _ in series
                for impl in ('scriptella', 'jdbc')}
    if set(groups) != expected or any(len(values) != 3 for values in groups.values()):
        raise ValueError('Expected three repetitions of each of six configurations')
    medians = {key: statistics.median(values) for key, values in groups.items()}
    plt.rcParams.update({'font.family': 'DejaVu Sans', 'font.size': 11,
                         'svg.fonttype': 'none'})
    fig, axes = plt.subplots(2, 1, figsize=(10, 6.1))
    fig.subplots_adjust(left=.29, right=.91, top=.78, bottom=.18, hspace=.72)
    fig.suptitle('PostgreSQL batch rewriting narrows the gap', x=.04, y=.96,
                 ha='left', fontsize=19, weight='bold', color='#202320')
    fig.text(.04, .89, 'One million rows · batch size 1000 · median of three local runs',
             fontsize=11, color='#4d534e')
    for ax, impl, label in zip(axes, ['scriptella', 'jdbc'], ['Scriptella 1.6', 'Handwritten JDBC']):
        ax.set_title(label, loc='left', weight='bold', pad=12)
        ax.set_xlim(0, 18)
        ax.set_ylim(2.65, -.65)
        ax.set_xticks([0, 5, 10, 15])
        ax.set_yticks(range(3), [entry[2] for entry in series])
        ax.grid(axis='x', color='#dddddd', linewidth=.8, zorder=0)
        ax.tick_params(axis='both', length=0, pad=9, labelsize=10)
        for spine in ax.spines.values():
            spine.set_visible(False)
        for y, (db, rewrite, _, color) in enumerate(series):
            duration = medians[db, impl, rewrite]
            ax.barh(y, duration, height=.57, color=color, zorder=3)
            ax.text(duration + .2, y, f'{duration:.3f} s', va='center', fontsize=11, weight='bold')
    fig.text(.29, .09, 'Duration in seconds · lower is faster', fontsize=11)
    fig.text(.04, .035, 'Intel i9-9980HK · Colima, 4 vCPU / 6 GiB · PostgreSQL 17.11 / MariaDB 11.8.8',
             fontsize=9, color='#4d534e')
    output = root / 'rewrite-comparison.svg'
    fig.savefig(output, metadata={'Date': None})
    fig.savefig(root / 'rewrite-comparison-preview.png', dpi=180)
    plt.close(fig)
    ET.register_namespace('', 'http://www.w3.org/2000/svg')
    ns = '{http://www.w3.org/2000/svg}'
    tree = ET.parse(output)
    svg = tree.getroot()
    svg.set('role', 'img')
    svg.set('aria-labelledby', 'chart-title chart-desc')
    title = ET.Element(ns + 'title', {'id': 'chart-title'})
    title.text = 'PostgreSQL batch rewriting: Scriptella and handwritten JDBC'
    desc = ET.Element(ns + 'desc', {'id': 'chart-desc'})
    desc.text = 'Median seconds for one million rows, three runs per configuration. '
    for impl, label in [('scriptella', 'Scriptella'), ('jdbc', 'JDBC')]:
        desc.text += (f"{label}: PostgreSQL off {medians['postgres', impl, 'false']:.3f}, "
                      f"on {medians['postgres', impl, 'true']:.3f}, "
                      f"MariaDB {medians['mariadb', impl, 'default']:.3f}. ")
    desc.text += 'Linear axis starts at zero; lower is faster.'
    svg.insert(0, desc)
    svg.insert(0, title)
    tree.write(output, encoding='utf-8', xml_declaration=True)
    print(output)
    sys.exit(0)

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
output = root / 'overview.svg'
fig.savefig(output, metadata={'Date': None})
fig.savefig(root / 'overview-preview.png', dpi=180)
plt.close(fig)
ET.register_namespace('', 'http://www.w3.org/2000/svg')
ns = '{http://www.w3.org/2000/svg}'
tree = ET.parse(output)
svg = tree.getroot()
svg.set('role', 'img')
svg.set('aria-labelledby', 'chart-title chart-desc')
title = ET.Element(ns + 'title', {'id': 'chart-title'})
title.text = f"End-to-end CSV import times — benchmark {meta['benchmark_version']}"
desc = ET.Element(ns + 'desc', {'id': 'chart-desc'})
desc.text = 'One million rows, one GitHub Actions run. '
for db, label in [('postgres', 'PostgreSQL'), ('mariadb', 'MariaDB')]:
    selected = cases if db == 'postgres' else cases[:3]
    desc.text += label + ': ' + ', '.join(
        f'{name} {values[db, impl, mode]:.3f} seconds'
        for impl, mode, name in selected) + '. '
desc.text += 'Logarithmic duration scale; lower is faster. Chunking and JDBC batching differ.'
svg.insert(0, desc)
svg.insert(0, title)
tree.write(output, encoding='utf-8', xml_declaration=True)
print(output)
