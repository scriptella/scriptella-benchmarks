# Exploratory CSV import comparison

Environment: intel-colima-v5-final8 — Intel Mac x86_64; Colima default 4 CPUs 6 GiB; JVM defaults; PostgreSQL 17.11 MariaDB 11.8.8; final eight cases

Fixture: 1000000 rows; SHA-256 bd323d366297efd6895e11c0feac212dadc3ea37c9db8ccadc3e96409cb393cc

| Metadata | Value |
|---|---|
| benchmark_version | v5 |
| environment_id | intel-colima-v5-final8 |
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
| sqlalchemy_version | 2.0.46 |
| postgres_dbapi_driver | psycopg2 |
| postgres_dbapi_driver_version | 2.9.11 |
| mariadb_dbapi_driver | PyMySQL |
| mariadb_dbapi_driver_version | 1.1.2 |
| postgres_synchronous_commit | on |
| postgres_fsync | on |
| mariadb_innodb_flush_log_at_trx_commit | 1 |
| timing_boundary | fresh_process_start_to_exit |
| case_definition | v5-final-eight-pg-rewrite-true-warmup20000-pandas-read10000-write1000-local-infile-v1 |
| pandas_version | 2.3.3 |
| numpy_version | 2.3.4 |
| mariadb_local_infile | 1 |
| mariadb_sql_mode | IGNORE_SPACE,STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_AUTO_CREATE_USER,NO_ENGINE_SUBSTITUTION |
| mariadb_log_bin | 0 |
| mariadb_binlog_format | MIXED |
| mariadb_sync_binlog | 0 |
| mariadb_load_client_settings | MariaDB Connector/J 3.5.7; allowLocalInfile=true; bound InputStream; autocommit=false; time_zone=+00:00; warnings fatal |
| mariadb_load_sql | LOAD DATA LOCAL INFILE 'benchmark-stream.csv' INTO TABLE csv_import\nCHARACTER SET utf8mb4\nFIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"' ESCAPED BY ''\nLINES TERMINATED BY '\n'\nIGNORE 1 LINES\n(id,name,category,amount,created_at)\n |
| mariadb_load_transaction_probe | InnoDB: in_transaction=1; rows invisible before commit; rollback empty |

| Database | Case | Scriptella release | Median duration (s) | Median rows/s | Samples |
|---|---|---|---:|---:|---:|
| postgres | scriptella batch_1000 | 1.6 | 10.053 | 99493 | 2 |
| postgres | jdbc batch_1000 | — | 7.579 | 132344 | 2 |
| postgres | Pandas + SQLAlchemy chunk_1000 | — | 29.106 | 34505 | 2 |
| postgres | PostgreSQL native COPY reference native_reference | — | 2.366 | 429160 | 2 |
| mariadb | scriptella batch_1000 | 1.6 | 9.227 | 108397 | 2 |
| mariadb | jdbc batch_1000 | — | 5.622 | 178086 | 2 |
| mariadb | Pandas + SQLAlchemy chunk_1000 | — | 19.609 | 51012 | 2 |
| mariadb | MariaDB native LOAD DATA LOCAL INFILE reference native_reference | — | 3.039 | 330457 | 2 |

Median seconds; complete individual measurements follow below.

| Database | Scriptella | JDBC | Pandas | Native load |
|---|---:|---:|---:|---:|
| postgres (Scriptella 1.6) | 10.053 | 7.579 | 29.106 | 2.366 (COPY) |
| mariadb (Scriptella 1.6) | 9.227 | 5.622 | 19.609 | 3.039 (LOAD DATA LOCAL) |

Ratios of median durations; above 1 means the first named implementation/mode is faster. Missing cases are omitted.

- postgres Scriptella 1.6 vs JDBC (batch_1000): 0.754× (jdbc batch_1000 duration / scriptella batch_1000 duration)
- postgres Scriptella 1.6 vs pandas (batch_1000): 2.895× (pandas chunk_1000 duration / scriptella batch_1000 duration)
- postgres COPY vs Scriptella 1.6 (batch_1000): 4.250× (scriptella batch_1000 duration / copy native_reference duration)
- mariadb Scriptella 1.6 vs JDBC (batch_1000): 0.609× (jdbc batch_1000 duration / scriptella batch_1000 duration)
- mariadb Scriptella 1.6 vs pandas (batch_1000): 2.125× (pandas chunk_1000 duration / scriptella batch_1000 duration)
- mariadb LOAD DATA vs Scriptella 1.6 (batch_1000): 3.036× (scriptella batch_1000 duration / load_data native_reference duration)

| Run | Database | Case | Release | Duration (s) | Rows/s |
|---|---|---|---|---:|---:|
| run-20261027T005424Z-3a5bdee1-b718-4a4b-ba9d-203f857e2b87 | postgres | scriptella batch_1000 | 1.6 | 9.907 | 100937 |
| run-20261027T005620Z-02b005f9-87f7-494c-86e3-3ea9a3f12ccc | postgres | scriptella batch_1000 | 1.6 | 10.199 | 98049 |
| run-20261027T005424Z-3a5bdee1-b718-4a4b-ba9d-203f857e2b87 | postgres | jdbc batch_1000 | — | 7.992 | 125126 |
| run-20261027T005620Z-02b005f9-87f7-494c-86e3-3ea9a3f12ccc | postgres | jdbc batch_1000 | — | 7.165 | 139561 |
| run-20261027T005424Z-3a5bdee1-b718-4a4b-ba9d-203f857e2b87 | postgres | Pandas + SQLAlchemy chunk_1000 | — | 31.013 | 32244 |
| run-20261027T005620Z-02b005f9-87f7-494c-86e3-3ea9a3f12ccc | postgres | Pandas + SQLAlchemy chunk_1000 | — | 27.200 | 36765 |
| run-20261027T005424Z-3a5bdee1-b718-4a4b-ba9d-203f857e2b87 | postgres | PostgreSQL native COPY reference native_reference | — | 2.076 | 481753 |
| run-20261027T005620Z-02b005f9-87f7-494c-86e3-3ea9a3f12ccc | postgres | PostgreSQL native COPY reference native_reference | — | 2.656 | 376567 |
| run-20261027T005424Z-3a5bdee1-b718-4a4b-ba9d-203f857e2b87 | mariadb | scriptella batch_1000 | 1.6 | 9.113 | 109731 |
| run-20261027T005620Z-02b005f9-87f7-494c-86e3-3ea9a3f12ccc | mariadb | scriptella batch_1000 | 1.6 | 9.340 | 107062 |
| run-20261027T005424Z-3a5bdee1-b718-4a4b-ba9d-203f857e2b87 | mariadb | jdbc batch_1000 | — | 5.823 | 171722 |
| run-20261027T005620Z-02b005f9-87f7-494c-86e3-3ea9a3f12ccc | mariadb | jdbc batch_1000 | — | 5.422 | 184451 |
| run-20261027T005424Z-3a5bdee1-b718-4a4b-ba9d-203f857e2b87 | mariadb | Pandas + SQLAlchemy chunk_1000 | — | 19.942 | 50145 |
| run-20261027T005620Z-02b005f9-87f7-494c-86e3-3ea9a3f12ccc | mariadb | Pandas + SQLAlchemy chunk_1000 | — | 19.276 | 51878 |
| run-20261027T005424Z-3a5bdee1-b718-4a4b-ba9d-203f857e2b87 | mariadb | MariaDB native LOAD DATA LOCAL INFILE reference native_reference | — | 3.235 | 309136 |
| run-20261027T005620Z-02b005f9-87f7-494c-86e3-3ea9a3f12ccc | mariadb | MariaDB native LOAD DATA LOCAL INFILE reference native_reference | — | 2.843 | 351777 |

Directional engineering experiment; machine, caches, fixed order and fresh process startup affect results. Python chunking differs from JDBC batching. Native COPY and LOAD DATA are separate bulk-loading references with different parsing/error semantics. Repeated runs do not make results universal.
