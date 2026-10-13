# Exploratory CSV import comparison

Environment: gha-ubuntu-24.04-x64 — GitHub-hosted Ubuntu 24.04 x86_64 runner; Docker Compose; JVM defaults

Fixture: 1000000 rows; SHA-256 bd323d366297efd6895e11c0feac212dadc3ea37c9db8ccadc3e96409cb393cc

| Metadata | Value |
|---|---|
| benchmark_version | v2 |
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
| case_definition | phase3-v2-fixed-order-warmup20000-csvsql-defaults-chunk1000-copy |

| Database | Case | Scriptella release | Median duration (s) | Median rows/s | Samples |
|---|---|---|---:|---:|---:|
| postgres | scriptella unbatched | 1.6 | 36.260 | 27578 | 1 |
| postgres | scriptella batch_1000 | 1.6 | 6.858 | 145820 | 1 |
| postgres | jdbc unbatched | — | 35.469 | 28193 | 1 |
| postgres | jdbc batch_1000 | — | 5.854 | 170813 | 1 |
| postgres | csvsql chunk_1000 | — | 221.805 | 4508 | 1 |
| postgres | PostgreSQL native COPY reference native_reference | — | 0.968 | 1032664 | 1 |
| mariadb | scriptella unbatched | 1.6 | 40.722 | 24557 | 1 |
| mariadb | scriptella batch_1000 | 1.6 | 4.446 | 224932 | 1 |
| mariadb | jdbc unbatched | — | 38.912 | 25699 | 1 |
| mariadb | jdbc batch_1000 | — | 2.998 | 333503 | 1 |
| mariadb | csvsql chunk_1000 | — | 214.924 | 4653 | 1 |

Ratios of median durations; above 1 means the first named implementation/mode is faster. Missing cases are omitted.

- postgres Scriptella 1.6 vs JDBC (unbatched): 0.978× (jdbc unbatched duration / scriptella unbatched duration)
- postgres Scriptella 1.6 vs csvsql (unbatched): 6.117× (csvsql chunk_1000 duration / scriptella unbatched duration)
- postgres COPY vs Scriptella 1.6 (unbatched): 37.445× (scriptella unbatched duration / copy native_reference duration)
- postgres Scriptella 1.6 vs JDBC (batch_1000): 0.854× (jdbc batch_1000 duration / scriptella batch_1000 duration)
- postgres Scriptella 1.6 vs csvsql (batch_1000): 32.344× (csvsql chunk_1000 duration / scriptella batch_1000 duration)
- postgres COPY vs Scriptella 1.6 (batch_1000): 7.082× (scriptella batch_1000 duration / copy native_reference duration)
- postgres Scriptella 1.6 batching speedup: 5.287× (scriptella unbatched duration / scriptella batch_1000 duration)
- postgres JDBC batching speedup: 6.059× (jdbc unbatched duration / jdbc batch_1000 duration)
- mariadb Scriptella 1.6 vs JDBC (unbatched): 0.956× (jdbc unbatched duration / scriptella unbatched duration)
- mariadb Scriptella 1.6 vs csvsql (unbatched): 5.278× (csvsql chunk_1000 duration / scriptella unbatched duration)
- mariadb Scriptella 1.6 vs JDBC (batch_1000): 0.674× (jdbc batch_1000 duration / scriptella batch_1000 duration)
- mariadb Scriptella 1.6 vs csvsql (batch_1000): 48.343× (csvsql chunk_1000 duration / scriptella batch_1000 duration)
- mariadb Scriptella 1.6 batching speedup: 9.160× (scriptella unbatched duration / scriptella batch_1000 duration)
- mariadb JDBC batching speedup: 12.977× (jdbc unbatched duration / jdbc batch_1000 duration)

| Run | Database | Case | Release | Duration (s) | Rows/s |
|---|---|---|---|---:|---:|
| run-20261013T010808Z-73840b57-aab0-458b-b510-e36f6fdaf6cc | postgres | scriptella unbatched | 1.6 | 36.260 | 27578 |
| run-20261013T010808Z-73840b57-aab0-458b-b510-e36f6fdaf6cc | postgres | scriptella batch_1000 | 1.6 | 6.858 | 145820 |
| run-20261013T010808Z-73840b57-aab0-458b-b510-e36f6fdaf6cc | postgres | jdbc unbatched | — | 35.469 | 28193 |
| run-20261013T010808Z-73840b57-aab0-458b-b510-e36f6fdaf6cc | postgres | jdbc batch_1000 | — | 5.854 | 170813 |
| run-20261013T010808Z-73840b57-aab0-458b-b510-e36f6fdaf6cc | postgres | csvsql chunk_1000 | — | 221.805 | 4508 |
| run-20261013T010808Z-73840b57-aab0-458b-b510-e36f6fdaf6cc | postgres | PostgreSQL native COPY reference native_reference | — | 0.968 | 1032664 |
| run-20261013T010808Z-73840b57-aab0-458b-b510-e36f6fdaf6cc | mariadb | scriptella unbatched | 1.6 | 40.722 | 24557 |
| run-20261013T010808Z-73840b57-aab0-458b-b510-e36f6fdaf6cc | mariadb | scriptella batch_1000 | 1.6 | 4.446 | 224932 |
| run-20261013T010808Z-73840b57-aab0-458b-b510-e36f6fdaf6cc | mariadb | jdbc unbatched | — | 38.912 | 25699 |
| run-20261013T010808Z-73840b57-aab0-458b-b510-e36f6fdaf6cc | mariadb | jdbc batch_1000 | — | 2.998 | 333503 |
| run-20261013T010808Z-73840b57-aab0-458b-b510-e36f6fdaf6cc | mariadb | csvsql chunk_1000 | — | 214.924 | 4653 |

Directional engineering experiment; machine, caches, fixed order and fresh process startup affect results. csvsql chunking differs from JDBC batching. PostgreSQL native COPY is a separate reference. Repeated runs do not make results universal.
