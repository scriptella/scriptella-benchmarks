# GitHub Actions CSV import results — benchmark v4, 2026-10-21 UTC

One complete [manual workflow run](https://github.com/scriptella/scriptella-benchmarks/actions/runs/39688830534)
imported the million-row fixture through all eleven cases using Scriptella 1.6.
Every import passed independent row-count and sample-value verification,
including representative fractional amounts, before its timing was recorded.
Benchmark v4 explicitly enables pgJDBC `reWriteBatchedInserts=true` for
Scriptella and handwritten JDBC. MariaDB retains its driver defaults.
Scriptella's general adapter default is unchanged.

| Database | Scriptella batch 1000 | JDBC batch 1000 | csvsql chunk 1000 | Native COPY reference |
|---|---:|---:|---:|---:|
| PostgreSQL | 7.775 s | 6.320 s | 378.555 s | 1.401 s |
| MariaDB | 6.694 s | 4.465 s | 370.594 s | N/A |

![End-to-end import times for one million rows on PostgreSQL and MariaDB, from one v4 CI run. Logarithmic duration scale; lower is faster.](overview.svg)

Scriptella batching provided a 6.48× speedup over its unbatched mode on
PostgreSQL and an 8.57× speedup on MariaDB. Handwritten JDBC batching was faster
than Scriptella. Scriptella's batched durations were 48.7× and 55.4× shorter
than csvsql's tested chunk-1000 workflow, respectively. Chunking and JDBC
batching use different mechanisms. Native COPY is a separate PostgreSQL reference.

## Environment and evidence

- GitHub-hosted `ubuntu-24.04` x86_64 runner, Docker Compose and JVM defaults.
- Environment ID: `gha-ubuntu-24.04-x64`; benchmark version: `v4`.
- Scriptella 1.6; Java 17.0.17+10; Python 3.12.3; csvkit 2.2.0.
- PostgreSQL 17.11; pgJDBC 42.7.13 with batch rewriting enabled.
- MariaDB 11.8.8; MariaDB Connector/J 3.5.7 with driver defaults.
- Normal database durability: PostgreSQL `fsync` and `synchronous_commit` on;
  MariaDB `innodb_flush_log_at_trx_commit=1`.
- Recorded benchmark revision: `0447118c454d3731ccd8fe40a496f38a0aff3767`.
- RAM was not measured.

The [full report](report.md) includes all eleven durations, throughputs,
ratios and compatibility metadata. The run directory contains unchanged
`measurements.csv` and `preparation.properties` selected from artifact
`benchmark-39688830534-1` (artifact ID `12388053600`). Build and process logs
remain in the workflow artifact.

These durations are **single observations from one complete run**. The
report's median columns equal those observations. Compare implementations
within this run, where they share a runner. GitHub-hosted hardware and load can
vary between executions; matching runner labels do not establish identical
hardware. Comparisons across CI runs do not establish performance improvements
or regressions. Use repeated runs on a controlled machine to measure changes
over time. This run does not establish a performance improvement over v3.

The [historical v3 CI results](../2026-10-14-ci-v3/README.md),
[v2 CI results](../2026-10-13-ci/README.md) and
[local v2 results](../2026-10-12-local/README.md) remain available separately.
The [repeated local rewrite follow-up](../2026-10-21-pg-rewrite/README.md)
compares rewriting off and on with a separate compatibility identifier.
Results from different benchmark versions and environments are not pooled.

## Regenerate the report and charts

From the repository root:

```sh
docker compose run --build --no-deps --rm \
  -v "$PWD/docs/results/2026-10-21-ci-v4:/results" benchmark aggregate /results
```

To regenerate the charts from the raw measurements, use Python with
matplotlib installed, from the repository root:

```sh
python3 tools/plot-overview.py docs/results/2026-10-21-ci-v4
python3 tools/plot-results.py docs/results/2026-10-21-ci-v4
```
