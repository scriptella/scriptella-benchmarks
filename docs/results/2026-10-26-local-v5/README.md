# Local CSV import results — final benchmark v5

Two compatible eight-case runs on 2026-10-26 local time (2026-10-27 UTC), using
Scriptella **1.6**. These selected measurements are copied byte-for-byte from the
final v5 experiment. Earlier seven-/eleven-case spikes and historical v4 results
are excluded. The chart reports end-to-end import durations, not isolated SQL
execution or ETL abstraction overhead.

![Final v5 CSV import durations: median of two local runs, with individual observations. Linear axis starts at zero; lower is faster.](overview.svg)

Values are **median seconds (run 1, run 2)**. Scriptella and JDBC use batch 1000.

| Database | Scriptella | JDBC | Pandas + SQLAlchemy | Native load |
|---|---:|---:|---:|---:|
| PostgreSQL | 10.053 (9.907, 10.199) | 7.579 (7.992, 7.165) | 29.106 (31.013, 27.200) | COPY: 2.366 (2.076, 2.656) |
| MariaDB | 9.227 (9.113, 9.340) | 5.622 (5.823, 5.422) | 19.609 (19.942, 19.276) | LOAD DATA LOCAL: 3.039 (3.235, 2.843) |

| Database | Pandas / Scriptella | JDBC / Scriptella | Scriptella / native |
|---|---:|---:|---:|
| PostgreSQL | 2.895× | 0.754× | 4.250× |
| MariaDB | 2.125× | 0.609× | 3.036× |

Ratios use median durations. Pandas took longer than Scriptella on both
databases; JDBC was faster than Scriptella. Native loaders were fastest here.
Pandas remains active as a familiar portable reference. COPY and LOAD DATA LOCAL
are native bulk-loading references with different parsing/error semantics.
These observations do not establish a universal ranking or a comparison against
historical results on different machines or CI runners.

## Environment and evidence

- Intel Mac x86_64; existing Colima VM, 4 CPUs / 6 GiB; JVM defaults.
- Environment ID: `intel-colima-v5-final8`; benchmark version: `v5`.
- Case definition: `v5-final-eight-pg-rewrite-true-warmup20000-pandas-read10000-write1000-local-infile-v1`.
- Scriptella core/drivers 1.6; Java 17.0.17+10; Commons CSV 1.14.1.
- PostgreSQL 17.11, pgJDBC 42.7.13, `reWriteBatchedInserts=true` for both Java contenders.
- MariaDB 11.8.8, Connector/J 3.5.7; native loader sets `allowLocalInfile=true`.
- Python 3.12.3; Pandas 2.3.3; NumPy 2.3.4; SQLAlchemy 2.0.46;
  psycopg2-binary 2.9.11; PyMySQL 1.1.2.
- Source traceability: base Git commit `eecd095b71b251e3e9e280fd45eb9da8fabf7b34`
  plus the uncommitted final v5 implementation, recorded as
  `eecd095-dirty-v5-final8`. This is not presented as a clean source commit.

Raw measurements and preparation metadata retain the original timestamps,
version markers, fixture hash, dependency checksums and settings:

- [Run 1 measurements](run-20261027T005424Z-3a5bdee1-b718-4a4b-ba9d-203f857e2b87/measurements.csv)
  and [metadata](run-20261027T005424Z-3a5bdee1-b718-4a4b-ba9d-203f857e2b87/preparation.properties).
- [Run 2 measurements](run-20261027T005620Z-02b005f9-87f7-494c-86e3-3ea9a3f12ccc/measurements.csv)
  and [metadata](run-20261027T005620Z-02b005f9-87f7-494c-86e3-3ea9a3f12ccc/preparation.properties).
- [Generated aggregate](report.md): full compatibility metadata, individual
  values, throughput and ratios. [Evidence checksums](SHA256SUMS).

## Method and correctness

Each run imports the unchanged deterministic million-row CSV into the shared
schema. A 2,001-row smoke checks all eight cases, and a verified 20,000-row warmup
precedes each measured import. Tables reset independently between imports.
Fresh process startup, parsing/conversion, database writes, commit and cleanup
are timed through successful exit. Preparation, warmup, reset and verification
are excluded. All sixteen measured imports passed independent JDBC count and
sample-value verification, including exact decimal amounts and UTC timestamps.

Pandas reads 10,000 rows at a time and uses default `to_sql` insertion with
1,000-row write chunks in one transaction. PostgreSQL COPY streams the same CSV.
MariaDB LOAD DATA LOCAL streams the fixture from the client, without a separate
file copy into the database container; it explicitly maps columns, UTF-8/LF
handling and the header. A pre-measurement InnoDB probe verified uncommitted
rows were invisible to another connection and rollback left the table empty.
The actual imports committed before independent verification.

Durability was not weakened for native loading: PostgreSQL synchronous_commit
and fsync are on; MariaDB innodb_flush_log_at_trx_commit is 1. MariaDB log_bin is
0, binlog_format is MIXED and sync_binlog is 0. LOCAL INFILE uses server parsing
and relaxes strict-mode handling; the client rejects load warnings. This does
not imply identical error, trigger, locking or replication semantics to batched
INSERTs. See the [benchmark guide](../../benchmark-guide.md#mariadb-native-load-data).

Only two sequential local runs were collected. Fixed order, cache state,
process startup costs and host load affect the results. Individual observations
are plotted as dots, not confidence intervals. RAM and swap were not measured.
The published article is unchanged.

## Reproduce the report and chart

Run from the benchmark repository root. The chart generator needs matplotlib
as an optional local plotting dependency; it is not a benchmark runtime dependency.
Both chart and report validate compatibility before aggregation. The checked-in
chart was rendered with Python 3.14.6 and matplotlib 3.11.2 in a separate temporary
plotting environment; measured benchmark runtime dependencies were unchanged.

```sh
java -cp 'target/classes:target/dependency/*' org.scriptella.benchmarks.Benchmark aggregate docs/results/2026-10-26-local-v5
python3 tools/plot-overview.py docs/results/2026-10-26-local-v5
```

For the benchmark itself, follow the [smoke and full-run instructions](../../benchmark-guide.md),
using two sequential runs with the same environment and settings. Keep incompatible
case definitions and historical versions in separate aggregation directories.
