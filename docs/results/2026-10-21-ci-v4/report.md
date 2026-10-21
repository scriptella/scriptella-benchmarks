# Exploratory CSV import comparison

Environment: gha-ubuntu-24.04-x64 — GitHub-hosted Ubuntu 24.04 x86_64 runner; Docker Compose; JVM defaults

Fixture: 1000000 rows; SHA-256 bd323d366297efd6895e11c0feac212dadc3ea37c9db8ccadc3e96409cb393cc

| Metadata | Value |
|---|---|
| benchmark_version | v4 |
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
| case_definition | phase3-v4-pg-rewrite-true-fixed-order-warmup20000-csvsql-defaults-chunk1000-copy |

| Database | Case | Scriptella release | Median duration (s) | Median rows/s | Samples |
|---|---|---|---:|---:|---:|
| postgres | scriptella unbatched | 1.6 | 50.363 | 19856 | 1 |
| postgres | scriptella batch_1000 | 1.6 | 7.775 | 128625 | 1 |
| postgres | jdbc unbatched | — | 46.308 | 21594 | 1 |
| postgres | jdbc batch_1000 | — | 6.320 | 158219 | 1 |
| postgres | csvsql chunk_1000 | — | 378.555 | 2642 | 1 |
| postgres | PostgreSQL native COPY reference native_reference | — | 1.401 | 713569 | 1 |
| mariadb | scriptella unbatched | 1.6 | 57.374 | 17430 | 1 |
| mariadb | scriptella batch_1000 | 1.6 | 6.694 | 149381 | 1 |
| mariadb | jdbc unbatched | — | 54.498 | 18349 | 1 |
| mariadb | jdbc batch_1000 | — | 4.465 | 223979 | 1 |
| mariadb | csvsql chunk_1000 | — | 370.594 | 2698 | 1 |

Ratios of median durations; above 1 means the first named implementation/mode is faster. Missing cases are omitted.

- postgres Scriptella 1.6 vs JDBC (unbatched): 0.919× (jdbc unbatched duration / scriptella unbatched duration)
- postgres Scriptella 1.6 vs csvsql (unbatched): 7.517× (csvsql chunk_1000 duration / scriptella unbatched duration)
- postgres COPY vs Scriptella 1.6 (unbatched): 35.937× (scriptella unbatched duration / copy native_reference duration)
- postgres Scriptella 1.6 vs JDBC (batch_1000): 0.813× (jdbc batch_1000 duration / scriptella batch_1000 duration)
- postgres Scriptella 1.6 vs csvsql (batch_1000): 48.692× (csvsql chunk_1000 duration / scriptella batch_1000 duration)
- postgres COPY vs Scriptella 1.6 (batch_1000): 5.548× (scriptella batch_1000 duration / copy native_reference duration)
- postgres Scriptella 1.6 batching speedup: 6.478× (scriptella unbatched duration / scriptella batch_1000 duration)
- postgres JDBC batching speedup: 7.327× (jdbc unbatched duration / jdbc batch_1000 duration)
- mariadb Scriptella 1.6 vs JDBC (unbatched): 0.950× (jdbc unbatched duration / scriptella unbatched duration)
- mariadb Scriptella 1.6 vs csvsql (unbatched): 6.459× (csvsql chunk_1000 duration / scriptella unbatched duration)
- mariadb Scriptella 1.6 vs JDBC (batch_1000): 0.667× (jdbc batch_1000 duration / scriptella batch_1000 duration)
- mariadb Scriptella 1.6 vs csvsql (batch_1000): 55.360× (csvsql chunk_1000 duration / scriptella batch_1000 duration)
- mariadb Scriptella 1.6 batching speedup: 8.571× (scriptella unbatched duration / scriptella batch_1000 duration)
- mariadb JDBC batching speedup: 12.206× (jdbc unbatched duration / jdbc batch_1000 duration)

| Run | Database | Case | Release | Duration (s) | Rows/s |
|---|---|---|---|---:|---:|
| run-20261021T224726Z-ce601cbd-21f5-4da4-9c1b-74b4ad95c2b5 | postgres | scriptella unbatched | 1.6 | 50.363 | 19856 |
| run-20261021T224726Z-ce601cbd-21f5-4da4-9c1b-74b4ad95c2b5 | postgres | scriptella batch_1000 | 1.6 | 7.775 | 128625 |
| run-20261021T224726Z-ce601cbd-21f5-4da4-9c1b-74b4ad95c2b5 | postgres | jdbc unbatched | — | 46.308 | 21594 |
| run-20261021T224726Z-ce601cbd-21f5-4da4-9c1b-74b4ad95c2b5 | postgres | jdbc batch_1000 | — | 6.320 | 158219 |
| run-20261021T224726Z-ce601cbd-21f5-4da4-9c1b-74b4ad95c2b5 | postgres | csvsql chunk_1000 | — | 378.555 | 2642 |
| run-20261021T224726Z-ce601cbd-21f5-4da4-9c1b-74b4ad95c2b5 | postgres | PostgreSQL native COPY reference native_reference | — | 1.401 | 713569 |
| run-20261021T224726Z-ce601cbd-21f5-4da4-9c1b-74b4ad95c2b5 | mariadb | scriptella unbatched | 1.6 | 57.374 | 17430 |
| run-20261021T224726Z-ce601cbd-21f5-4da4-9c1b-74b4ad95c2b5 | mariadb | scriptella batch_1000 | 1.6 | 6.694 | 149381 |
| run-20261021T224726Z-ce601cbd-21f5-4da4-9c1b-74b4ad95c2b5 | mariadb | jdbc unbatched | — | 54.498 | 18349 |
| run-20261021T224726Z-ce601cbd-21f5-4da4-9c1b-74b4ad95c2b5 | mariadb | jdbc batch_1000 | — | 4.465 | 223979 |
| run-20261021T224726Z-ce601cbd-21f5-4da4-9c1b-74b4ad95c2b5 | mariadb | csvsql chunk_1000 | — | 370.594 | 2698 |

Directional engineering experiment; machine, caches, fixed order and fresh process startup affect results. csvsql chunking differs from JDBC batching. PostgreSQL native COPY is a separate reference. Repeated runs do not make results universal.
