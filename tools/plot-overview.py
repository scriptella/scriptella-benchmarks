#!/usr/bin/env python3
"""Render compatible complete runs or the rewrite follow-up (requires matplotlib).

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
sources = sorted(root.glob('*/measurements.csv'))
if not sources:
    raise ValueError('Overview requires measurements.csv inputs')
with sources[0].open() as stream:
    rows = list(csv.DictReader(stream))
if rows and 'rewrite' in rows[0]:
    if len(sources) != 1:
        raise ValueError('Rewrite overview requires one follow-up dataset')
    properties = {}
    metadata_path = sources[0].parent / 'preparation.properties'
    if not metadata_path.is_file():
        raise ValueError(f'{metadata_path}: required preparation metadata is missing')
    for line in metadata_path.read_text().splitlines():
        if '=' in line and not line.startswith(('#', '!')):
            key, value = line.split('=', 1)
            if key.strip() == 'scriptella_version':
                if key.strip() in properties:
                    raise ValueError('Duplicate Scriptella version in preparation metadata')
                properties[key.strip()] = value.strip()
    version = properties.get('scriptella_version')
    if not version:
        raise ValueError(f'{metadata_path}: missing scriptella_version')
    scriptella_label = f'Scriptella {version}'
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
    for ax, impl, label in zip(axes, ['scriptella', 'jdbc'], [scriptella_label, 'Handwritten JDBC']):
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

# Use the same compatibility validator as the per-database comparison charts.
from plot_data import load_measurements
import math

groups, run_ids, meta = load_measurements(root)
values = {key: statistics.median(samples) for key, samples in groups.items()}
python_impl = 'pandas' if meta['benchmark_version'] == 'v5' else 'csvsql'
python_label = 'Pandas + SQLAlchemy' if python_impl == 'pandas' else 'csvsql'
cases = [('scriptella', 'batch_1000', 'Scriptella · batch 1000'),
         ('jdbc', 'batch_1000', 'JDBC · batch 1000'),
         (python_impl, 'chunk_1000', python_label + ' · write 1000')]
colors = ['#197a43', '#526d82', '#94651b', '#747c76']
is_v5 = meta['benchmark_version'] == 'v5'
is_ci = meta['environment_id'].startswith('gha-')
run_label = 'CI' if is_ci else 'local'
sample_label = f'Median of {len(run_ids)} {run_label} runs' if len(run_ids) > 1 else f'One {run_label} run'
plt.rcParams.update({'font.family': 'DejaVu Sans', 'font.size': 11, 'svg.fonttype': 'none'})
fig, axes = plt.subplots(2, 1, figsize=(11.2, 6.8), sharex=True)
fig.subplots_adjust(left=.36, right=.94, top=.77, bottom=.18, hspace=.57)
fig.suptitle('End-to-end CSV import times', x=.035, y=.97,
             ha='left', fontsize=21, weight='bold', color='#202320')
fig.text(.035, .895, f"One million rows · {sample_label.lower()} · benchmark {meta['benchmark_version']}"
         f" · Scriptella {meta['scriptella_version']}", fontsize=11, color='#444444')
fig.text(.035, .847, 'Bars show median durations; dots show individual runs.', fontsize=10, color='#444444')
maximum = max(max(samples) for samples in groups.values())
for ax, db, title in zip(axes, ['postgres', 'mariadb'], ['PostgreSQL', 'MariaDB']):
    selected = list(cases)
    if db == 'postgres':
        selected.append(('copy', 'native_reference', 'COPY · native reference'))
    elif (db, 'load_data', 'native_reference') in values:
        selected.append(('load_data', 'native_reference', 'LOAD DATA LOCAL · native reference'))
    ax.set_title(title, loc='left', weight='bold', pad=11, color='#202320')
    if is_v5:
        ax.set_xlim(0, math.ceil(maximum * 1.22 / 5) * 5)
    else:
        ax.set_xscale('log')
        ax.set_xlim(.1, 1200)
        ax.xaxis.set_major_locator(FixedLocator([.1, 1, 10, 100, 1000]))
        ax.xaxis.set_major_formatter(FixedFormatter(['0.1', '1', '10', '100', '1,000']))
        ax.minorticks_off()
    ax.set_ylim(len(selected)-.5, -.5)
    ax.grid(axis='x', color='#dce1dd', linewidth=.8, zorder=0)
    ax.set_yticks(range(len(selected)), [case[2] for case in selected])
    ax.tick_params(axis='both', length=0, pad=10, colors='#444444')
    for spine in ax.spines.values():
        spine.set_visible(False)
    for y, (impl, mode, _) in enumerate(selected):
        key = db, impl, mode
        value = values[key]
        left = 0 if is_v5 else .1
        ax.barh(y, value-left, left=left, height=.53, color=colors[y], zorder=2)
        ax.scatter(groups[key], [y] * len(groups[key]), s=22, facecolors='white',
                   edgecolors='#364039', linewidths=.9, zorder=3)
        end = max(groups[key]) + maximum * .024 if is_v5 else max(groups[key]) * 1.16
        ax.text(end, y, f'{value:.3f} s', va='center', fontsize=10, weight='bold', color='#202320')
axes[-1].set_xlabel('Duration in seconds · lower is faster' + ('' if is_v5 else ' · logarithmic scale'),
                    labelpad=12, color='#444444')
fig.text(.035, .06, f"Environment: {meta['environment_id']} · fresh process start through exit", fontsize=9, color='#444444')
fig.text(.035, .025, 'COPY and LOAD DATA LOCAL are native references with different parsing/error semantics.',
         fontsize=9, color='#444444')
output = root / 'overview.svg'
fig.savefig(output, metadata={'Date': None})
fig.savefig(root / 'overview-preview.png', dpi=160)
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
desc.text = f"One million rows; {sample_label.lower()}; Scriptella {meta['scriptella_version']}. "
for db, label in [('postgres', 'PostgreSQL'), ('mariadb', 'MariaDB')]:
    selected = list(cases)
    if db == 'postgres':
        selected.append(('copy', 'native_reference', 'COPY native reference'))
    elif (db, 'load_data', 'native_reference') in values:
        selected.append(('load_data', 'native_reference', 'LOAD DATA LOCAL native reference'))
    desc.text += label + ': ' + ', '.join(f'{name} {values[db, impl, mode]:.3f} seconds'
                                         for impl, mode, name in selected) + '. '
desc.text += ('Linear axis starts at zero. ' if is_v5 else 'Logarithmic duration scale. ')
desc.text += 'Lower is faster; bars are medians, dots are individual measurements. Native loaders are separate references.'
svg.insert(0, desc)
svg.insert(0, title)
tree.write(output, encoding='utf-8', xml_declaration=True)
print(output)
