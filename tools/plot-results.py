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

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('docs/results/2026-10-12-local')
groups = {}
run_ids = set()
metadata = None
for source in sorted(root.glob('*/measurements.csv')):
    with source.open() as stream:
        for row in csv.DictReader(stream):
            if metadata is None: metadata = row
            for field in ('environment_id', 'fixture_sha256', 'benchmark_version', 'case_definition', 'scriptella_version'):
                if metadata[field] != row[field]: raise ValueError(f'Incompatible {field}; keep environments separate')
            key = (row['database'], row['implementation'], row['mode'])
            groups.setdefault(key, []).append(float(row['duration_ms']) / 1000)
            run_ids.add(row['run_id'])
if not run_ids or len(groups) != 11 or any(len(values) != len(run_ids) for values in groups.values()):
    raise ValueError('Charts require complete, compatible eleven-case runs')
median = {key: statistics.median(values) for key, values in groups.items()}
plt.rcParams.update({'font.family': 'Arial', 'svg.fonttype': 'none', 'font.size': 11})
is_ci = metadata['environment_id'].startswith('gha-')
caption = 'One million rows · one GitHub Actions run' if is_ci and len(run_ids) == 1 else f'One million rows · median of {len(run_ids)} runs'
footer = ('GitHub Actions · Ubuntu 24.04 x86_64' if is_ci else 'Intel i9-9980HK · Colima x86_64, 4 CPUs / 6 GiB') + ' · Scriptella 1.6 · benchmark v2'
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
    fig.text(.06, .801, caption, color=muted, fontsize=12)
    fig.text(.06, .70, f'{ratio:.1f}×', color=colors['scriptella'], fontsize=31, weight='bold')
    fig.text(.205, .72, 'Scriptella batch 1000 vs csvsql chunk 1000', color=ink, fontsize=12)
    fig.text(.205, .683, ('Duration ratio' if len(run_ids) == 1 else 'Ratio of median durations') + '; chunking mechanisms differ.', color=muted, fontsize=10)
    fig.add_artist(FancyBboxPatch((.035, .168), .93, .45, boxstyle='round,pad=0.008,rounding_size=0.008',
                   transform=fig.transFigure, facecolor='white', edgecolor=border, linewidth=1, zorder=0))
    ax = fig.add_axes([.30, .27, .60, .30], facecolor='white')
    selected = [('scriptella', 'batch_1000', 'Scriptella · batch 1000'),
                ('jdbc', 'batch_1000', 'JDBC · batch 1000'),
                ('csvsql', 'chunk_1000', 'csvsql · chunk 1000')]
    if db == 'postgres': selected.append(('copy', 'native_reference', 'COPY · native reference'))
    ax.set_xscale('log'); ax.set_xlim(.1, 1200); ax.set_ylim(len(selected)-.4, -.6)
    ax.xaxis.set_major_locator(FixedLocator([.1, 1, 10, 100, 1000]))
    ax.xaxis.set_major_formatter(FixedFormatter(['0.1 s', '1 s', '10 s', '100 s', '1,000 s']))
    ax.minorticks_off(); ax.grid(axis='x', color=border, linewidth=.8, zorder=0)
    for spine in ax.spines.values(): spine.set_visible(False)
    ax.tick_params(axis='both', length=0, colors=muted); ax.tick_params(axis='y', pad=15)
    ax.set_yticks(range(len(selected)), [case[2] for case in selected])
    for y, (implementation, mode, _) in enumerate(selected):
        value = median[(db, implementation, mode)]
        ax.barh(y, value-.1, left=.1, height=.43, color=colors[implementation], zorder=3)
        ax.text(value*1.12, y, f'{value:.2f} s', va='center', color=ink, weight='bold', fontsize=11)
    ax.set_xlabel('Duration in seconds · logarithmic axis', color=muted, labelpad=11, fontsize=10)
    fig.text(.60, .181, 'Lower duration is faster', ha='center', color=muted, fontsize=10, fontstyle='italic')
    fig.text(.06, .105, footer, color=muted, fontsize=9)
    note = 'COPY is a separate native reference. ' if db == 'postgres' else ''
    fig.text(.06, .062, note+'Exploratory results for this machine and fixture; not universal performance claims.', color=muted, fontsize=9)
    output = root / f'{db}.svg'
    fig.savefig(output, format='svg', facecolor=background)
    tree = ET.parse(output); svg = tree.getroot()
    svg.set('role', 'img'); svg.set('aria-labelledby', 'chart-title chart-desc')
    svg.attrib.pop('width', None); svg.attrib.pop('height', None)
    title = ET.Element(ns+'title', {'id': 'chart-title'}); title.text = f'{database}: CSV import durations, {len(run_ids)} run(s)'
    description = ET.Element(ns+'desc', {'id': 'chart-desc'})
    description.text = f'Logarithmic duration axis, lower is faster. Scriptella batch 1000 is {ratio:.1f} times faster than csvsql chunk 1000. Detailed report includes every individual value and unbatched cases.'
    svg.insert(0, description); svg.insert(0, title)
    for text in svg.iter(ns+'text'):
        text.set('style', text.get('style', '').replace("'Arial'", font_stack))
    tree.write(output, encoding='utf-8', xml_declaration=True)
    fig.savefig(root / f'{db}-preview.png', dpi=110, facecolor=background)
    plt.close(fig)
    print(output)

# Memory evidence is optional and belongs only to the observed local run.
summary = root / 'memory-summary.md'
if summary.exists():
    observed = {}
    for line in summary.read_text().splitlines():
        columns = [part.strip() for part in line.split('|')]
        if len(columns) == 6 and columns[1] == 'mariadb':
            observed[(columns[2], columns[3])] = float(columns[4])
    selected = [('scriptella', 'batch_1000', 'Scriptella · batch 1000'),
                ('jdbc', 'batch_1000', 'JDBC · batch 1000'),
                ('csvsql', 'chunk_1000', 'csvsql · chunk 1000')]
    if any((impl, mode) not in observed for impl, mode, _ in selected):
        raise ValueError('Missing curated local memory observations')
    fig = plt.figure(figsize=(12.2, 6.2), facecolor=background)
    fig.text(.06, .93, 'Scriptella CSV import benchmark', color=muted, fontsize=13, weight='bold')
    fig.text(.06, .855, 'Process memory during CSV imports.', color=ink, fontsize=25, weight='bold')
    fig.text(.06, .801, 'MariaDB · coarse observations from one local run', color=muted, fontsize=12)
    fig.text(.06, .71, 'Sampled resident memory', color=ink, fontsize=16, weight='bold')
    fig.text(.06, .672, 'Whole-process high-water observations, not precise peak-RAM measurements.', color=muted, fontsize=11)
    fig.add_artist(FancyBboxPatch((.035, .168), .93, .45, boxstyle='round,pad=0.008,rounding_size=0.008',
                   transform=fig.transFigure, facecolor='white', edgecolor=border, linewidth=1, zorder=0))
    ax = fig.add_axes([.30, .27, .60, .30], facecolor='white')
    ax.set_xlim(0, 1000); ax.set_ylim(2.6, -.6)
    ax.set_xticks([0, 250, 500, 750, 1000], ['0', '250', '500', '750', '1,000'])
    ax.grid(axis='x', color=border, linewidth=.8, zorder=0)
    for spine in ax.spines.values(): spine.set_visible(False)
    ax.tick_params(axis='both', length=0, colors=muted); ax.tick_params(axis='y', pad=15)
    ax.set_yticks(range(3), [case[2] for case in selected])
    for y, (impl, mode, _) in enumerate(selected):
        value = observed[(impl, mode)]
        ax.barh(y, value, height=.43, color=colors[impl], zorder=3)
        ax.text(value+18, y, f'≈ {value:.0f} MiB', va='center', color=ink, weight='bold', fontsize=11)
    ax.set_xlabel('Observed process high-water mark (MiB)', color=muted, labelpad=11, fontsize=10)
    fig.text(.60, .181, 'Lower memory use is better', ha='center', color=muted, fontsize=10, fontstyle='italic')
    fig.text(.06, .105, footer, color=muted, fontsize=9)
    fig.text(.06, .062, 'One-second Linux process sampling; peaks near exit may be missed. Database and container memory are excluded.', color=muted, fontsize=9)
    output = root / 'memory.svg'
    fig.savefig(output, format='svg', facecolor=background)
    tree = ET.parse(output); svg = tree.getroot()
    svg.set('role', 'img'); svg.set('aria-labelledby', 'chart-title chart-desc')
    svg.attrib.pop('width', None); svg.attrib.pop('height', None)
    title = ET.Element(ns+'title', {'id': 'chart-title'}); title.text = 'Coarse local MariaDB process-memory observations'
    description = ET.Element(ns+'desc', {'id': 'chart-desc'})
    description.text = 'Sampled resident high-water memory: Scriptella batch 1000 187.9 MiB, JDBC batch 1000 146.1 MiB, csvsql chunk 1000 894.6 MiB. One local run only, not precise peak RAM. No CI memory measurements.'
    svg.insert(0, description); svg.insert(0, title)
    for text in svg.iter(ns+'text'):
        text.set('style', text.get('style', '').replace("'Arial'", font_stack))
    tree.write(output, encoding='utf-8', xml_declaration=True)
    fig.savefig(root / 'memory-preview.png', dpi=110, facecolor=background)
    plt.close(fig)
    print(output)
