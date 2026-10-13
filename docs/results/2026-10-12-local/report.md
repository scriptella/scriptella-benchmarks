# Exploratory CSV import comparison

Environment: intel-i9-colima-x64 — Intel Core i9-9980HK 2.40GHz, macOS 32GiB; Colima x86_64 Virtualization.Framework 4 CPUs 6GiB RAM

Fixture: 1000000 rows; SHA-256 bd323d366297efd6895e11c0feac212dadc3ea37c9db8ccadc3e96409cb393cc

| Metadata | Value |
|---|---|
| benchmark_version | v2 |
| environment_id | intel-i9-colima-x64 |
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
| postgres | scriptella unbatched | 1.6 | 177.669 | 5629 | 2 |
| postgres | scriptella batch_1000 | 1.6 | 14.792 | 68230 | 2 |
| postgres | jdbc unbatched | — | 176.834 | 5655 | 2 |
| postgres | jdbc batch_1000 | — | 13.631 | 73410 | 2 |
| postgres | csvsql chunk_1000 | — | 557.945 | 1797 | 2 |
| postgres | PostgreSQL native COPY reference native_reference | — | 1.672 | 605109 | 2 |
| mariadb | scriptella unbatched | 1.6 | 220.081 | 4553 | 2 |
| mariadb | scriptella batch_1000 | 1.6 | 9.523 | 105910 | 2 |
| mariadb | jdbc unbatched | — | 205.926 | 4864 | 2 |
| mariadb | jdbc batch_1000 | — | 6.649 | 150493 | 2 |
| mariadb | csvsql chunk_1000 | — | 597.053 | 1675 | 2 |

Ratios of median durations; above 1 means the first named implementation/mode is faster. Missing cases are omitted.

- postgres Scriptella 1.6 vs JDBC (unbatched): 0.995× (jdbc unbatched duration / scriptella unbatched duration)
- postgres Scriptella 1.6 vs csvsql (unbatched): 3.140× (csvsql chunk_1000 duration / scriptella unbatched duration)
- postgres COPY vs Scriptella 1.6 (unbatched): 106.232× (scriptella unbatched duration / copy native_reference duration)
- postgres Scriptella 1.6 vs JDBC (batch_1000): 0.922× (jdbc batch_1000 duration / scriptella batch_1000 duration)
- postgres Scriptella 1.6 vs csvsql (batch_1000): 37.720× (csvsql chunk_1000 duration / scriptella batch_1000 duration)
- postgres COPY vs Scriptella 1.6 (batch_1000): 8.844× (scriptella batch_1000 duration / copy native_reference duration)
- postgres Scriptella 1.6 batching speedup: 12.011× (scriptella unbatched duration / scriptella batch_1000 duration)
- postgres JDBC batching speedup: 12.973× (jdbc unbatched duration / jdbc batch_1000 duration)
- mariadb Scriptella 1.6 vs JDBC (unbatched): 0.936× (jdbc unbatched duration / scriptella unbatched duration)
- mariadb Scriptella 1.6 vs csvsql (unbatched): 2.713× (csvsql chunk_1000 duration / scriptella unbatched duration)
- mariadb Scriptella 1.6 vs JDBC (batch_1000): 0.698× (jdbc batch_1000 duration / scriptella batch_1000 duration)
- mariadb Scriptella 1.6 vs csvsql (batch_1000): 62.695× (csvsql chunk_1000 duration / scriptella batch_1000 duration)
- mariadb Scriptella 1.6 batching speedup: 23.110× (scriptella unbatched duration / scriptella batch_1000 duration)
- mariadb JDBC batching speedup: 30.970× (jdbc unbatched duration / jdbc batch_1000 duration)

| Run | Database | Case | Release | Duration (s) | Rows/s |
|---|---|---|---|---:|---:|
| run-20261012T213750Z-1051edb2-e886-4ba4-8ad2-f254596d00a3 | postgres | scriptella unbatched | 1.6 | 175.375 | 5702 |
| run-20261012T234136Z-613ba497-844b-49bf-b443-7ff07db8efa9 | postgres | scriptella unbatched | 1.6 | 179.964 | 5557 |
| run-20261012T213750Z-1051edb2-e886-4ba4-8ad2-f254596d00a3 | postgres | scriptella batch_1000 | 1.6 | 13.376 | 74763 |
| run-20261012T234136Z-613ba497-844b-49bf-b443-7ff07db8efa9 | postgres | scriptella batch_1000 | 1.6 | 16.208 | 61697 |
| run-20261012T213750Z-1051edb2-e886-4ba4-8ad2-f254596d00a3 | postgres | jdbc unbatched | — | 176.872 | 5654 |
| run-20261012T234136Z-613ba497-844b-49bf-b443-7ff07db8efa9 | postgres | jdbc unbatched | — | 176.796 | 5656 |
| run-20261012T213750Z-1051edb2-e886-4ba4-8ad2-f254596d00a3 | postgres | jdbc batch_1000 | — | 13.286 | 75269 |
| run-20261012T234136Z-613ba497-844b-49bf-b443-7ff07db8efa9 | postgres | jdbc batch_1000 | — | 13.976 | 71550 |
| run-20261012T213750Z-1051edb2-e886-4ba4-8ad2-f254596d00a3 | postgres | csvsql chunk_1000 | — | 530.346 | 1886 |
| run-20261012T234136Z-613ba497-844b-49bf-b443-7ff07db8efa9 | postgres | csvsql chunk_1000 | — | 585.544 | 1708 |
| run-20261012T213750Z-1051edb2-e886-4ba4-8ad2-f254596d00a3 | postgres | PostgreSQL native COPY reference native_reference | — | 1.490 | 671067 |
| run-20261012T234136Z-613ba497-844b-49bf-b443-7ff07db8efa9 | postgres | PostgreSQL native COPY reference native_reference | — | 1.855 | 539152 |
| run-20261012T213750Z-1051edb2-e886-4ba4-8ad2-f254596d00a3 | mariadb | scriptella unbatched | 1.6 | 210.032 | 4761 |
| run-20261012T234136Z-613ba497-844b-49bf-b443-7ff07db8efa9 | mariadb | scriptella unbatched | 1.6 | 230.131 | 4345 |
| run-20261012T213750Z-1051edb2-e886-4ba4-8ad2-f254596d00a3 | mariadb | scriptella batch_1000 | 1.6 | 8.644 | 115689 |
| run-20261012T234136Z-613ba497-844b-49bf-b443-7ff07db8efa9 | mariadb | scriptella batch_1000 | 1.6 | 10.403 | 96131 |
| run-20261012T213750Z-1051edb2-e886-4ba4-8ad2-f254596d00a3 | mariadb | jdbc unbatched | — | 197.659 | 5059 |
| run-20261012T234136Z-613ba497-844b-49bf-b443-7ff07db8efa9 | mariadb | jdbc unbatched | — | 214.192 | 4669 |
| run-20261012T213750Z-1051edb2-e886-4ba4-8ad2-f254596d00a3 | mariadb | jdbc batch_1000 | — | 6.818 | 146676 |
| run-20261012T234136Z-613ba497-844b-49bf-b443-7ff07db8efa9 | mariadb | jdbc batch_1000 | — | 6.480 | 154310 |
| run-20261012T213750Z-1051edb2-e886-4ba4-8ad2-f254596d00a3 | mariadb | csvsql chunk_1000 | — | 592.038 | 1689 |
| run-20261012T234136Z-613ba497-844b-49bf-b443-7ff07db8efa9 | mariadb | csvsql chunk_1000 | — | 602.068 | 1661 |

Directional engineering experiment; machine, caches, fixed order and fresh process startup affect results. csvsql chunking differs from JDBC batching. PostgreSQL native COPY is a separate reference. Repeated runs do not make results universal.
