# Benchmark guide

This guide documents benchmark v4: its workload, measurement method, run
metadata, and reporting rules. For the overview and current results, see the
[root README](../README.md). Run all commands below from the repository root.

Version 4 enables pgJDBC `reWriteBatchedInserts=true` equally for Scriptella
and handwritten JDBC, following the separately reported
[false/true follow-up](results/2026-10-21-pg-rewrite/README.md). Version 3 used
the PostgreSQL driver default, false. Historical results remain unchanged and
must not be pooled with v4. Standard preparation requires
`POSTGRES_REWRITE_BATCHED_INSERTS=true`. Both implementations pass it as a
connection property; the Scriptella ETL places it next to `statement.batchSize`. No Scriptella adapter default is changed.

Version 3 simplified Scriptella's CSV setup by removing benchmark-specific
custom formatter classes and using the CSV driver's built-in formatters. This
is a configuration change; it is not a claim of improved performance.

## Running and inspecting results

Clone the repository and enter its directory:

```sh
git clone https://github.com/scriptella/scriptella-benchmarks.git
cd scriptella-benchmarks
```

The host needs a Docker-compatible runtime and Docker Compose. The image owns
Java 17, Maven, Python/csvkit, the selected Maven-published Scriptella release,
and JDBC dependencies. It does not use a Scriptella source checkout or distribution ZIP.

### Quick correctness check

Start with a smoke run, which validates all eleven imports using 2,001-row
prefixes, including a final partial batch. It records no benchmark timings.
The first invocation also downloads the images and builds the benchmark image.

```sh
BENCHMARK_COMMAND=smoke SCRIPTELLA_VERSION=1.6 docker compose up --build \
  --abort-on-container-exit --exit-code-from benchmark
```

### Full million-row comparison

The historical local runs took about 33–35 minutes each; runtime depends on
your machine and Docker allocation. Use `--force-recreate` to start fresh
containers if you have already run the smoke check:

```sh
ENVIRONMENT_ID=my-machine-docker \
ENVIRONMENT_DESCRIPTION='Describe CPU, architecture and Docker runtime' \
SCRIPTELLA_VERSION=1.6 docker compose up --build --force-recreate \
  --abort-on-container-exit --exit-code-from benchmark
```

### Preparation and output files

This starts PostgreSQL 17.11 and MariaDB 11.8.8, waits for health checks,
generates one deterministic million-row CSV, verifies the requested Scriptella
artifact versions and their SHA-256 checksums, and prepares each database. It
inserts and verifies the first, middle, and last fixture values using typed
JDBC bindings, then truncates the table and verifies it is empty. Preparation
is followed by the eleven-case comparison described under Measurement method. Results and diagnostics
are saved under `results/run-<timestamp>-<id>/`:

- `measurements.csv`: only successfully imported and independently verified cases.
- `preparation.properties`: environment, resolved dependency versions/checksums,
  fixture details, database settings, phase, and final success/failure status.
- Per-case smoke, warmup, and measured process logs.

On failure, the command exits nonzero and keeps completed valid measurements and
diagnostics.

### Benchmark versioning and compatibility

New measurements record the stable `benchmark_version=v4`
compatibility identifier. Repeated runs of an unchanged benchmark do not require
Git commits. Increment the version when meaningful implementation or methodology
changes affect comparability, such as timing boundaries, conversions, transaction
behavior, or case order. Other environment, fixture, dependency and case metadata
must still match when aggregating.

`BENCHMARK_REVISION` is optional build-time traceability metadata (default `n/a`),
and does not determine compatibility. You may supply a Git SHA; mark a modified
checkout's traceability value as dirty rather than presenting it as a clean
commit. Existing raw measurements retain their original schema and revision
markers. Unversioned legacy runs require an explicit documented compatibility
attribution outside the raw files before inclusion in a versioned aggregate.

### Configuration and cleanup

The Compose configuration accepts these environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `SCRIPTELLA_VERSION` | `1.6` | Maven-published release included in the image. |
| `POSTGRES_REWRITE_BATCHED_INSERTS` | `true` | Shared PostgreSQL connection property; false/true in the follow-up. |
| `BENCHMARK_COMMAND` | `run` | Select `run`, `smoke`, or `prepare`. |
| `ENVIRONMENT_ID` | `local-unspecified` | Identify the measurement environment. |
| `ENVIRONMENT_DESCRIPTION` | `Unspecified local Docker environment` | Describe hardware and runtime allocation. |
| `BENCHMARK_REVISION` | `n/a` | Optional build-time source traceability. |

To perform only preparation, add `BENCHMARK_COMMAND=prepare` to the Compose
command. Preparation and smoke directories are labeled `preparation-...` or
`smoke-...` and contain no `measurements.csv`. The fixture stays inside the
stopped benchmark container.

Credentials are disposable, databases have no host ports, and no persistent
database volumes are configured. Remove stopped containers and their anonymous
database volumes after inspection:

```sh
docker compose down --volumes
```

For another run, use `docker compose up --build --force-recreate` with the same
exit flags. Each run gets its own directory.

## Fixture and schema

The CSV has header `id,name,category,amount,created_at`, UTF-8 encoding, LF
line endings, and exactly 1,000,000 rows. For row `i`, values are `i`,
`row_<i>`, `category_<i % 10>`, `(i % 100000)` integer cents rendered with
two decimal places, and `2020-01-01 00:00:00` plus `i` seconds. Timestamps are
timezone-free, whole-second values; clients and database sessions use UTC.
There are no nulls, transformations, indexes, or primary keys.

The table uses BIGINT, VARCHAR(32), VARCHAR(16), DECIMAL(12,2), and PostgreSQL
TIMESTAMP WITHOUT TIME ZONE or MariaDB DATETIME. All columns are NOT NULL;
MariaDB uses InnoDB. Durability settings retain normal defaults. PostgreSQL explicitly enables
batch insert rewriting; MariaDB batch options retain driver defaults. Preparation records server versions and selected durability settings.

## Measurement method

Every run first smoke-checks all eleven cases. For each database, the fixed
order is Scriptella unbatched, Scriptella batch 1000, JDBC unbatched, JDBC batch
1000, then csvsql chunk 1000, then PostgreSQL native COPY reference on PostgreSQL
only; PostgreSQL runs before MariaDB. Each case uses one transaction and one
commit per import, the same JDBC settings within each database, and a pre-created table.
PostgreSQL explicitly enables `reWriteBatchedInserts=true` for both Java paths.
This overrides pgJDBC's default of false; unbatched INSERTs do not use batch
rewriting. COPY remains the native path. csvsql uses its existing psycopg2 path
and is unaffected by the JDBC option. MariaDB defaults
`useBulkStmts=false` and `useBulkStmtsForInserts=true`. Both Java implementations
use the same settings for their database. JDBC uses Apache Commons CSV 1.14.1,
independent of Scriptella. Scriptella uses the CSV driver's built-in number and
timestamp parsing via `format.<column>.type`; no benchmark-specific formatter
classes are involved. Both paths preserve integer IDs, exact decimal cents,
and whole-second timestamps. Scriptella passes parsed `Number` values, while
the JDBC baseline binds IDs as `long` and amounts as `BigDecimal`; these are
different Java/JDBC conversion paths. Verification compares stored decimal
values for representative fractions such as 1.23 and 999.99 in both databases.
See the [short Scriptella ETL starter and full benchmark configuration](scriptella-etl.md).

Timing includes fresh client process startup through successful
exit after commit and cleanup, excluding fixture generation, database startup,
warmup, reset, and verification. A fixed 20,000-row untimed warmup precedes each
measured case, followed by table reset. It primes database and file caches; it
does not warm the fresh measured JVM's JIT. Heap and garbage collector selection
use JVM defaults; every Java process uses `-Duser.timezone=UTC`.

After each import exits, a separate connection checks row count and first,
middle, and last IDs, strings, decimals and timestamps. The runner then writes
duration and throughput and resets the table. Console ratios use current-run
durations: Scriptella vs JDBC is `JDBC / Scriptella` for each matching mode;
batching speedup is `unbatched / batch-1000` for each implementation;
Scriptella vs csvsql is `csvsql / Scriptella` for each Scriptella mode;
COPY vs Scriptella is `Scriptella / COPY`. Above 1
means the first named implementation or batched mode is faster.

csvsql 2.2.0 uses the [documented direct-import workflow](https://csvkit.readthedocs.io/en/2.2.0/scripts/csvsql.html):
`csvsql --db <SQLAlchemy URL> --tables csv_import --no-create --insert --chunk-size 1000 <fixture>`.
Type inference, dialect sniffing, SQLAlchemy and DBAPI settings use normal defaults.
Chunk 1000 aligns row grouping with JDBC batching; it is not an optimal-size claim.
The image pins SQLAlchemy 2.0.46, psycopg2-binary 2.9.11 and PyMySQL 1.1.2.
Python package versions and invocation are recorded in run metadata. Python/csvkit
startup is included in csvsql timing. COPY streams the same CSV through pgJDBC,
with explicit columns and CSV/header/UTF-8 settings, in one transaction.

## Aggregate compatible runs

Place two or three compatible run directories under one results directory, then:

```sh
docker compose run --no-deps --rm benchmark aggregate /results
```

The command writes `/results/report.md`, including median duration, median
throughput, every individual measurement and ratios of median durations.
It scans recursively and rejects incompatible metadata, duplicate run/case rows,
invalid timings and legacy files missing compatibility fields. Select compatible
runs in a separate directory if `/results` also contains older experiments; use:

```sh
docker compose run --no-deps --rm \
  -v /absolute/path/to/selected-runs:/results benchmark aggregate /results
```

To regenerate the published local report with the benchmark image:

```sh
docker compose run --build --no-deps --rm \
  -v "$PWD/docs/results/2026-10-12-local:/results" benchmark aggregate /results
```
Scriptella releases remain separate; Git revision is traceability only.
Partial runs contribute only verified cases; comparisons with missing cases are omitted.
The report records sample counts so incomplete comparisons remain visible.

Results from different methodologies are not combined.

## GitHub Actions

Use **Actions → CSV import benchmark → Run workflow**, with the numeric
Maven-published `scriptella_version` input (default `1.6`). The manual workflow
runs the same full Compose experiment on `ubuntu-24.04`, generates a single-run
report, and uploads measurements and diagnostics as a downloadable artifact.
Completed measurements and logs are uploaded even if a later case fails.
Artifacts are retained for 30 days. The runner needs no project secrets.

CI establishes reproducibility and catches breakage. Compare implementations
within each run; GitHub-hosted hardware and load can vary across runs. Matching
environment IDs or runner labels do not prove identical hardware. Use repeated
runs on a controlled machine to measure performance changes over time, and keep
CI results separate from named-machine results.

## Expected duration and resource use

The published million-row local runs took about 33–35 minutes each. Runtime varies
by machine and Docker allocation; csvsql spent about 9–10 minutes per database in
these runs. Its normal type-inference path loads an in-memory table before
inserting, so a quiet console can mean parsing rather than a stalled import.
The runner prints each stage and retains subprocess logs. It has no progress
percentage. Preparation and smoke-only runs offer a short correctness check.

The experiment does not routinely profile RAM. Published memory observations are
coarse process resident-memory diagnostics from part of the first local run,
with their collection method and limits documented alongside the results.

This is a directional engineering experiment, not an industry-grade benchmark.
Machine, Docker environment, runtime and database versions, caches, fixed case
order, and startup overhead affect results. Repeated compatible runs help
interpret a result; they do not make it universal. csvsql chunking differs from
JDBC batching. PostgreSQL COPY is a native reference, not a portable competitor.

## Future local follow-ups

For focused local experiments, use PostgreSQL and MariaDB with Scriptella and
handwritten JDBC. Do not rerun csvsql locally unless a relevant change or a
specific csvsql question warrants it. Keep the full comparison, including
csvsql, in CI. Cite CI measurements as separately labeled context; do not pool
them with local measurements or calculate cross-environment speedups.
The default eleven-case runner remains available for full CI comparisons;
the PostgreSQL rewrite follow-up uses `compose.pg-rewrite.yaml` to run only
the six relevant batch configurations.
