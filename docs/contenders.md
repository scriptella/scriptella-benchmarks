# Contender registry

**Active** means included in standard runs for useful ongoing comparison.
**Candidate** means evaluated in a spike before a retention decision.
**Eliminated** means excluded from routine smoke, local and CI execution.
This is a documentation registry, not runtime configuration.

| Contender | Status | Coverage |
|---|---|---|
| Scriptella batch 1000 | Active | PostgreSQL, MariaDB |
| Handwritten JDBC batch 1000 | Active | PostgreSQL, MariaDB |
| Pandas + SQLAlchemy | Active | PostgreSQL, MariaDB; retained after the v5 candidate spike |
| PostgreSQL COPY | Active native reference | PostgreSQL only; not a portable ETL competitor |
| MariaDB LOAD DATA LOCAL INFILE | Active native reference | MariaDB only; not a portable ETL competitor |
| Scriptella / JDBC unbatched | Retired | Last published v4; early v5 spike preserved separately |
| csvsql chunk 1000 | Eliminated | Last standard version: v4 |

## csvsql: eliminated for v5

The csvkit 2.2.0 direct importer used inferred types, SQLAlchemy 2.0.46,
psycopg2-binary 2.9.11 and PyMySQL 1.1.2. The workload was the shared deterministic
million-row fixture and pre-created schema on PostgreSQL 17 and MariaDB 11.

| Database | Scriptella batch 1000 | csvsql chunk 1000 | csvsql / Scriptella |
|---|---:|---:|---:|
| PostgreSQL | 7.775 s | 378.555 s | 48.7x |
| MariaDB | 6.694 s | 370.594 s | 55.4x |

[Last v4 CI evidence](results/2026-10-21-ci-v4/README.md) records the environment,
raw measurements and full dependencies. At approximately **50× slower** (48.7× PostgreSQL, 55.4× MariaDB)
than Scriptella batching, this workflow costs substantial routine execution
time without enough ongoing analytical value. This conclusion applies to the
documented workflow and settings, not every possible csvsql configuration.

Invocation:

```sh
csvsql --db '<SQLAlchemy URL>' --tables csv_import --no-create --insert \
  --chunk-size 1000 <fixture>
```

For reproduction, check out benchmark revision
`eecd095b71b251e3e9e280fd45eb9da8fabf7b34` in a separate worktree and use its
Dockerfile, complete requirements.txt, Compose configuration and benchmark guide.
That revision retains the small runner-based implementation; v5 carries no
csvkit/agate runtime dependencies. Historical v4 data and docs are unchanged.
To reinstate, restore that revision's launch branch, dependency pins, case and
metadata definitions into a new benchmark version, validate both databases,
and document why it provides a useful comparison. Do not relabel v4 results.

## Unbatched Scriptella/JDBC: retired from final v5

Batching is the established baseline for this million-row workload. Unbatched
imports are substantially slower and add little to the continuing comparison.
No default or optional local/CI execution includes them; `run-all` and
`smoke-all` no longer exist, and the Java importer rejects batch size zero for
Scriptella/JDBC. Batch size zero is reserved for native reference dispatch.

Last published evidence is [v4 CI](results/2026-10-21-ci-v4/README.md).
The early eleven-case v5 spike (including its interrupted second run) is retained
privately with exact source patches and raw measurements. It is incompatible
with final eight-case v5. Last published durations (seconds; contextual, not pooled with final v5):

| Database | Scriptella unbatched / batch 1000 | JDBC unbatched / batch 1000 |
|---|---:|---:|
| PostgreSQL | 50.363 / 7.775 | 46.308 / 6.320 |
| MariaDB | 57.374 / 6.694 | 54.498 / 4.465 |

The public v4 revision
`eecd095b71b251e3e9e280fd45eb9da8fabf7b34` retains the old implementations,
requirements and invocation. To reproduce them, use that revision in a separate
worktree and follow its benchmark guide. Scriptella 1.6, pgJDBC 42.7.13,
MariaDB Connector/J 3.5.7 and Commons CSV 1.14.1 were used. To reinstate them,
restore the old case definitions/importer paths into a new compatibility
identifier and validate them; there is no runtime toggle in final v5.

## Pandas: retained after the v5 spike

The ordinary workflow is slower than Scriptella batching in the local spike,
but remains useful as a familiar, portable DataFrame-based reference. Its small
implementation, explicit conversions and chunked input make it inexpensive to
maintain. Retention is an analytical-value decision, not a claim of equivalent
performance. The [curated final v5 results](results/2026-10-26-local-v5/README.md) record the
comparison; earlier spike investigations remain private. The published article
is unchanged.
