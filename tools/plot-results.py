#!/usr/bin/env python3
"""Render the published comparison from raw CSV files; optional matplotlib tool."""
import csv
import statistics
import sys
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.ticker import FixedLocator, FixedFormatter
from xml.etree import ElementTree as ET

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('docs/results/2026-10-12-intel-i9')
groups = {}
run_ids = set()
for source in sorted(root.glob('*/measurements.csv')):
    with source.open() as stream:
        for row in csv.DictReader(stream):
            key = (row['database'], row['implementation'], row['mode'])
            groups.setdefault(key, []).append(float(row['duration_ms']) / 1000)
            run_ids.add(row['run_id'])
if len(run_ids) != 2 or any(len(values) != 2 for values in groups.values()):
    raise ValueError('This published chart expects exactly two complete runs')
median = {key: statistics.median(values) for key, values in groups.items()}
plt.rcParams.update({'font.family': 'Arial', 'svg.fonttype': 'none', 'font.size': 11})
# Match scriptella.org's light theme and browser-native typography.
background, ink, muted, border = '#f7f8f7', '#202320', '#4d534e', '#d6dbd7'
colors = {'scriptella': '#197a43', 'jdbc': '#68786c', 'csvsql': '#8a6118', 'copy': '#aeb7b0'}
font_stack = 'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif'
from matplotlib.patches import FancyBboxPatch
ET.register_namespace('', 'http://www.w3.org/2000/svg')
ns = '{http://www.w3.org/2000/svg}'
for db, database in [('postgres', 'PostgreSQL 17.11'), ('mariadb', 'MariaDB 11.8.8')]:
    ratio = median[(db, 'csvsql', 'chunk_1000')] / median[(db, 'scriptella', 'batch_1000')]
    fig = plt.figure(figsize=(12.2, 6.2), facecolor=background)
    fig.text(.06, .93, 'Scriptella CSV import benchmark', color=muted, fontsize=13, weight='bold')
    fig.text(.06, .855, f'Batched CSV imports. {database}.', color=ink, fontsize=25, weight='bold')
    fig.text(.06, .801, 'One million rows · median of two local runs', color=muted, fontsize=12)
    fig.text(.94, .801, 'Lower duration is faster', ha='right', color=muted, fontsize=11, fontstyle='italic')
    fig.text(.06, .70, f'{ratio:.1f}×', color=colors['scriptella'], fontsize=31, weight='bold')
    fig.text(.205, .72, 'Scriptella batch 1000 vs csvsql chunk 1000', color=ink, fontsize=12)
    fig.text(.205, .683, 'Ratio of median durations; chunking mechanisms differ.', color=muted, fontsize=10)
    fig.add_artist(FancyBboxPatch((.035, .168), .93, .45, boxstyle='round,pad=0.008,rounding_size=0.008',
                   transform=fig.transFigure, facecolor='white', edgecolor=border, linewidth=1, zorder=0))
    ax = fig.add_axes([.30, .27, .60, .30], facecolor='white')
    selected = [('scriptella', 'batch_1000', 'Scriptella · batch 1000'),
                ('jdbc', 'batch_1000', 'JDBC · batch 1000'),
                ('csvsql', 'chunk_1000', 'csvsql · chunk 1000')]
    if db == 'postgres': selected.append(('copy', 'native_reference', 'COPY · native reference'))
    ax.set_xscale('log'); ax.set_xlim(1, 1200); ax.set_ylim(len(selected)-.4, -.6)
    ax.xaxis.set_major_locator(FixedLocator([1, 10, 100, 1000]))
    ax.xaxis.set_major_formatter(FixedFormatter(['1 s', '10 s', '100 s', '1,000 s']))
    ax.minorticks_off(); ax.grid(axis='x', color=border, linewidth=.8, zorder=0)
    for spine in ax.spines.values(): spine.set_visible(False)
    ax.tick_params(axis='both', length=0, colors=muted); ax.tick_params(axis='y', pad=15)
    ax.set_yticks(range(len(selected)), [case[2] for case in selected])
    for y, (implementation, mode, _) in enumerate(selected):
        value = median[(db, implementation, mode)]
        ax.barh(y, value-1, left=1, height=.43, color=colors[implementation], zorder=3)
        ax.text(value*1.12, y, f'{value:.2f} s', va='center', color=ink, weight='bold', fontsize=11)
    ax.set_xlabel('Duration in seconds · logarithmic axis', color=muted, labelpad=11, fontsize=10)
    fig.text(.06, .105, 'Intel i9-9980HK · Colima x86_64, 4 CPUs / 6 GiB · Scriptella 1.6 · benchmark v2', color=muted, fontsize=9)
    note = 'COPY is a separate native reference. ' if db == 'postgres' else ''
    fig.text(.06, .062, note+'Exploratory results for this machine and fixture; not universal performance claims.', color=muted, fontsize=9)
    output = root / f'{db}.svg'
    fig.savefig(output, format='svg', facecolor=background)
    tree = ET.parse(output); svg = tree.getroot()
    svg.set('role', 'img'); svg.set('aria-labelledby', 'chart-title chart-desc')
    svg.attrib.pop('width', None); svg.attrib.pop('height', None)
    title = ET.Element(ns+'title', {'id': 'chart-title'}); title.text = f'{database}: median CSV import durations, two local runs'
    description = ET.Element(ns+'desc', {'id': 'chart-desc'})
    description.text = f'Logarithmic duration axis, lower is faster. Scriptella batch 1000 is {ratio:.1f} times faster than csvsql chunk 1000. Detailed report includes every individual value and unbatched cases.'
    svg.insert(0, description); svg.insert(0, title)
    for text in svg.iter(ns+'text'):
        text.set('style', text.get('style', '').replace("'Arial'", font_stack))
    tree.write(output, encoding='utf-8', xml_declaration=True)
    fig.savefig(root / f'{db}-preview.png', dpi=110, facecolor=background)
    plt.close(fig)
    print(output)
