# GitHub Actions CSV import results — benchmark v3, 2026-10-14 UTC

One complete [manual workflow run](https://github.com/scriptella/scriptella-benchmarks/actions/runs/38494804326)
imported the million-row fixture through all eleven cases using Scriptella 1.6.
Every import passed independent row-count and sample-value verification,
including representative fractional amounts, before its timing was recorded.
The v3 Scriptella configuration uses the CSV driver's built-in number and
timestamp formatters; see the [ETL specification](../../scriptella-etl.md).

| Database | Scriptella batch 1000 | JDBC batch 1000 | csvsql chunk 1000 | Scriptella vs csvsql |
|---|---:|---:|---:|---:|
| PostgreSQL | 12.358 s | 9.235 s | 400.684 s | 32.4× |
| MariaDB | 7.404 s | 4.717 s | 389.348 s | 52.6× |

Scriptella batching provided a 4.62× speedup over its unbatched mode on
PostgreSQL and a 9.28× speedup on MariaDB. Handwritten JDBC batching was faster than
Scriptella. csvsql uses its documented chunk-1000 import path; chunking and
JDBC batching use different mechanisms. PostgreSQL native COPY took 1.329 s
as a separate native reference.

## Environment and evidence

- GitHub-hosted `ubuntu-24.04` x86_64 runner, Docker Compose and JVM defaults.
- Environment ID: `gha-ubuntu-24.04-x64`; benchmark version: `v3`.
- Scriptella 1.6; Java 17.0.17+10; Python 3.12.3.
- PostgreSQL 17.11 and MariaDB 11.8.8, with normal durability and driver defaults.
- Recorded benchmark revision: `f0fdb0b520cf4e72b4f37122fea2b50e5bf34b19`.
- RAM was not measured.

The [full report](report.md) includes all eleven durations, throughputs,
ratios and compatibility metadata. The run directory contains unchanged
`measurements.csv` and `preparation.properties` selected from artifact
`benchmark-38494804326-1` (artifact ID `11864601005`). Build and process logs
remain in the workflow artifact.

These durations are **single observations from one complete run**. The
report's median columns equal those observations. Compare implementations
within this run, where they share a runner. GitHub-hosted hardware and load can
vary between executions; matching runner labels do not establish identical
hardware. Comparisons across CI runs do not establish performance improvements
or regressions. Use repeated runs on a controlled machine to measure changes
over time. This run does not establish a performance improvement over v2.

The [historical v2 CI results](../2026-10-13-ci/README.md) and
[local v2 results](../2026-10-12-local/README.md) remain available separately.
Results from different benchmark versions and environments are not pooled.

## Regenerate the report

From the repository root:

```sh
docker compose run --build --no-deps --rm \
  -v "$PWD/docs/results/2026-10-14-ci-v3:/results" benchmark aggregate /results
```
