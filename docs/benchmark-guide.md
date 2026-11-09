# Benchmark guide

This guide documents benchmark v5: its workload, measurement method, run
metadata, and reporting rules. For the overview and current results, see the
[root README](../README.md). Run all commands below from the repository root.

Version 5 replaces csvsql with Pandas; v4 evidence remains historical.

Version 4 enables pgJDBC `reWriteBatchedInserts=true` equally for Scriptella
and handwritten JDBC, following the separately reported
[false/true follow-up](results/2026-10-21-pg-rewrite/README.md). Version 3 used
the PostgreSQL driver default, false. Historical results remain unchanged and
must not be pooled with v4 or v5. Standard preparation requires
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
Java 17, Maven, Python/Pandas, the selected Maven-published Scriptella release,
and JDBC dependencies. It does not use a Scriptella source checkout or distribution ZIP.

### Quick correctness check

Start with a smoke run, which validates all eight imports using 2,001-row
prefixes, including a final partial batch. It records no benchmark timings.
The first invocation also downloads the images and builds the benchmark image.

```sh
BENCHMARK_COMMAND=smoke SCRIPTELLA_VERSION=1.7.0 docker compose up --build \
  --abort-on-container-exit --exit-code-from benchmark
```

### Full million-row comparison

The historical local runs took about 33–35 minutes each; runtime depends on
your machine and Docker allocation. Use `--force-recreate` to start fresh
containers if you have already run the smoke check:

```sh
ENVIRONMENT_ID=intel-colima-4cpu-6gib \
ENVIRONMENT_DESCRIPTION='Intel Mac Colima x86_64 4 CPUs 6 GiB' \
SCRIPTELLA_VERSION=1.7.0 docker compose up --build --force-recreate \
  --abort-on-container-exit --exit-code-from benchmark
```

Quoted local comparisons use an x86_64 Colima VM with 4 CPUs and 6 GiB of
memory. Keep that allocation for later local runs. The published v5 numbers use
it with Scriptella 1.7.0.

### Preparation and output files

This starts PostgreSQL 17.11 and MariaDB 11.8.8, waits for health checks,
generates one deterministic million-row CSV, verifies the requested Scriptella
artifact versions and their SHA-256 checksums, and prepares each database. It
inserts and verifies the first, middle, and last fixture values using typed
JDBC bindings, then truncates the table and verifies it is empty. Preparation
is followed by the eight-case comparison described under Measurement method. Results and diagnostics
are saved under `results/run-<timestamp>-<id>/`:

- `measurements.csv`: only successfully imported and independently verified cases.
- `preparation.properties`: environment, resolved dependency versions/checksums,
  fixture details, database settings, phase, and final success/failure status.
- Per-case smoke, warmup, and measured process logs.

On failure, the command exits nonzero and keeps completed valid measurements and
diagnostics.

### Benchmark versioning and compatibility

New measurements record the stable `benchmark_version=v5`
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
| `SCRIPTELLA_VERSION` | `1.7.0` | Maven-published release included in the image. |
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

Every run first smoke-checks every selected case. For each database, the fixed
order is Scriptella batch 1000, JDBC batch 1000, Pandas + SQLAlchemy chunk 1000,
then the native reference (PostgreSQL COPY or MariaDB LOAD DATA LOCAL INFILE); PostgreSQL runs before MariaDB. Each case uses one transaction and one
commit per import, the same JDBC settings within each database, and a pre-created table.
PostgreSQL explicitly enables `reWriteBatchedInserts=true` for both Java paths.
This overrides pgJDBC's default of false. COPY remains the PostgreSQL native path. Pandas uses its psycopg2 path
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
Scriptella vs Pandas is `Pandas / Scriptella` for each Scriptella mode;
Native loader vs Scriptella is `Scriptella / native loader`. Above 1
means the first named implementation or batched mode is faster.

Pandas reads the CSV with explicit int64 IDs and string columns in 10,000-row
chunks. Amount strings become Python `Decimal` objects without a floating-point
intermediate. Timestamp strings use an explicit whole-second format. Each chunk
calls `DataFrame.to_sql()` with `if_exists="append"`, `index=False`,
`chunksize=1000`, explicit SQLAlchemy types and the default insertion method.
One `engine.begin()` connection covers every chunk and commits once, then the
engine is disposed before exit. Both database sessions explicitly use UTC.
There is no `method="multi"`, COPY, or custom bulk path in the Pandas contender.

Exact invocation: `python /app/src/main/python/pandas_import.py <SQLAlchemy URL> <fixture>`.
URLs use `postgresql+psycopg2` or `mysql+pymysql`. The image pins Pandas 2.3.3,
NumPy 2.3.4, SQLAlchemy 2.0.46, psycopg2-binary 2.9.11 and PyMySQL 1.1.2;
all Python dependencies are pinned in `requirements.txt`. Resolved versions and
invocation are recorded in preparation metadata. Python startup is timed.
COPY streams the same CSV through pgJDBC in one transaction and remains a
native bulk-loading reference.

The final runner has exactly eight cases for both `run` and `smoke`. Earlier
seven-case, eleven-case and focused v5 experiments remain historical and cannot
be pooled with the final case definition. Unbatched implementations are retired;
`run-all`, `smoke-all` and the completed `spike` selection are removed. Reproduce
retired workflows from their recorded source revision, not this runner.

## MariaDB native LOAD DATA

`LOAD DATA LOCAL INFILE` sends the existing fixture from the benchmark client to
the server, avoiding a file copy or shared mount into the database container.
Connector/J 3.5.7 supplies a bound `InputStream`; the SQL filename is a protocol
placeholder. This uses the existing Java dependency, not a new native client.
The exact statement is recorded as `mariadb_load_sql` in every final measurement:

```sql
LOAD DATA LOCAL INFILE 'benchmark-stream.csv' INTO TABLE csv_import
CHARACTER SET utf8mb4
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"' ESCAPED BY ''
LINES TERMINATED BY '\n'
IGNORE 1 LINES
(id,name,category,amount,created_at)
```

The Java SQL literal contains an actual LF delimiter. The fixture is UTF-8,
LF-terminated CSV with one header. Explicit columns preserve the common schema;
server conversion loads BIGINT, exact DECIMAL(12,2) and whole-second DATETIME
values. CSV backslash escaping is disabled; this is the documented deterministic
fixture format, not a general CSV compatibility claim. The loader session uses
`time_zone='+00:00'`, `autocommit=false`, one load statement and one explicit
commit. It closes the stream, statement and connection before successful exit.
Fresh JVM startup, client file streaming, server parsing/loading, commit and
cleanup are timed, just as process startup through exit is timed for other cases.

Compose explicitly enables server `local_infile=1`. Only the native load
connection sets `allowLocalInfile=true`; the other contender configurations are
unchanged. Metadata records both settings, SQL, session SQL mode, binlog state,
binlog format, sync_binlog, server/driver versions and existing durability flags.
The target remains InnoDB, with `innodb_flush_log_at_trx_commit` unchanged from
its normal default (1 in the measured environment); durability is not weakened
for native loading. The untimed smoke preparation requires the load to remain
in a transaction, its rows to be invisible on a separate connection before
commit, and rollback to leave the table empty. The real smoke and measured
imports commit, exit, and pass independent count/value verification.

Native bulk loading is not semantically identical to batched INSERTs: parsing
and type conversion happen in the server, and MariaDB documents that LOCAL
relaxes strict modes such as `STRICT_TRANS_TABLES`. The implementation treats
any load warning as failure and rolls back rather than silently accepting
coercion. The transaction probe establishes behavior for the pinned InnoDB
workload; it does not establish identical error, trigger, replication or locking
behavior for all loaders/storage engines. No general claim about implicit
commits is inferred from other server versions or engines.

Primary references: [MariaDB LOAD DATA syntax and LOCAL behavior](https://mariadb.com/docs/server/reference/sql-statements/data-manipulation/inserting-loading-data/load-data-into-tables-or-index/load-data-infile),
[Connector/J client streaming](https://mariadb.com/docs/connectors/mariadb-connector-j/about-mariadb-connector-j).

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

The optional `tools/plot-results.py` tool validates the same version-specific
compatibility fields before importing matplotlib, computing medians or writing
charts. It additionally requires complete runs of one Scriptella release. Its
field-list parity with the Java aggregator and its Python regression tests run
under `mvn-lite test`. Run the plot-input tests alone without plotting packages
using `python3 -m unittest discover -s tools -p test_plot_data.py`.

For rewrite follow-up charts, `tools/plot-overview.py` requires the run's
`preparation.properties` with a `scriptella_version` entry for the chart label.


## GitHub Actions

Use **Actions → CSV import benchmark → Run workflow**, with the numeric
Maven-published `scriptella_version` input (default `1.7.0`). The manual workflow
runs the same eight-case Compose experiment on `ubuntu-24.04`, generates a single-run
report, and uploads measurements and diagnostics as a downloadable artifact.
Completed measurements and logs are uploaded even if a later case fails.
Artifacts are retained for 30 days. The runner needs no project secrets.

CI establishes reproducibility and catches breakage. Compare implementations
within each run; GitHub-hosted hardware and load can vary across runs. Matching
environment IDs or runner labels do not prove identical hardware. Use repeated
runs on a controlled machine to measure performance changes over time, and keep
CI results separate from named-machine results.

## Expected duration and resource use

Historical v2 local runs took about 33–35 minutes each; v5 runtime depends on
the environment. The runner prints stages and retains child logs. The benchmark
does not routinely profile RAM. Pandas holds one 10,000-row input frame plus
conversion and insertion buffers; this bounds input chunk size, not measured
peak process memory.

Machine, caches, fixed case order and fresh process startup affect results.
Compare current implementations within runs; repeated compatible measurements
help interpretation but do not establish universal performance.

## Contender lifecycle

The [documentation registry](contenders.md) records active, candidate and
eliminated contenders. csvsql is eliminated from all normal v5 smoke, local and
CI runs. Its pinned v4 implementation and historical evidence remain available
from the recorded Git revision. There is no runtime lifecycle framework.
