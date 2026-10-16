# Scriptella benchmarks

[Scriptella](https://github.com/scriptella/scriptella-etl) is a Java ETL tool that describes data
flows in XML and SQL. This experiment compares its end-to-end CSV import times
with handwritten JDBC, csvsql, and PostgreSQL's native COPY.

The reproducible eleven-case comparison imports one million rows into
PostgreSQL and MariaDB. This repository includes the implementations, raw
measurements, independent import verification, and Docker Compose setup.

## Why this comparison?

The goal is to establish a reproducible performance baseline for Scriptella
and guide future improvements, starting with a simple CSV import workload.

- **Scriptella:** declarative CSV parsing and SQL inserts, with and without batching.
- **Handwritten JDBC:** a Java baseline using the same database drivers,
  with Apache Commons CSV for parsing and explicit typed bindings.
- **csvsql:** the documented CSV-to-SQL command-line workflow from Python's csvkit.
- **PostgreSQL COPY:** a native bulk-loading reference for direct CSV imports.

These are specific workflows and settings, not a ranking of CSV loaders.
No transformations or MariaDB native loader are tested.

## Results

The current **v3** results come from **one complete GitHub Actions run**,
importing one million rows with Scriptella 1.6 on Ubuntu 24.04:

| Database | Scriptella batch 1000 | JDBC batch 1000 | csvsql chunk 1000 | Native COPY reference |
|---|---:|---:|---:|---:|
| PostgreSQL | 12.358 s | 9.235 s | 400.684 s | 1.329 s |
| MariaDB | 7.404 s | 4.717 s | 389.348 s | N/A |

![End-to-end import times for one million rows on PostgreSQL and MariaDB, from one v3 CI run. Logarithmic duration scale; lower is faster.](docs/results/2026-10-14-ci-v3/overview.svg)

Handwritten JDBC batching was faster than Scriptella; both took less time than
csvsql's chunk-1000 import path. Native COPY was fastest on PostgreSQL.
Chunking and JDBC batching use different mechanisms.

The [full v3 report and evidence](docs/results/2026-10-14-ci-v3/README.md)
include all eleven cases, unbatched results, and dependency metadata.
Each import passed independent row-count and sample-value verification before
its timing was recorded. CI did not measure RAM.

## Reproduce

You need Git, a Docker-compatible runtime, and Docker Compose. The container
build supplies Java, Maven, Python/csvkit, Scriptella, and JDBC dependencies;
a Scriptella source checkout or distribution ZIP is not required.

Clone this repository and enter it:

```sh
git clone https://github.com/scriptella/scriptella-benchmarks.git
cd scriptella-benchmarks
```

First, smoke-test all eleven imports with 2,001-row prefixes, including a final
partial batch. This checks correctness without recording benchmark timings.
The first invocation also downloads images and builds the benchmark image.

```sh
BENCHMARK_COMMAND=smoke SCRIPTELLA_VERSION=1.6 docker compose up --build \
  --abort-on-container-exit --exit-code-from benchmark
```

Run the full million-row comparison with a description of your environment:

```sh
ENVIRONMENT_ID=my-machine-docker \
ENVIRONMENT_DESCRIPTION='Describe CPU, architecture and Docker runtime' \
SCRIPTELLA_VERSION=1.6 docker compose up --build --force-recreate \
  --abort-on-container-exit --exit-code-from benchmark
```

The historical local runs took about 33–35 minutes each; runtime varies by
machine and Docker allocation. Results and diagnostics are saved under
`results/run-<timestamp>-<id>/`. The command exits nonzero on failure and retains
completed valid measurements and diagnostics.

See the [benchmark guide](docs/benchmark-guide.md) for output files, cleanup,
configuration, aggregation, and the complete measurement method.

## Limitations

These are end-to-end workflow timings, including process startup, CSV parsing,
type conversion, database writes, and commit. Different parsers, conversion
paths, and startup work mean they do not isolate Scriptella's abstraction
overhead. This is a single workload and a single v3 CI run; hardware, caches,
driver defaults, and fixed case order affect results. Compare implementations
within a run. Use repeated runs on a controlled machine to measure changes
over time, and keep different benchmark versions and environments separate.

## Further reading

- [Benchmark guide](docs/benchmark-guide.md): fixture, schema, timing boundaries,
  driver settings, verification, compatibility rules, and operational details.
- [Scriptella ETL example and full benchmark configuration](docs/scriptella-etl.md).
- [Historical v2 local results](docs/results/2026-10-12-local/README.md):
  two runs, charts, and coarse process-memory observations.
- [Historical v2 CI results](docs/results/2026-10-13-ci/README.md):
  one complete run with measurements and dependency metadata.
- [Importing a million CSV rows: Scriptella, JDBC, csvsql, and PostgreSQL COPY](https://pvrlabs.xyz/articles/importing-a-million-csv-rows.html):
  the PVR Labs article walking through the historical v2 results.
