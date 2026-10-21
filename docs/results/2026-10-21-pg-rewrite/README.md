# PostgreSQL JDBC batch rewrite follow-up

This separate follow-up uses Scriptella **1.6** (the Maven artifact version of
the release referred to as 1.6.0), pgJDBC **42.7.13**, PostgreSQL **17.11**, and
MariaDB **11.8.8** / Connector/J **3.5.7**. It preserves the original v3 results.
Compatibility ID: `v3-pg-rewrite-followup-1`; never aggregate with v3.

## Method

Three sequential rounds on the same Intel i9-9980HK machine; macOS host,
Colima VZ x86_64, 4 vCPU and 6 GiB configured RAM (Docker reports
6,211,026,944 bytes), Ubuntu 24.04.4 guest, Java 17.0.17+10.
Scriptella and JDBC both use batch size 1000. PostgreSQL explicitly sets
`reWriteBatchedInserts=false` or `true` in each measured child's JDBC URL.
MariaDB retains driver defaults and supplies contemporary same-machine controls.

Rounds 1 and 3: PostgreSQL Scriptella false, Scriptella true, JDBC false,
JDBC true, MariaDB Scriptella, MariaDB JDBC. Round 2 reverses that order.
This alternates both option and implementation order, although three rounds
cannot balance first position perfectly.

The unchanged v3 importer, deterministic million-row fixture, one transaction
and commit per import, table schema, reset, and independent verification are
reused. All six variants first pass a 2,001-row smoke check (partial batch).
Each measured import follows an untimed 20,000-row import, verification, and
reset. Timing covers a fresh client JVM from process start through successful
exit; preparation, reset, warmup and verification are excluded. JVM heap and
collector defaults are retained, timezone UTC. Each measured import passes
the existing separate-connection row count and sample checks, including exact
decimals and timestamps. This does not check every cell.

Fixture: 1,000,000 rows, 55,667,827 bytes, SHA-256
`bd323d366297efd6895e11c0feac212dadc3ea37c9db8ccadc3e96409cb393cc`.
Database durability defaults retained: PostgreSQL fsync/synchronous_commit on;
MariaDB innodb_flush_log_at_trx_commit=1.

## Reproduce

From the repository root:

```sh
BENCHMARK_REVISION="$(git rev-parse HEAD)-dirty" \
ENVIRONMENT_ID=my-machine-rewrite \
ENVIRONMENT_DESCRIPTION='Describe CPU and Docker allocation' \
SCRIPTELLA_VERSION=1.6 docker compose -f compose.yaml -f compose.pg-rewrite.yaml \
  up --build --force-recreate --abort-on-container-exit --exit-code-from benchmark
python3 tools/report-pg-rewrite.py results/pg-rewrite-followup-<run-id>
docker compose -f compose.yaml -f compose.pg-rewrite.yaml down --volumes
```

The follow-up runner reuses the current benchmark helpers by reflection;
the original measurement used the v3 implementation described below. The standard benchmark now uses v4 with rewriting enabled. The follow-up
sets the shared connection property to false or true for each child process. The original
measurement used a cached benchmark image for dependencies and compiled the
archived v3 source plus the new runner inside it before execution; preparation
validated actual dependency versions and checksums. Base source revision:
`10324639e42ebb5ad57d2a16f40d69d54e465ed4`.

## Interpretation limits

Only this CSV workload, eligible INSERT shape and batch size were measured.
End-to-end parsing, conversion, startup and driver work are included. Three
repetitions give directional evidence, not broad statistical guarantees.
MariaDB's bulk protocol and pgJDBC SQL rewriting are different mechanisms;
this experiment does not isolate an engine-only performance difference.
There is no new COPY or csvsql timing. Historical CI and local v2/v3 results
remain separate and are not used to calculate these comparisons.

## Results

![Median import times with PostgreSQL rewriting off and on, alongside unchanged MariaDB controls.](rewrite-comparison.svg)

Regenerate this chart from the recorded CSV with
`python3 tools/plot-overview.py docs/results/2026-10-21-pg-rewrite`
(requires matplotlib). The PNG preview is generated locally and Git-ignored.

All 18 measured imports and all smoke/warmup imports passed verification.
The table lists seconds; lower is faster. Medians are calculated independently
for each configuration, from three repetitions.

| Database | Implementation | Rewrite | Round 1 (s) | Round 2 (s) | Round 3 (s) | Median (s) |
|---|---|---|---:|---:|---:|---:|
| postgres | scriptella | false | 17.214 | 15.298 | 15.995 | 15.995 |
| postgres | scriptella | true | 9.409 | 9.262 | 9.482 | 9.409 |
| postgres | jdbc | false | 12.845 | 11.333 | 10.952 | 11.333 |
| postgres | jdbc | true | 7.290 | 7.348 | 7.165 | 7.290 |
| mariadb | scriptella | default | 8.852 | 8.688 | 8.602 | 8.688 |
| mariadb | jdbc | default | 5.571 | 5.694 | 5.526 | 5.571 |

scriptella: rewriting speedup 1.700x; elapsed reduction 41.2%; PG/MariaDB duration ratio 1.841x -> 1.083x; PG minus MariaDB 7.307s -> 0.722s.
jdbc: rewriting speedup 1.555x; elapsed reduction 35.7%; PG/MariaDB duration ratio 2.034x -> 1.309x; PG minus MariaDB 5.762s -> 1.719s.

Scriptella and handwritten JDBC both benefit. The proportional improvement is
larger for Scriptella here, but their different parsers and conversion paths
prevent attributing this to a single internal cause. PostgreSQL remains slower
than contemporary MariaDB controls in both paths; the gap narrows considerably.
Scriptella/JDBC median duration ratio is 1.411x without rewriting and 1.291x
with rewriting. There is no claim that driver rewriting eliminates all costs.

The original v3 measurements remain untouched. Raw configuration-specific
measurements, successful execution metadata, and individual subprocess logs
are in [pg-rewrite-followup-20261021T213518Z-7a4d33b4-c4b5-4e0b-bec8-7c1266942366](pg-rewrite-followup-20261021T213518Z-7a4d33b4-c4b5-4e0b-bec8-7c1266942366). Regenerate the table with
`python3 tools/report-pg-rewrite.py docs/results/2026-10-21-pg-rewrite/pg-rewrite-followup-20261021T213518Z-7a4d33b4-c4b5-4e0b-bec8-7c1266942366`.

## Decision: adopt rewriting in benchmark v4

Based on this follow-up, the standard benchmark now explicitly enables
`reWriteBatchedInserts=true` equally for Scriptella and handwritten JDBC.
The compatibility ID is `v4`, with a new case definition. Historical v3
measurements and this separate false/true dataset remain unchanged; neither
is relabeled as a full v4 run. A full v4 CI comparison is still pending.
The `compose.pg-rewrite.yaml` override selects the false/true experiment for
local reproduction. No fixture, batch size, transaction or verification
behavior changes. The general Scriptella adapter default remains unchanged;
the option is documented in the driver guide and website FAQ.

This experiment does not justify changing Scriptella's general PostgreSQL
adapter default: generated keys, per-statement batch counts, other SQL shapes,
triggers and application-specific error handling were not exercised.

Preparation note: an initial scratch-directory bind mount was not visible to
Colima, so compilation failed before any preparation or measurements. Moving
the same sources to a shared workspace path resolved it. All reported data
comes from the subsequent successful run. Normal database checkpoints and
cache evolution were retained; they can affect timing, including the downward
trend in the non-rewritten JDBC observations.

## Reproduction metadata corrections

Future follow-up runs set `phase=pg-rewrite-followup-measurement` and record
`source_revision` from the image's `BENCHMARK_REVISION`, instead of claiming
the original v3 source revision. The command above conservatively labels the
checkout dirty; for an unchanged committed checkout, use its exact Git SHA.
If no revision is supplied, traceability is explicitly `n/a`. These are
build-time values: rebuild the image when the sources or revision change.
The runner sets `POSTGRES_REWRITE_BATCHED_INSERTS` for each child. Scriptella
places `reWriteBatchedInserts` beside `statement.batchSize`; handwritten JDBC
passes the same value through `Properties`. URLs carry no rewrite option.
A rewrite option in the URL is rejected to prevent overriding the property.

The historical dataset remains unchanged, including its original fixed
`baseline_source_revision` and inherited `phase=preparation-only`. That phase
was a metadata labeling error: its 18 independently verified measurements
are the measured follow-up, as recorded by the CSV and successful status.
The archive's source revision identifies the actual original v3 inputs;
new reproductions execute the current sources and report their own revision.
