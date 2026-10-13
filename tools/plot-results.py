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
plt.rcParams.update({'font.family': 'DejaVu Sans', 'svg.fonttype': 'none', 'font.size': 11})
background, ink, muted = '#f5f7fb', '#14243b', '#55657a'
colors = {'scriptella': '#3469d9', 'jdbc': '#17877e', 'csvsql': '#c77a16', 'copy': '#758399'}
fig = plt.figure(figsize=(15.2, 10.8), facecolor=background)
fig.text(.07, .947, 'SCRIPTELLA  /  CSV IMPORT EXPERIMENT', color=muted, fontsize=11, weight='bold')
fig.text(.07, .897, 'One million rows. Two local runs.', color=ink, fontsize=28, weight='bold')
fig.text(.07, .859, 'Median wall-clock duration · fresh client process through commit and exit · lower is faster', color=muted, fontsize=12)
for x, db, title in [(.07, 'postgres', 'POSTGRESQL'), (.53, 'mariadb', 'MARIADB')]:
    ratio = median[(db,'csvsql','chunk_1000')] / median[(db,'scriptella','batch_1000')]
    fig.text(x, .802, f'{ratio:.1f}×', fontsize=29, weight='bold', color=colors['scriptella'])
    fig.text(x+.10, .807, f'{title}\nBatched Scriptella vs csvsql chunk 1000', fontsize=11, color=ink, linespacing=1.7)
axes = [fig.add_axes([.30,.445,.61,.265]), fig.add_axes([.30,.12,.61,.235])]
cases = [('scriptella','unbatched','Scriptella · unbatched'), ('scriptella','batch_1000','Scriptella · batch 1000'),
         ('jdbc','unbatched','JDBC · unbatched'), ('jdbc','batch_1000','JDBC · batch 1000'),
         ('csvsql','chunk_1000','csvsql · chunk 1000'), ('copy','native_reference','COPY · native reference')]
for ax, db, title in zip(axes, ['postgres','mariadb'], ['PostgreSQL 17.11','MariaDB 11.8.8']):
    selected = cases if db == 'postgres' else cases[:-1]
    ax.set_facecolor(background)
    ax.set_xscale('log')
    ax.set_xlim(1, 1200)
    ax.set_ylim(len(selected)-.45, -.7)
    ax.xaxis.set_major_locator(FixedLocator([1,10,100,1000]))
    ax.xaxis.set_major_formatter(FixedFormatter(['1 s','10 s','100 s','1,000 s']))
    ax.minorticks_off()
    ax.grid(axis='x', color='#dce3ee', linewidth=.9, zorder=0)
    for spine in ax.spines.values(): spine.set_visible(False)
    ax.tick_params(axis='both', length=0, colors=muted)
    ax.tick_params(axis='y', pad=15)
    ax.set_yticks(range(len(selected)), [case[2] for case in selected])
    ax.set_title(title, loc='left', color=ink, fontsize=16, weight='bold', pad=18)
    for y,(implementation, mode, _) in enumerate(selected):
        value = median[(db,implementation,mode)]
        ax.barh(y, value-1, left=1, height=.48, color=colors[implementation], zorder=3)
        ax.text(value*1.14,y,f'{value:.2f} s',va='center',color=ink,weight='bold',fontsize=11)
    ax.set_xlabel('Duration in seconds · logarithmic axis', color=muted, labelpad=11, fontsize=10)
fig.text(.07,.048,'Intel i9-9980HK · Colima x86_64, 4 CPUs / 6 GiB · Scriptella 1.6 · benchmark v2',color=muted,fontsize=10)
fig.text(.07,.025,'Exploratory results, not universal claims. COPY is a separate native reference; csvsql chunking differs from JDBC batching.',color=muted,fontsize=9)
output=root/'comparison.svg'
fig.savefig(output,format='svg',facecolor=background)
# Add accessible descriptions and responsive scaling without rasterizing text.
ET.register_namespace('', 'http://www.w3.org/2000/svg')
ns='{http://www.w3.org/2000/svg}'
tree=ET.parse(output); svg=tree.getroot(); svg.set('role','img'); svg.set('aria-labelledby','chart-title chart-desc')
svg.attrib.pop('width',None);svg.attrib.pop('height',None)
title=ET.Element(ns+'title',{'id':'chart-title'});title.text='Median durations for one million CSV rows, two local runs'
description=ET.Element(ns+'desc',{'id':'chart-desc'});description.text='Logarithmic duration axis, lower is faster. Batched Scriptella is 37.7 times faster than csvsql on PostgreSQL and 62.7 times on MariaDB. All individual values and methodology accompany this chart.'
svg.insert(0,description);svg.insert(0,title)
tree.write(output,encoding='utf-8',xml_declaration=True)
fig.savefig(root/'comparison-preview.png',dpi=110,facecolor=background)
print(output)
