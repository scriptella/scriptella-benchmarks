# Scriptella 1.6 and 1.7.0

Four complete v5 runs compare the published Scriptella 1.6 and 1.7.0 artifacts
on the local reference VM. The order was 1.6, 1.7.0, 1.7.0, 1.6, giving two
observations per version. Each run completed all eight one-million-row cases
and passed independent row-count and sample-value verification.

The quoted eight-case v5 table uses the two Scriptella 1.7.0 runs. Those runs
and their chart are in [2026-11-03-local-v5](../2026-11-03-local-v5/README.md).

## Scriptella results

Durations are seconds; medians are over two runs. Lower is faster.

| Database | 1.6 median (runs) | 1.7.0 median (runs) | Change in duration |
|---|---:|---:|---:|
| PostgreSQL | 14.604 (13.603, 15.605) | 8.747 (8.893, 8.601) | -40.1% |
| MariaDB | 11.217 (12.567, 9.867) | 7.950 (8.200, 7.700) | -29.1% |

Scriptella 1.7.0 was faster on both databases, and the two versions did not
overlap. JDBC, Pandas, COPY and LOAD DATA from the same four runs stayed in a
narrower band, so the Scriptella gap is not a change in the database servers.
These are two observations per release on one machine. The workload measures
end-to-end CSV import. It includes the cached static SQL binding-plan path and
does not exercise the JSON driver.

## Method and environment

- Intel Mac with Colima x86_64, 4 CPUs and 6 GiB of memory. This is the local
  reference setup for quoted comparisons.
- Benchmark v5, case definition
  `v5-final-eight-pg-rewrite-true-warmup20000-pandas-read10000-write1000-local-infile-v1`.
- Environment ID: `intel-colima-release-4cpu-6gib`.
- Run order: 1.6, 1.7.0, 1.7.0, 1.6. Scriptella version changed between runs;
  database images, fixture, benchmark cases and other dependencies stayed fixed.
- Each run included the normal verified 20,000-row warmup before each measured
  import. Timing covers fresh process startup through successful exit; preparation,
  warmup, reset and independent verification are excluded.
- PostgreSQL 17.11, MariaDB 11.8.8, Java 17.0.17+10, pgJDBC 42.7.13,
  MariaDB Connector/J 3.5.7, Commons CSV 1.14.1, Pandas 2.3.3 and SQLAlchemy
  2.0.46.
- Fixture SHA-256:
  `bd323d366297efd6895e11c0feac212dadc3ea37c9db8ccadc3e96409cb393cc`.
- Benchmark source was commit `80d95ef` with the local `.dockerignore` fix that
  allows the Dockerfile's `tools` directory into the build context. The recorded
  benchmark revision is `80d95ef-plus-dockerignore`.

## Raw evidence

Each run directory contains its eight-case `measurements.csv` and
`preparation.properties`, including the run timestamp, version, dependency
checksums, database settings and environment identity.

- 1.6, run 1: [measurements](run-20261103T194856Z-07f9722a-48a4-44a1-a5bf-59e7578a65e0/measurements.csv), [preparation](run-20261103T194856Z-07f9722a-48a4-44a1-a5bf-59e7578a65e0/preparation.properties)
- 1.7.0, run 1: [measurements](run-20261103T195118Z-2b4c33da-d7ff-4529-a543-ced722a062e2/measurements.csv), [preparation](run-20261103T195118Z-2b4c33da-d7ff-4529-a543-ced722a062e2/preparation.properties)
- 1.7.0, run 2: [measurements](run-20261103T195311Z-0306c6ec-8f2e-4e61-8dbe-917eeaa1ba71/measurements.csv), [preparation](run-20261103T195311Z-0306c6ec-8f2e-4e61-8dbe-917eeaa1ba71/preparation.properties)
- 1.6, run 2: [measurements](run-20261103T195507Z-f2b3c94c-a514-44ea-a482-0e10d901721a/measurements.csv), [preparation](run-20261103T195507Z-f2b3c94c-a514-44ea-a482-0e10d901721a/preparation.properties)

File checksums are recorded in [SHA256SUMS](SHA256SUMS).
