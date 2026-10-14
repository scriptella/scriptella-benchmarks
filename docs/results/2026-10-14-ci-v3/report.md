# Exploratory CSV import comparison

Environment: gha-ubuntu-24.04-x64 — GitHub-hosted Ubuntu 24.04 x86_64 runner; Docker Compose; JVM defaults

Fixture: 1000000 rows; SHA-256 bd323d366297efd6895e11c0feac212dadc3ea37c9db8ccadc3e96409cb393cc

| Metadata | Value |
|---|---|
| benchmark_version | v3 |
| environment_id | gha-ubuntu-24.04-x64 |
| architecture | amd64 |
| fixture_sha256 | bd323d366297efd6895e11c0feac212dadc3ea37c9db8ccadc3e96409cb393cc |
| rows | 1000000 |
| java_version | 17.0.17+10 |
| postgres_image | postgres:17.11-alpine3.24 |
| mariadb_image | mariadb:11.8.8 |
| postgres_jdbc_version | 42.7.13 |
| mariadb_jdbc_version | 3.5.7 |
| commons_csv_version | 1.14.1 |
| python_version | 3.12.3 |
| agate_version | 1.14.2 |
| agate_sql_version | 0.7.3 |
| csvkit_version | 2.2.0 |
| sqlalchemy_version | 2.0.46 |
| postgres_dbapi_driver | psycopg2 |
| postgres_dbapi_driver_version | 2.9.11 |
| mariadb_dbapi_driver | PyMySQL |
| mariadb_dbapi_driver_version | 1.1.2 |
| postgres_synchronous_commit | on |
| postgres_fsync | on |
| mariadb_innodb_flush_log_at_trx_commit | 1 |
| timing_boundary | fresh_process_start_to_exit |
| case_definition | phase3-v3-fixed-order-warmup20000-csvsql-defaults-chunk1000-copy |

| Database | Case | Scriptella release | Median duration (s) | Median rows/s | Samples |
|---|---|---|---:|---:|---:|
| postgres | scriptella unbatched | 1.6 | 57.127 | 17505 | 1 |
| postgres | scriptella batch_1000 | 1.6 | 12.358 | 80918 | 1 |
| postgres | jdbc unbatched | — | 52.051 | 19212 | 1 |
| postgres | jdbc batch_1000 | — | 9.235 | 108289 | 1 |
| postgres | csvsql chunk_1000 | — | 400.684 | 2496 | 1 |
| postgres | PostgreSQL native COPY reference native_reference | — | 1.329 | 752333 | 1 |
| mariadb | scriptella unbatched | 1.6 | 68.674 | 14562 | 1 |
| mariadb | scriptella batch_1000 | 1.6 | 7.404 | 135063 | 1 |
| mariadb | jdbc unbatched | — | 63.458 | 15758 | 1 |
| mariadb | jdbc batch_1000 | — | 4.717 | 212006 | 1 |
| mariadb | csvsql chunk_1000 | — | 389.348 | 2568 | 1 |

Ratios of median durations; above 1 means the first named implementation/mode is faster. Missing cases are omitted.

- postgres Scriptella 1.6 vs JDBC (unbatched): 0.911× (jdbc unbatched duration / scriptella unbatched duration)
- postgres Scriptella 1.6 vs csvsql (unbatched): 7.014× (csvsql chunk_1000 duration / scriptella unbatched duration)
- postgres COPY vs Scriptella 1.6 (unbatched): 42.978× (scriptella unbatched duration / copy native_reference duration)
- postgres Scriptella 1.6 vs JDBC (batch_1000): 0.747× (jdbc batch_1000 duration / scriptella batch_1000 duration)
- postgres Scriptella 1.6 vs csvsql (batch_1000): 32.423× (csvsql chunk_1000 duration / scriptella batch_1000 duration)
- postgres COPY vs Scriptella 1.6 (batch_1000): 9.297× (scriptella batch_1000 duration / copy native_reference duration)
- postgres Scriptella 1.6 batching speedup: 4.623× (scriptella unbatched duration / scriptella batch_1000 duration)
- postgres JDBC batching speedup: 5.637× (jdbc unbatched duration / jdbc batch_1000 duration)
- mariadb Scriptella 1.6 vs JDBC (unbatched): 0.924× (jdbc unbatched duration / scriptella unbatched duration)
- mariadb Scriptella 1.6 vs csvsql (unbatched): 5.670× (csvsql chunk_1000 duration / scriptella unbatched duration)
- mariadb Scriptella 1.6 vs JDBC (batch_1000): 0.637× (jdbc batch_1000 duration / scriptella batch_1000 duration)
- mariadb Scriptella 1.6 vs csvsql (batch_1000): 52.586× (csvsql chunk_1000 duration / scriptella batch_1000 duration)
- mariadb Scriptella 1.6 batching speedup: 9.275× (scriptella unbatched duration / scriptella batch_1000 duration)
- mariadb JDBC batching speedup: 13.454× (jdbc unbatched duration / jdbc batch_1000 duration)

| Run | Database | Case | Release | Duration (s) | Rows/s |
|---|---|---|---|---:|---:|
| run-20261014T011740Z-27d7fcb7-bf3d-40c5-91f8-2336cf5a9db5 | postgres | scriptella unbatched | 1.6 | 57.127 | 17505 |
| run-20261014T011740Z-27d7fcb7-bf3d-40c5-91f8-2336cf5a9db5 | postgres | scriptella batch_1000 | 1.6 | 12.358 | 80918 |
| run-20261014T011740Z-27d7fcb7-bf3d-40c5-91f8-2336cf5a9db5 | postgres | jdbc unbatched | — | 52.051 | 19212 |
| run-20261014T011740Z-27d7fcb7-bf3d-40c5-91f8-2336cf5a9db5 | postgres | jdbc batch_1000 | — | 9.235 | 108289 |
| run-20261014T011740Z-27d7fcb7-bf3d-40c5-91f8-2336cf5a9db5 | postgres | csvsql chunk_1000 | — | 400.684 | 2496 |
| run-20261014T011740Z-27d7fcb7-bf3d-40c5-91f8-2336cf5a9db5 | postgres | PostgreSQL native COPY reference native_reference | — | 1.329 | 752333 |
| run-20261014T011740Z-27d7fcb7-bf3d-40c5-91f8-2336cf5a9db5 | mariadb | scriptella unbatched | 1.6 | 68.674 | 14562 |
| run-20261014T011740Z-27d7fcb7-bf3d-40c5-91f8-2336cf5a9db5 | mariadb | scriptella batch_1000 | 1.6 | 7.404 | 135063 |
| run-20261014T011740Z-27d7fcb7-bf3d-40c5-91f8-2336cf5a9db5 | mariadb | jdbc unbatched | — | 63.458 | 15758 |
| run-20261014T011740Z-27d7fcb7-bf3d-40c5-91f8-2336cf5a9db5 | mariadb | jdbc batch_1000 | — | 4.717 | 212006 |
| run-20261014T011740Z-27d7fcb7-bf3d-40c5-91f8-2336cf5a9db5 | mariadb | csvsql chunk_1000 | — | 389.348 | 2568 |

Directional engineering experiment; machine, caches, fixed order and fresh process startup affect results. csvsql chunking differs from JDBC batching. PostgreSQL native COPY is a separate reference. Repeated runs do not make results universal.
